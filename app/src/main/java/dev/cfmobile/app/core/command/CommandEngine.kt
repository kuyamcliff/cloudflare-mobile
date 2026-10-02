package dev.cfmobile.app.core.command

import dev.cfmobile.app.core.api.BodyField
import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.EndpointScope
import dev.cfmobile.app.core.capabilities.CapabilityRegistry
import dev.cfmobile.app.core.capabilities.CapabilityScope
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.ui.navigation.Routes

/** Something the user owns that can be named in a command: a Worker, a bucket, a database. */
data class ResourceRef(
    val kind: String,
    val id: String,
    val name: String,
    /** A native screen for it, when one exists. */
    val route: String? = null,
    /** Otherwise, the read that shows it. */
    val draft: ActionDraft? = null
)

data class CommandContext(
    val account: NamedRef? = null,
    val zone: NamedRef? = null,
    val zones: List<NamedRef> = emptyList(),
    val resources: List<ResourceRef> = emptyList()
)

/** A navigable screen. Exactly one of the route builders applies. */
data class Place(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: String,
    val scope: CapabilityScope?,
    val capabilityId: String?,
    val route: (account: String?, zone: NamedRef?) -> String?
)

sealed class CommandHit {
    abstract val key: String
    abstract val title: String
    abstract val subtitle: String?
    abstract val score: Double

    /** A prefilled operation. [restricted] when the token's policies rule it out. */
    data class Run(
        override val key: String,
        override val title: String,
        override val subtitle: String?,
        override val score: Double,
        val draft: ActionDraft,
        val endpoint: EndpointDef?,
        val recipe: Recipe?,
        val restricted: Boolean = false
    ) : CommandHit()

    data class Go(
        override val key: String,
        override val title: String,
        override val subtitle: String?,
        override val score: Double,
        val route: String,
        val place: Place?,
        /** Selecting this also switches the working zone. */
        val selectZone: NamedRef? = null,
        val restricted: Boolean = false
    ) : CommandHit()

    data class Resource(
        override val key: String,
        override val title: String,
        override val subtitle: String?,
        override val score: Double,
        val ref: ResourceRef,
        val zone: NamedRef? = null
    ) : CommandHit()
}

data class CommandResults(
    val query: String,
    val best: CommandHit?,
    val actions: List<CommandHit.Run>,
    val places: List<CommandHit.Go>,
    val resources: List<CommandHit.Resource>,
    val operations: List<CommandHit.Run>,
    val mentionedZone: NamedRef?,
    /** True when the request reads like a sentence that nothing local fully explains. */
    val suggestPlanner: Boolean
) {
    val isEmpty: Boolean get() = actions.isEmpty() && places.isEmpty() && resources.isEmpty() && operations.isEmpty()

    companion object {
        val EMPTY = CommandResults("", null, emptyList(), emptyList(), emptyList(), emptyList(), null, false)
    }
}

/**
 * Turns typed text into ranked things to do. Pure and synchronous, so it runs on every
 * keystroke off the main thread and is fully unit tested.
 *
 * - Recipes: common tasks with slot filling ("add A record www 192.0.2.1 on example.com").
 * - Places: native screens, in the zone the text names or the working zone.
 * - Resources: zones and named account resources.
 * - Operations: every operation in Cloudflare's schema, each prefilled the same way.
 *
 * [allowed] reports whether the token's policies permit an operation (null when unknown).
 */
class CommandEngine(
    private val registry: EndpointRegistry?,
    private val allowed: (EndpointDef, accountId: String?, zoneId: String?) -> Boolean? = { _, _, _ -> null },
    private val placeAllowed: (capabilityId: String, accountId: String?, zoneId: String?) -> Boolean? = { _, _, _ -> null }
) {
    private val byKey: Map<Pair<String, String>, EndpointDef> = registry?.endpoints?.associateBy { it.method to it.path }.orEmpty()

    private class OpIndex(val def: EndpointDef, val summary: Set<String>, val path: Set<String>, val group: Set<String>, val desc: Set<String>)

    private val opIndex: List<OpIndex> by lazy {
        registry?.endpoints.orEmpty().map { e ->
            OpIndex(
                e,
                words(e.summary),
                e.path.split('/', '_', '-', '.').filter { it.isNotBlank() && !it.startsWith("{") }.map { CommandText.canonical(it) }.toSet(),
                words(e.group),
                words(e.description)
            )
        }
    }

    fun endpointFor(method: String, path: String): EndpointDef? = byKey[method to path]

    fun search(input: String, ctx: CommandContext, limit: Int = 8): CommandResults {
        if (input.isBlank()) return CommandResults.EMPTY
        val parsed = CommandText.parse(input)
        val zone = mentionedZone(parsed, ctx.zones)
        val workingZone = zone ?: ctx.zone
        val accountId = workingZone?.parentId ?: ctx.account?.id
        val content = contentWords(parsed, zone)

        val actions = recipeHits(parsed, ctx, zone, accountId).sortedByDescending { it.score }.take(limit)
            .let { hits -> if (hits.any { it.score >= CONFIDENT }) hits.filter { it.score >= CONFIDENT - 25 } else hits }
        val places = placeHits(parsed, content, workingZone, ctx.account?.id, zone).sortedByDescending { it.score }.take(limit)
        val resources = resourceHits(parsed, content, ctx, zone).sortedByDescending { it.score }.take(limit)
        val recipeKeys = actions.map { it.draft.method to it.draft.path }.toSet()
        val rawOps = operationHits(parsed, content, ctx, zone, accountId)
            .filter { (it.draft.method to it.draft.path) !in recipeKeys }
            .sortedByDescending { it.score }
        val topOp = rawOps.firstOrNull()?.score ?: 0.0
        val confidentElsewhere = (actions + places).any { it.score >= CONFIDENT }
        val operations = rawOps.filter { it.score >= topOp * 0.75 }.take(if (confidentElsewhere) 4 else limit * 2)

        val all: List<CommandHit> = actions + places + resources + operations
        val best = all.filter { it !is CommandHit.Run || !it.restricted }.maxByOrNull { it.score }
        val confident = best != null && best.score >= CONFIDENT
        return CommandResults(
            query = input,
            best = best,
            actions = actions,
            places = places,
            resources = resources,
            operations = operations,
            mentionedZone = zone,
            suggestPlanner = content.size >= 2 && !confident
        )
    }

    // ---- Recipes ---------------------------------------------------------------------------

    private fun recipeHits(parsed: CommandText.Parsed, ctx: CommandContext, zone: NamedRef?, accountId: String?): List<CommandHit.Run> {
        val words = parsed.canonSet - CommandText.STOP + virtualWords(parsed)
        return Recipes.all.mapNotNull { r ->
            if (r.excludes.any { it in words }) return@mapNotNull null
            val verbs = MUTATING_VERBS.filter { it in words }
            val base = r.phrases.maxOf { phrase ->
                val p = CommandText.phraseWords(phrase)
                val matched = p.count { it in words }
                val score = when {
                    p.isEmpty() -> 0.0
                    matched == p.size -> 100.0 + 10 * p.size
                    p.size >= 3 && matched >= p.size - 1 -> 40.0 + 10 * matched
                    else -> 0.0
                }
                // "create worker" is not a request to list Workers.
                if (score > 0 && r.isRead && verbs.any { it !in p }) score - 60 else score
            }
            if (base <= 0) return@mapNotNull null
            if (r.needsZone && zone == null && ctx.zone == null) return@mapNotNull null
            val endpoint = byKey[r.method to r.path]
            val (draft, explained, unexplained) = draftFor(r, endpoint, parsed, ctx, zone)
            val zoneId = draft.pathValues["zone_id"]
            val restricted = endpoint?.let { allowed(it, accountId, zoneId) } == false
            val readPenalty = if (r.isRead) 10.0 else 0.0
            val requiredBody = endpoint?.bodyFields?.filter { it.required }?.map { it.name }.orEmpty().toSet()
            val incomplete = r.slots.any { slot ->
                slot.target == Target.BODY && isEmptyValue(Json.get(draft.body, slot.name)) &&
                    ((slot.kind is SlotKind.Choice && slot.kind.default == null) || slot.kind == SlotKind.Number || slot.name in requiredBody)
            }
            val score = base + 30 * explained - 12 * maxOf(0, unexplained - 1) - readPenalty -
                (if (incomplete) 45 else 0) - (if (restricted) 60 else 0)
            CommandHit.Run(
                key = "recipe:${r.id}", title = r.title, subtitle = describe(draft, r.hint, ctx), score = score,
                draft = draft, endpoint = endpoint, recipe = r, restricted = restricted
            )
        }
    }

    data class RecipeFill(val draft: ActionDraft, val explained: Double, val unexplained: Int)

    /** Builds a recipe's draft and reports how much of the text it accounted for. */
    fun draftFor(recipe: Recipe, endpoint: EndpointDef?, parsed: CommandText.Parsed, ctx: CommandContext, zone: NamedRef?): RecipeFill {
        val filler = Filler(parsed, ctx, zone, endpoint)
        recipe.phrases.flatMap { CommandText.phraseWords(it) }.forEach { filler.consumeWord(it) }
        filler.path.putAll(recipe.fixedPath)
        filler.query.putAll(recipe.fixedQuery)
        var body: Map<String, Any?> = substitute(recipe.fixedBody, filler) as Map<String, Any?>
        filler.fillPath(recipe.path)
        filler.applyAssignments(endpoint)?.let { body = merge(body, it) }
        for (slot in recipe.slots) {
            val current = when (slot.target) {
                Target.PATH -> filler.path[slot.name]
                Target.QUERY -> filler.query[slot.name]
                Target.BODY -> Json.get(body, slot.name)?.takeUnless { it == "" || (it is List<*> && it.all { x -> x == "" }) }
            }
            if (current != null && !(slot.target == Target.BODY && isFixedDefault(recipe, slot))) continue
            var value = filler.take(slot.kind, zoneForName = if (slot.inZone) (zone ?: ctx.zone) else null) ?: continue
            if (slot.inZone) value = qualify(value.toString(), zone ?: ctx.zone)
            if (slot.asList) value = listOf(value)
            when (slot.target) {
                Target.PATH -> filler.path[slot.name] = value.toString()
                Target.QUERY -> filler.query[slot.name] = value.toString()
                Target.BODY -> body = Json.set(body, slot.name, value)
            }
            if (recipe.id == "firewall.access_rule" && slot.name == "configuration.value") {
                val target = when {
                    value is String && CommandText.isCidr(value) -> "ip_range"
                    value is String && CommandText.isIp(value) -> if (value.contains(':')) "ip6" else "ip"
                    else -> "country"
                }
                body = Json.set(body, "configuration.target", target)
            }
        }
        val hasBody = recipe.method != "GET" && recipe.method != "DELETE" || body.isNotEmpty()
        val draft = ActionDraft(
            method = recipe.method,
            path = recipe.path,
            title = recipe.title,
            pathValues = filler.path.toMap(),
            query = filler.query.toMap(),
            body = if (hasBody) body else null,
            autoRun = recipe.isRead,
            source = ActionDraft.Source.RECIPE
        )
        return RecipeFill(draft, filler.explainedShare(), filler.unexplained())
    }

    private fun isEmptyValue(v: Any?): Boolean = v == null || v == "" || (v is List<*> && v.all { isEmptyValue(it) })

    /** A slot whose fixed value is a placeholder default (an access rule's mode) is still
     *  overwritten by what the user typed. */
    private fun isFixedDefault(recipe: Recipe, slot: Slot): Boolean =
        slot.kind is SlotKind.Choice && Json.get(recipe.fixedBody, slot.name) != null

    // ---- Places ----------------------------------------------------------------------------

    val places: List<Place> by lazy { buildPlaces() }

    private fun placeHits(parsed: CommandText.Parsed, content: List<String>, zone: NamedRef?, accountId: String?, mentioned: NamedRef?): List<CommandHit.Go> {
        val nav = parsed.tokens.any { it.canon in CommandText.NAV || it.canon == "list" }
        val terms = content.filter { it != "list" }
        val out = ArrayList<CommandHit.Go>()
        if (mentioned != null && terms.isEmpty()) {
            out += CommandHit.Go("zone:${mentioned.id}", mentioned.name, "Open zone", 130.0, Routes.zoneMenu(mentioned.id, mentioned.name), null, selectZone = mentioned)
        }
        if (terms.isEmpty()) return out
        for (p in places) {
            val name = words(p.title)
            val more = words(p.keywords)
            val desc = words(p.subtitle)
            val nameHits = terms.count { t -> name.any { matches(t, it) } }
            val moreHits = terms.count { t -> (name + more).any { matches(t, it) } }
            val descHits = terms.count { t -> desc.any { matches(t, it) } }
            val score = when {
                nameHits == terms.size -> 96.0 + 4 * nameHits + (if (name.size == nameHits) 6 else 0)
                moreHits == terms.size -> 86.0 + 3 * moreHits
                else -> 30.0 * moreHits / terms.size + 12.0 * descHits / terms.size
            } + (if (nav) 15 else 0)
            if (score < 25) continue
            val route = p.route(accountId, zone) ?: continue
            val restricted = p.capabilityId?.let { placeAllowed(it, accountId, zone?.id) } == false
            out += CommandHit.Go(
                key = "place:${p.id}", title = p.title,
                subtitle = when (p.scope) {
                    CapabilityScope.ZONE -> zone?.name
                    else -> p.subtitle
                },
                score = score - (if (restricted) 50 else 0), route = route, place = p,
                selectZone = if (p.scope == CapabilityScope.ZONE) mentioned else null,
                restricted = restricted
            )
        }
        return out
    }

    private fun buildPlaces(): List<Place> {
        val caps = CapabilityRegistry.implemented().map { cap ->
            Place(
                id = cap.id, title = cap.displayName, subtitle = cap.description, keywords = cap.product + " " + cap.id.replace('.', ' ').replace('_', ' '),
                scope = cap.scope, capabilityId = cap.id,
                route = { account, zone ->
                    when {
                        cap.zoneRoute != null -> zone?.let { cap.zoneRoute.invoke(it.id, it.name) }
                        cap.accountRoute != null -> account?.let { cap.accountRoute.invoke(it) }
                        else -> null
                    }
                }
            )
        }
        val fixed = listOf(
            Place("zones", "Zones", "Every zone this token can see", "domains sites websites", null, null) { _, _ -> Routes.ZONES },
            Place("api.catalog", "All Cloudflare APIs", "Browse every operation by product", "api catalog operations endpoints explorer", null, null) { _, _ -> Routes.CATALOG },
            Place("explorer", "API Explorer", "Send any request by hand", "api request curl raw http", null, null) { _, _ -> Routes.explorer() },
            Place("graphql", "Analytics query", "GraphQL Analytics API console", "graphql analytics query console", null, null) { _, _ -> Routes.GRAPHQL },
            Place("token.access", "What this token can do", "Permissions, scopes and what is hidden", "token permissions access scope", null, null) { _, _ -> Routes.TOKEN_ACCESS },
            Place("token.create", "Create API token", "Choose permissions, resources and conditions", "api token create new key", null, null) { a, _ -> Routes.tokenCreate(null).takeIf { a != null || true } },
            Place("history", "Request history", "Every request sent from this device", "history activity requests log recent", null, null) { _, _ -> Routes.ACTIVITY },
            Place("transfers", "Transfers", "R2 uploads and downloads", "transfers uploads downloads progress", null, null) { _, _ -> Routes.TRANSFERS },
            Place("settings", "Settings", "Profiles, theme, data on this device", "settings preferences profiles theme sign out", null, null) { _, _ -> Routes.SETTINGS },
            Place("security", "App lock", "Biometric lock and screenshot protection", "security lock biometric fingerprint screenshot", null, null) { _, _ -> Routes.SECURITY },
            Place("diagnostics", "Diagnostics", "Connectivity, API health and storage", "diagnostics health debug network", null, null) { _, _ -> Routes.DIAGNOSTICS }
        )
        return caps + fixed
    }

    // ---- Resources -------------------------------------------------------------------------

    private fun resourceHits(parsed: CommandText.Parsed, content: List<String>, ctx: CommandContext, mentioned: NamedRef?): List<CommandHit.Resource> {
        val raw = parsed.tokens.map { it.raw.lowercase() }.filter { it.length >= 2 && it !in CommandText.STOP }
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<CommandHit.Resource>()
        for (z in ctx.zones) {
            if (z.id == mentioned?.id) continue
            val s = nameScore(z.name.lowercase(), raw)
            if (s > 0) out += CommandHit.Resource("res:zone:${z.id}", z.name, "Zone", s, ResourceRef("zone", z.id, z.name, Routes.zoneMenu(z.id, z.name)), zone = z)
        }
        for (r in ctx.resources) {
            val s = nameScore(r.name.lowercase(), raw) + (if (content.any { it == CommandText.canonical(r.kind) }) 10 else 0)
            if (s > 10) out += CommandHit.Resource("res:${r.kind}:${r.id}", r.name, r.kind, s, r)
        }
        return out
    }

    private fun nameScore(name: String, tokens: List<String>): Double = tokens.maxOf { t ->
        when {
            t == name -> 110.0
            name.startsWith(t) && t.length >= 3 -> 70.0 + 20.0 * t.length / name.length
            name.contains(t) && t.length >= 3 -> 50.0 + 20.0 * t.length / name.length
            else -> 0.0
        }
    }

    // ---- Operations ------------------------------------------------------------------------

    private fun operationHits(parsed: CommandText.Parsed, content: List<String>, ctx: CommandContext, zone: NamedRef?, accountId: String?): List<CommandHit.Run> {
        val verbs = setOf("create", "delete", "update", "list", "purge")
        val terms = content.filter { it !in verbs }
        if (terms.isEmpty() || registry == null) return emptyList()
        val methods: Set<String>? = when {
            parsed.has("create") -> setOf("POST", "PUT")
            parsed.has("delete") -> setOf("DELETE")
            parsed.has("update") || parsed.has("on") || parsed.has("off") -> setOf("PATCH", "PUT", "POST")
            parsed.has("list") -> setOf("GET")
            else -> null
        }
        val scored = ArrayList<Pair<OpIndex, Double>>()
        for (op in opIndex) {
            var weight = 0.0
            var covered = 0
            for (t in terms) {
                val w = when {
                    op.summary.any { matches(t, it) } -> 3.0
                    op.path.any { matches(t, it) } -> 2.0
                    op.group.any { matches(t, it) } -> 1.5
                    op.desc.contains(t) -> 0.5
                    else -> 0.0
                }
                if (w > 0) covered++
                weight += w
            }
            if (covered * 2 < terms.size || weight < 2.0) continue
            var score = 70.0 * weight / (3.0 * terms.size)
            if (methods != null) score += if (op.def.method in methods) 12 else -15
            else if (op.def.method == "GET") score += 4
            if (op.def.deprecated) score -= 15
            if (zone != null && op.def.scope == EndpointScope.ZONE) score += 6
            if (op.def.native) score += 2
            // Collections before single items: "list workers" wants the list.
            if (op.def.method == "GET" && op.def.path.endsWith("}")) score -= 3
            scored += op to score
        }
        return scored.sortedByDescending { it.second }.take(24).map { (op, score) ->
            val draft = draftForEndpoint(op.def, parsed, ctx, zone)
            val restricted = allowed(op.def, accountId, draft.pathValues["zone_id"]) == false
            CommandHit.Run(
                key = "op:${op.def.id}", title = op.def.summary, subtitle = "${op.def.method} ${op.def.group}",
                score = score - (if (restricted) 40 else 0), draft = draft, endpoint = op.def, recipe = null, restricted = restricted
            )
        }
    }

    /** Prefills any operation from the text: zone and account, typed `name=value` pairs,
     *  enum words, and obvious values (an IP into `content`, an email into `email`). */
    fun draftForEndpoint(endpoint: EndpointDef, parsed: CommandText.Parsed?, ctx: CommandContext, zone: NamedRef? = null, source: ActionDraft.Source = ActionDraft.Source.SEARCH): ActionDraft {
        val filler = Filler(parsed ?: CommandText.parse(""), ctx, zone, endpoint)
        filler.fillPath(endpoint.path)
        var body: Map<String, Any?> = emptyMap()
        if (parsed != null) {
            filler.applyAssignments(endpoint)?.let { body = merge(body, it) }
            for (f in endpoint.bodyFields) {
                if (body.containsKey(f.name)) continue
                val v = filler.guessField(f) ?: continue
                body = body + (f.name to v)
            }
            for (q in endpoint.queryParams) {
                if (filler.query.containsKey(q.name) || q.enumValues.isEmpty()) continue
                filler.takeEnum(q.enumValues)?.let { filler.query[q.name] = it }
            }
        }
        val required = endpoint.pathParams.all { !filler.path[it.name].isNullOrBlank() } &&
            endpoint.queryParams.filter { it.required }.all { !filler.query[it.name].isNullOrBlank() }
        return ActionDraft(
            method = endpoint.method,
            path = endpoint.path,
            title = endpoint.summary,
            pathValues = filler.path.toMap(),
            query = filler.query.toMap(),
            body = if (endpoint.bodyContentType != null || body.isNotEmpty()) body else null,
            autoRun = endpoint.method == "GET" && required && endpoint.pathParams.size <= 2,
            source = source
        )
    }

    // ---- Shared helpers --------------------------------------------------------------------

    private fun mentionedZone(parsed: CommandText.Parsed, zones: List<NamedRef>): NamedRef? {
        if (zones.isEmpty()) return null
        for (t in parsed.tokens) {
            val w = t.raw.lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
            zones.firstOrNull { it.name.equals(w, true) }?.let { return it }
        }
        for (t in parsed.tokens) {
            val w = t.raw.lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
            zones.filter { w.endsWith("." + it.name.lowercase()) }.maxByOrNull { it.name.length }?.let { return it }
        }
        // A bare first label ("example" for example.com), only when unambiguous.
        for (t in parsed.tokens) {
            val w = t.raw.lowercase()
            if (w.length < 4 || '.' in w) continue
            val hits = zones.filter { it.name.lowercase().substringBefore('.') == w }
            if (hits.size == 1) return hits.first()
        }
        return null
    }

    private fun contentWords(parsed: CommandText.Parsed, zone: NamedRef?): List<String> =
        parsed.tokens.filter { t ->
            val raw = t.raw.lowercase()
            t.canon !in CommandText.STOP && t.canon !in CommandText.NAV &&
                !(zone != null && (raw == zone.name.lowercase() || raw == zone.name.lowercase().substringBefore('.') || raw.endsWith("." + zone.name.lowercase()))) &&
                !CommandText.isIp(t.raw) && !CommandText.isCidr(t.raw) && !CommandText.EMAIL.matches(t.raw) &&
                !CommandText.URL.matches(t.raw) && !CommandText.NUMBER.matches(t.raw)
        }.map { it.canon }.distinct()

    private fun virtualWords(parsed: CommandText.Parsed): Set<String> = buildSet {
        parsed.tokens.forEach { t ->
            when {
                CommandText.isIp(t.raw) || CommandText.isCidr(t.raw) -> add("ip")
                CommandText.EMAIL.matches(t.raw) -> add("email")
                CommandText.URL.matches(t.raw) -> add("url")
            }
            if (CommandText.COUNTRIES.containsKey(t.raw.lowercase())) add("country")
        }
    }

    private fun describe(draft: ActionDraft, hint: String?, ctx: CommandContext): String? {
        val parts = ArrayList<String>()
        draft.body?.let { b -> summarize(b).takeIf { it.isNotBlank() }?.let(parts::add) }
        if (parts.isEmpty() && hint != null) parts += hint
        val zoneId = draft.pathValues["zone_id"]
        val zoneName = ctx.zones.firstOrNull { it.id == zoneId }?.name ?: ctx.zone?.takeIf { it.id == zoneId }?.name
        zoneName?.let { parts += it }
        return parts.joinToString(" · ").ifBlank { null }
    }

    private fun summarize(body: Map<String, Any?>): String = body.entries
        .filter { (k, v) -> v != null && v != "" && k !in setOf("ttl", "account", "enabled") }
        .take(4)
        .joinToString("  ") { (k, v) ->
            val value = when (v) {
                is Map<*, *> -> (v["value"] ?: v.values.firstOrNull { it != null && it != "" })?.toString() ?: "…"
                is List<*> -> v.filterNotNull().joinToString(", ") { if (it is Map<*, *>) "…" else it.toString() }
                else -> v.toString()
            }
            "$k: $value"
        }.take(90)

    private fun substitute(value: Any?, filler: Filler): Any? = when (value) {
        "{account_id}" -> filler.path["account_id"] ?: filler.accountId()
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to substitute(v, filler) }
        is List<*> -> value.map { substitute(it, filler) }
        else -> value
    }

    private fun merge(a: Map<String, Any?>, b: Map<String, Any?>): Map<String, Any?> {
        var out = a
        b.forEach { (k, v) -> out = Json.set(out, k, v) }
        return out
    }

    private fun qualify(name: String, zone: NamedRef?): String {
        if (zone == null) return name
        val z = zone.name.lowercase()
        return when {
            name == "@" || name.equals(z, true) -> zone.name
            name.lowercase().endsWith(".$z") -> name
            else -> "$name.${zone.name}"
        }
    }

    /** Collects values from the parsed text for one draft, remembering which words it used. */
    private inner class Filler(val parsed: CommandText.Parsed, val ctx: CommandContext, val zone: NamedRef?, val endpoint: EndpointDef?) {
        val path = LinkedHashMap<String, String>()
        val query = LinkedHashMap<String, String>()
        private val used = HashSet<Int>()
        /** Words the recipe's own phrase accounts for. Choice slots may still read them
         *  ("challenge" is both the phrase and the mode); free-text slots may not. */
        private val phraseUsed = HashSet<Int>()
        private var quotedUsed = 0
        private var restUsed = false

        fun accountId(): String? = (zone ?: ctx.zone)?.parentId ?: ctx.account?.id

        fun consumeWord(canon: String) {
            parsed.tokens.filter { it.canon == canon }.forEach { phraseUsed += it.index }
        }

        fun fillPath(template: String) {
            val names = Regex("\\{([^}]+)}").findAll(template).map { it.groupValues[1] }.toList()
            val z = zone ?: ctx.zone
            for (n in names) {
                if (!path[n].isNullOrBlank()) continue
                val lower = n.lowercase()
                val v = when {
                    lower == "zone_id" || lower == "zone_identifier" || (lower == "identifier" && template.startsWith("zones/{")) -> z?.id
                    lower == "account_id" || lower == "account_identifier" || (lower == "identifier" && template.startsWith("accounts/{")) -> accountId()
                    else -> null
                }
                if (v != null) path[n] = v
            }
            // The zone itself was named, so its words are accounted for.
            if (zone != null) parsed.tokens.filter { t ->
                val raw = t.raw.lowercase()
                raw == zone.name.lowercase() || raw == zone.name.lowercase().substringBefore('.')
            }.forEach { used += it.index }
        }

        /** Applies `name=value` pairs; returns the body part. */
        fun applyAssignments(endpoint: EndpointDef?): Map<String, Any?>? {
            if (parsed.assignments.isEmpty()) return null
            var body: Map<String, Any?> = emptyMap()
            for ((k, v) in parsed.assignments) {
                when {
                    endpoint?.pathParams?.any { it.name == k } == true || (endpoint == null && k.endsWith("_id")) -> path[k] = v
                    endpoint?.queryParams?.any { it.name == k } == true -> query[k] = v
                    else -> body = Json.set(body, k, coerce(v, endpoint?.bodyFields?.firstOrNull { it.name == k.substringBefore('.') }?.takeIf { '.' !in k }))
                }
            }
            return body
        }

        fun take(kind: SlotKind, zoneForName: NamedRef? = null): Any? = when (kind) {
            SlotKind.Ip -> takeToken { CommandText.isIp(it.raw) || CommandText.isCidr(it.raw) }?.raw
            SlotKind.Email -> takeToken { CommandText.EMAIL.matches(it.raw) }?.raw
            SlotKind.Url -> takeToken { CommandText.URL.matches(it.raw) }?.raw
            SlotKind.Host -> takeToken { t ->
                CommandText.HOST.matches(t.raw) && !CommandText.isIp(t.raw) &&
                    (zone == null || !t.raw.equals(zone.name, true) || zoneForName != null)
            }?.raw?.lowercase()
            SlotKind.Number -> takeToken { CommandText.NUMBER.matches(it.raw) }?.raw?.let { it.toLongOrNull() ?: it.toDouble() }
            SlotKind.Country -> takeCountry()
            SlotKind.Quoted -> parsed.quoted.getOrNull(quotedUsed)?.also { quotedUsed++ }
            SlotKind.Free -> takeToken { t ->
                t.canon !in CommandText.STOP && t.canon !in CommandText.NAV && !isVerb(t.canon) &&
                    (zone == null || zoneForName != null || !t.raw.equals(zone.name, true))
            }?.raw
            SlotKind.Rest -> takeRest()
            // "off" wins: "on" is also a preposition ("turn off dev mode on example.com").
            is SlotKind.OnOff -> when {
                takeAnyToken { it.canon == "off" } != null -> kind.off.also { takeAnyToken { it.canon == "on" } }
                takeAnyToken { it.canon == "on" } != null -> kind.on
                else -> kind.default
            }
            is SlotKind.Choice -> takeEnum(kind.values, kind.aliases) ?: kind.default
            is SlotKind.After -> {
                val i = parsed.tokens.indexOfFirst { it.canon == kind.keyword }
                val next = parsed.tokens.getOrNull(i + 1)
                if (i >= 0 && next != null && CommandText.NUMBER.matches(next.raw) && next.index !in used) {
                    used += parsed.tokens[i].index; used += next.index
                    next.raw.toLongOrNull() ?: next.raw.toDouble()
                } else null
            }
            is SlotKind.Flag -> parsed.tokens.firstOrNull { it.raw.lowercase() in kind.words || it.canon in kind.words }?.let { used += it.index; kind.value }
        }

        fun takeEnum(values: List<String>, aliases: Map<String, String> = emptyMap()): String? {
            for (t in parsed.tokens) {
                if (t.index in used) continue
                val alias = aliases[t.raw.lowercase()] ?: aliases[t.canon]
                val hit = alias ?: values.firstOrNull { v ->
                    if (v.length == 1) t.raw == v else t.raw.equals(v, true) || t.canon == v.lowercase()
                }
                if (hit != null) {
                    used += t.index
                    return hit
                }
            }
            return null
        }

        /** Best-effort value for an arbitrary body field. */
        fun guessField(f: BodyField): Any? {
            val n = f.name.lowercase()
            if (f.enumValues.isNotEmpty() && f.enumValues.size <= 40 && f.type != "array") {
                takeEnum(f.enumValues)?.let { return coerce(it, f) }
            }
            return when {
                f.type == "boolean" && (n == "enabled" || n == "paused" || n == "proxied") ->
                    if (parsed.has("on")) true.also { consumeWord("on") } else if (parsed.has("off")) false.also { consumeWord("off") } else null
                n in setOf("content", "ip", "ip_address", "address", "origin") -> take(SlotKind.Ip)
                n == "email" -> take(SlotKind.Email)
                n == "url" -> take(SlotKind.Url)
                n in setOf("name", "title", "description", "comment", "notes", "label") -> take(SlotKind.Quoted)
                else -> null
            }
        }

        fun coerce(value: String, f: BodyField?): Any? {
            val t = f?.type
            return when {
                t == "boolean" -> when (value.lowercase()) { "true", "on", "yes", "1" -> true; "false", "off", "no", "0" -> false; else -> value }
                t == "integer" -> value.toLongOrNull() ?: value
                t == "number" -> value.toLongOrNull() ?: value.toDoubleOrNull() ?: value
                t == "array" && !value.trimStart().startsWith("[") -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                value.trimStart().startsWith("{") || value.trimStart().startsWith("[") -> Json.parseOrNull(value) ?: value
                t == null && (value == "true" || value == "false") -> value.toBoolean()
                t == null && CommandText.NUMBER.matches(value) && !value.startsWith("0") -> value.toLongOrNull() ?: value.toDouble()
                else -> value
            }
        }

        private fun takeToken(pred: (CommandText.Token) -> Boolean): CommandText.Token? =
            parsed.tokens.firstOrNull { it.index !in used && it.index !in phraseUsed && pred(it) }?.also { used += it.index }

        private fun takeAnyToken(pred: (CommandText.Token) -> Boolean): CommandText.Token? =
            parsed.tokens.firstOrNull { it.index !in used && pred(it) }?.also { used += it.index }

        private fun takeCountry(): String? {
            val lower = parsed.tokens.map { it.raw.lowercase() }
            for (i in parsed.tokens.indices) {
                if (parsed.tokens[i].index in used) continue
                val two = if (i + 1 < lower.size) lower[i] + " " + lower[i + 1] else null
                if (two != null && CommandText.COUNTRIES.containsKey(two)) {
                    used += parsed.tokens[i].index; used += parsed.tokens[i + 1].index
                    return CommandText.COUNTRIES[two]
                }
                CommandText.COUNTRIES[lower[i]]?.let { code -> used += parsed.tokens[i].index; return code }
                val raw = parsed.tokens[i].raw
                if (raw.length == 2 && raw.all { it.isUpperCase() }) {
                    used += parsed.tokens[i].index
                    return raw
                }
            }
            return null
        }

        private fun takeRest(): String? {
            if (restUsed) return null
            restUsed = true
            val firstUnused = parsed.tokens.firstOrNull { it.index !in used && it.index !in phraseUsed && it.canon !in CommandText.STOP } ?: return parsed.quoted.firstOrNull()
            val rest = parsed.tokens.filter { it.index >= firstUnused.index }
            rest.forEach { used += it.index }
            val text = rest.joinToString(" ") { it.raw }
            return (listOf(text) + parsed.quoted).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
        }

        private fun isVerb(canon: String) = canon in setOf("create", "delete", "update", "list", "purge", "on", "off")

        fun explainedShare(): Double {
            val meaningful = meaningful()
            if (meaningful.isEmpty()) return 1.0
            return meaningful.count { it.index in used || it.index in phraseUsed }.toDouble() / meaningful.size
        }

        fun unexplained(): Int = meaningful().count { it.index !in used && it.index !in phraseUsed }

        private fun meaningful() = parsed.tokens.filter { it.canon !in CommandText.STOP && it.canon !in CommandText.NAV }
    }

    companion object {
        /** A best hit at or above this is safe to act on when the user presses enter. */
        const val CONFIDENT = 110.0
        private val MUTATING_VERBS = setOf("create", "delete", "update", "purge")

        private fun words(text: String): Set<String> =
            text.split(Regex("[^A-Za-z0-9]+")).filter { it.length > 1 }.map { CommandText.canonical(it) }.filter { it !in CommandText.STOP }.toSet()

        private fun matches(term: String, word: String): Boolean =
            term == word || (term.length >= 4 && word.startsWith(term)) || (word.length >= 4 && term.length > word.length && term.startsWith(word))
    }
}
