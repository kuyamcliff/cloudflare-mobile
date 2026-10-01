package dev.cfmobile.app.core.command

import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.api.RawRequest
import dev.cfmobile.app.core.api.RawResponse
import java.io.IOException

/**
 * Plans a free-form request with Workers AI on the user's own Cloudflare account. The request
 * text and the working zone and account go to Cloudflare, like every other call this app
 * makes; nothing goes anywhere else.
 *
 * The planner only ever returns a draft. The user reviews it in the normal form and nothing
 * is sent until they confirm, so a wrong plan costs a glance, not a change.
 *
 * Two steps keep the prompt small enough for a fast model: pick product groups from the
 * whole schema, then pick one operation among those groups plus the best local matches.
 */
class AiPlanner(
    private val client: RawApiClient,
    private val registryProvider: suspend () -> EndpointRegistry,
    private val model: String = DEFAULT_MODEL
) {
    sealed class Outcome {
        data class Planned(val draft: ActionDraft, val endpoint: EndpointDef) : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    suspend fun plan(request: String, ctx: CommandContext, localHits: List<CommandHit.Run>): Outcome {
        val accountId = ctx.zone?.parentId ?: ctx.account?.id
            ?: return Outcome.Failed("Choose an account first. Workers AI runs in your Cloudflare account.")
        val registry = registryProvider()
        return try {
            val groups = pickGroups(accountId, request, registry)
            val candidates = candidates(registry, groups, localHits, request)
            if (candidates.isEmpty()) return Outcome.Failed("No Cloudflare operation matched this request.")
            pickOperation(accountId, request, ctx, candidates)
        } catch (e: IOException) {
            Outcome.Failed("Couldn't reach Workers AI: ${e.message ?: "network error"}")
        } catch (e: PlannerError) {
            Outcome.Failed(e.message ?: "Workers AI could not plan this request.")
        }
    }

    private class PlannerError(message: String) : Exception(message)

    private suspend fun pickGroups(accountId: String, request: String, registry: EndpointRegistry): List<String> {
        val system = """
            You route requests about Cloudflare to product areas of the Cloudflare API.
            Reply with JSON only: {"groups": [up to 4 names copied exactly from the list]}.
        """.trimIndent()
        val user = "Request: $request\n\nProduct areas:\n" + registry.groups.joinToString("\n")
        val json = runModel(accountId, system, user, maxTokens = 200)
        val picked = (json["groups"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        return picked.filter { it in registry.groups }.take(4)
    }

    private fun candidates(registry: EndpointRegistry, groups: List<String>, local: List<CommandHit.Run>, request: String): List<EndpointDef> {
        val words = CommandText.parse(request)
        val method = when {
            words.has("create") -> setOf("POST", "PUT")
            words.has("delete") -> setOf("DELETE")
            words.has("list") -> setOf("GET")
            else -> null
        }
        val fromGroups = registry.endpoints.filter { it.group in groups && !it.deprecated }
            .sortedBy { if (method == null || it.method in method) 0 else 1 }
        val fromLocal = local.mapNotNull { it.endpoint }
        return (fromLocal.take(12) + fromGroups).distinctBy { it.id }.take(MAX_CANDIDATES)
    }

    private suspend fun pickOperation(accountId: String, request: String, ctx: CommandContext, ops: List<EndpointDef>): Outcome {
        val system = """
            You turn a request about Cloudflare into exactly one API operation from a numbered list.
            Reply with JSON only:
            {"operation": <number>, "path": {"<placeholder>": "<value>"}, "query": {"<name>": "<value>"}, "body": {<JSON body>}, "note": "<one short sentence saying what this does>"}
            Rules: use only values stated in the request or the context. Never invent IDs. Leave out
            placeholders you cannot fill; the user will pick them. Use Cloudflare's field names and
            enum values exactly as listed. Prefer the most specific operation that does what was asked.
        """.trimIndent()
        val context = buildString {
            append("account_id=").append(accountId)
            ctx.zone?.let { append("\nworking zone: ").append(it.name).append(" (zone_id=").append(it.id).append(')') }
            if (ctx.zones.isNotEmpty()) {
                append("\nother zones: ")
                append(ctx.zones.take(30).joinToString(", ") { "${it.name}=${it.id}" })
            }
        }
        val list = ops.mapIndexed { i, e -> "${i + 1}. ${describe(e)}" }.joinToString("\n")
        val json = runModel(accountId, system, "Context:\n$context\n\nRequest: $request\n\nOperations:\n$list", maxTokens = 700)
        val index = (json["operation"] as? Number)?.toInt()?.minus(1)
            ?: throw PlannerError("Workers AI did not choose an operation.")
        val endpoint = ops.getOrNull(index) ?: throw PlannerError("Workers AI chose an operation that isn't in the list.")

        val placeholders = endpoint.placeholderNames().toSet()
        val path = (json["path"] as? Map<*, *>).orEmpty().entries
            .filter { (k, v) -> k.toString() in placeholders && v != null && v.toString().isNotBlank() }
            .associate { (k, v) -> k.toString() to v.toString() }
            .filterValues { !it.startsWith("{") && !it.startsWith("<") }
        val defaults = CommandEngine(null).draftForEndpoint(endpoint, null, ctx, ctx.zone)
        val knownQuery = endpoint.queryParams.map { it.name }.toSet()
        val query = (json["query"] as? Map<*, *>).orEmpty().entries
            .filter { (k, v) -> k.toString() in knownQuery && v != null }
            .associate { (k, v) -> k.toString() to v.toString() }
        @Suppress("UNCHECKED_CAST")
        val body = (json["body"] as? Map<String, Any?>)?.takeIf { endpoint.method != "GET" }
        val draft = ActionDraft(
            method = endpoint.method,
            path = endpoint.path,
            title = endpoint.summary,
            pathValues = defaults.pathValues + path,
            query = query,
            body = body ?: if (endpoint.bodyContentType != null) emptyMap() else null,
            autoRun = false,
            source = ActionDraft.Source.AI,
            note = (json["note"] as? String)?.take(200)
        )
        return Outcome.Planned(draft, endpoint)
    }

    private fun describe(e: EndpointDef): String = buildString {
        append(e.method).append(' ').append(e.path).append(" - ").append(e.summary)
        val q = e.queryParams.take(6).joinToString(", ") { p -> p.name + (if (p.enumValues.isNotEmpty()) "(" + p.enumValues.take(6).joinToString("|") + ")" else "") }
        if (q.isNotEmpty()) append(" | query: ").append(q)
        val b = e.bodyFields.take(10).joinToString(", ") { f ->
            f.name + ":" + f.type + (if (f.required) "*" else "") + (if (f.enumValues.isNotEmpty()) "(" + f.enumValues.take(8).joinToString("|") + ")" else "")
        }
        if (b.isNotEmpty()) append(" | body: ").append(b)
    }

    private suspend fun runModel(accountId: String, system: String, user: String, maxTokens: Int): Map<String, Any?> {
        val payload = mapOf(
            "messages" to listOf(mapOf("role" to "system", "content" to system), mapOf("role" to "user", "content" to user)),
            "response_format" to mapOf("type" to "json_object"),
            "max_tokens" to maxTokens,
            "temperature" to 0
        )
        val response = client.execute(RawRequest("POST", "accounts/$accountId/ai/run/$model", body = Json.stringify(payload)))
        return parseModelResponse(response)
    }

    companion object {
        const val DEFAULT_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
        private const val MAX_CANDIDATES = 60

        /** Workers AI returns either `result.response` (an object or a JSON string) or an
         *  OpenAI-style `result.choices[0].message.content`, depending on the model. */
        @Suppress("UNCHECKED_CAST")
        internal fun parseModelResponse(response: RawResponse): Map<String, Any?> {
            val root = Json.parseOrNull(response.body) as? Map<String, Any?>
            if (!response.isSuccess || root == null || root["success"] == false) {
                val message = ((root?.get("errors") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("message")?.toString()
                throw PlannerError(
                    when (response.statusCode) {
                        401, 403 -> "This token can't run Workers AI. It needs the Workers AI permission."
                        429 -> "Workers AI is rate limited right now. Try again in a minute."
                        else -> message ?: "Workers AI returned HTTP ${response.statusCode}."
                    }
                )
            }
            val result = root["result"] as? Map<String, Any?> ?: throw PlannerError("Workers AI returned no result.")
            val direct = result["response"]
            val content: Any? = when {
                direct is Map<*, *> -> direct
                direct is String -> direct
                else -> ((result["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.let { (it["message"] as? Map<*, *>)?.get("content") }
            }
            return when (content) {
                is Map<*, *> -> content as Map<String, Any?>
                is String -> extractJsonObject(content) ?: throw PlannerError("Workers AI's answer wasn't a plan.")
                else -> throw PlannerError("Workers AI's answer wasn't a plan.")
            }
        }

        @Suppress("UNCHECKED_CAST")
        internal fun extractJsonObject(text: String): Map<String, Any?>? {
            (Json.parseOrNull(text.trim()) as? Map<String, Any?>)?.let { return it }
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return Json.parseOrNull(text.substring(start, end + 1)) as? Map<String, Any?>
        }
    }
}
