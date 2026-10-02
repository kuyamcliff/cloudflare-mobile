package dev.cfmobile.app.core.command

import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointRegistry

/** Cloudflare's response envelope, read generically so any operation's result can be shown. */
data class ParsedResult(
    val success: Boolean,
    val errors: List<String>,
    val messages: List<String>,
    val result: Any?,
    /** Set when the result is a list of objects (or wraps one), for the item list view. */
    val items: List<Map<String, Any?>>?,
    val page: Int?,
    val totalPages: Int?,
    val totalCount: Int?,
    val cursor: String?
) {
    val hasMore: Boolean get() = (page != null && totalPages != null && page < totalPages) || !cursor.isNullOrBlank()
}

object ResultModel {

    @Suppress("UNCHECKED_CAST")
    fun parse(body: String, statusCode: Int): ParsedResult {
        val root = Json.parseOrNull(body)
        val obj = root as? Map<String, Any?>
        if (obj == null || (!obj.containsKey("success") && !obj.containsKey("result") && !obj.containsKey("errors"))) {
            // Not Cloudflare's envelope (a raw script, a GraphQL answer, plain text).
            val items = (root as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.takeIf { it.isNotEmpty() }
            return ParsedResult(statusCode in 200..299, emptyList(), emptyList(), root ?: body, items, null, null, null, null)
        }
        val errors = messagesOf(obj["errors"])
        val messages = messagesOf(obj["messages"])
        val result = obj["result"]
        val info = obj["result_info"] as? Map<String, Any?>
        val items = listItems(result)
        return ParsedResult(
            success = (obj["success"] as? Boolean) ?: (statusCode in 200..299 && errors.isEmpty()),
            errors = errors,
            messages = messages,
            result = result,
            items = items,
            page = (info?.get("page") as? Number)?.toInt(),
            totalPages = (info?.get("total_pages") as? Number)?.toInt(),
            totalCount = (info?.get("total_count") as? Number)?.toInt(),
            cursor = (info?.get("cursor") as? String)?.takeIf { it.isNotBlank() } ?: (info?.get("cursors") as? Map<*, *>)?.get("after")?.toString()
        )
    }

    private fun messagesOf(v: Any?): List<String> = (v as? List<*>).orEmpty().mapNotNull { e ->
        when (e) {
            is Map<*, *> -> listOfNotNull(e["code"]?.let { c -> (c as? Number)?.toLong()?.toString() ?: c.toString() }, e["message"]?.toString())
                .joinToString(": ").ifBlank { null }
            is String -> e
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun listItems(result: Any?): List<Map<String, Any?>>? {
        if (result is List<*>) {
            val maps = result.filterIsInstance<Map<String, Any?>>()
            return if (maps.size == result.size) maps else result.map { mapOf("value" to it) }
        }
        val map = result as? Map<String, Any?> ?: return null
        // Some list operations wrap the array: {"buckets": [...]}, {"items": [...]}.
        val lists = map.entries.filter { (_, v) -> v is List<*> && v.isNotEmpty() && v.all { it is Map<*, *> } }
        if (lists.size == 1 && map.size <= 3) return (lists.first().value as List<Map<String, Any?>>)
        return null
    }

    private val TITLE_KEYS = listOf("name", "title", "hostname", "script_name", "email", "description", "domain", "pattern", "label", "value", "id", "uuid", "tag", "key")
    private val SUBTITLE_KEYS = listOf("type", "content", "status", "mode", "plan", "kind", "service", "action", "expression", "created_on", "modified_on")

    fun title(item: Map<String, Any?>): String {
        for (k in TITLE_KEYS) {
            val v = item[k]
            if (v is String && v.isNotBlank()) return v
            if (v is Number) return v.toString()
        }
        (item["plan"] as? Map<*, *>)?.get("name")?.toString()?.let { return it }
        return item.entries.firstOrNull { it.value is String }?.value?.toString() ?: "Item"
    }

    fun subtitle(item: Map<String, Any?>): String? {
        val title = title(item)
        val parts = SUBTITLE_KEYS.mapNotNull { k ->
            when (val v = item[k]) {
                is String -> v.takeIf { it.isNotBlank() && it != title }?.let { if (k.endsWith("_on")) it.take(10) else it }
                is Boolean -> if (v) k else null
                is Map<*, *> -> (v["name"] ?: v["value"])?.toString()
                else -> null
            }
        }.distinct().take(3)
        return parts.joinToString(" · ").take(120).ifBlank { null }
    }

    /** The identifier a path placeholder wants from an item: `{bucket_name}` takes the name,
     *  `{dns_record_id}` takes the id. */
    fun valueFor(placeholder: String, item: Map<String, Any?>): String? {
        val p = placeholder.lowercase()
        val stem = p.removeSuffix("_id").removeSuffix("_name").removeSuffix("_identifier").removeSuffix("_uuid").removeSuffix("_tag")
        val order = when {
            p.endsWith("name") -> listOf("name", "id", "script_name", "title")
            p.endsWith("uuid") -> listOf("uuid", "id")
            p.endsWith("tag") -> listOf("tag", "id")
            p == "hostname" -> listOf("hostname", "name")
            p.endsWith("key_name") || p == "key" -> listOf("name", "key")
            else -> listOf(p, "${stem}_id", "id", "uuid", "tag", "name")
        }
        for (k in listOf(p) + order) {
            val v = item[k]
            if (v is String && v.isNotBlank()) return v
            if (v is Number) return (v as? Double)?.takeIf { it % 1.0 == 0.0 }?.toLong()?.toString() ?: v.toString()
        }
        return null
    }

    data class FollowUp(val endpoint: EndpointDef, val label: String, val placeholder: String?)

    /**
     * Operations that continue from a result: for a list at `X`, the item operations at
     * `X/{id}` and the sub-collections under it; for a single item at `X/{id}`, its siblings.
     */
    fun followUps(registry: EndpointRegistry, method: String, template: String, isList: Boolean): List<FollowUp> {
        val base = if (isList) template else template.substringBeforeLast('/')
        val prefix = "$base/"
        val out = ArrayList<FollowUp>()
        for (e in registry.endpoints) {
            if (e.deprecated || !e.path.startsWith(prefix)) continue
            val rest = e.path.removePrefix(prefix).split('/')
            val head = rest.first()
            if (!(head.startsWith("{") && head.endsWith("}"))) continue
            if (rest.size > 2 || (rest.size == 2 && rest[1].startsWith("{"))) continue
            if (!isList && e.method == method && e.path == template) continue
            if (rest.size == 2 && e.method != "GET") continue
            out += FollowUp(e, labelFor(e, rest.size == 2), head.trim('{', '}'))
        }
        val order = mapOf("GET" to 0, "PATCH" to 1, "PUT" to 2, "POST" to 3, "DELETE" to 9)
        return out.distinctBy { it.endpoint.id }.sortedWith(compareBy({ it.endpoint.path.count { c -> c == '/' } }, { order[it.endpoint.method] ?: 5 }, { it.label }))
    }

    private fun labelFor(e: EndpointDef, sub: Boolean): String = when {
        sub -> e.summary
        e.method == "GET" -> "Details"
        e.method == "PATCH" -> "Edit"
        e.method == "PUT" -> "Replace"
        e.method == "DELETE" -> "Delete"
        else -> e.summary
    }

    /** Copies an item's current values into an edit body, limited to fields the operation takes. */
    fun editBody(endpoint: EndpointDef, item: Map<String, Any?>): Map<String, Any?> {
        val names = endpoint.bodyFields.map { it.name }.toSet()
        return item.filterKeys { it in names }.filterValues { it != null }
    }

    /** The verb for the run button, from the operation's own summary when it starts with one. */
    fun verb(endpoint: EndpointDef?, method: String): String {
        val first = endpoint?.summary?.trim()?.substringBefore(' ')?.replaceFirstChar { it.uppercase() }
        val known = setOf("Create", "Update", "Delete", "Purge", "List", "Get", "Edit", "Patch", "Add", "Remove", "Enable", "Disable",
            "Rotate", "Roll", "Verify", "Upload", "Run", "Revoke", "Reset", "Trigger", "Test", "Search", "Start", "Stop", "Restart", "Set", "Replace", "Query", "Send")
        return when {
            first != null && first in known && first !in setOf("Get", "List") -> first
            method == "GET" -> "Run"
            method == "DELETE" -> "Delete"
            method == "POST" -> "Send"
            else -> "Save"
        }
    }
}
