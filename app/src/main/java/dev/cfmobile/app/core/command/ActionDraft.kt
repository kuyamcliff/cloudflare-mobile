package dev.cfmobile.app.core.command

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okio.Buffer

/**
 * A prefilled, not yet sent API request. Every way of asking for something - a recipe, a
 * search hit, a Workers AI plan, a follow-up on a result - produces one of these, and the user
 * reviews it in the same form before anything is sent.
 */
data class ActionDraft(
    val method: String,
    /** Template with placeholders, as in the registry: `zones/{zone_id}/dns_records`. */
    val path: String,
    val title: String? = null,
    val pathValues: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    /** JSON object body as nested maps and lists; null for no body. */
    val body: Map<String, Any?>? = null,
    /** Reads with every required value present may load straight away. */
    val autoRun: Boolean = false,
    val source: Source = Source.SEARCH,
    /** Why the planner chose this, shown above the form. */
    val note: String? = null
) {
    enum class Source { RECIPE, SEARCH, AI, FOLLOW_UP, HISTORY }

    val isRead: Boolean get() = method.equals("GET", ignoreCase = true)
}

/** Small JSON helpers over Moshi's streaming API, shared by the command engine and forms. */
object Json {
    fun parse(text: String): Any? = JsonReader.of(Buffer().writeUtf8(text)).use { r ->
        r.isLenient = false
        val v = r.readJsonValue()
        if (r.peek() != JsonReader.Token.END_DOCUMENT) throw IllegalArgumentException("Unexpected content after JSON value")
        v
    }

    fun parseOrNull(text: String): Any? = runCatching { parse(text) }.getOrNull()

    fun stringify(value: Any?, pretty: Boolean = false): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { w ->
            if (pretty) w.indent = "  "
            w.serializeNulls = true
            write(w, value)
        }
        return buffer.readUtf8()
    }

    private fun write(w: JsonWriter, value: Any?) {
        when (value) {
            null -> w.nullValue()
            is Map<*, *> -> {
                w.beginObject()
                value.forEach { (k, v) -> w.name(k.toString()); write(w, v) }
                w.endObject()
            }
            is Iterable<*> -> {
                w.beginArray()
                value.forEach { write(w, it) }
                w.endArray()
            }
            is Boolean -> w.value(value)
            is Double -> if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) w.value(value.toLong()) else w.value(value)
            is Float -> write(w, value.toDouble())
            is Number -> w.value(value)
            else -> w.value(value.toString())
        }
    }

    /** Reads a dotted path (`configuration.value`, `actions.0.value.0`). */
    fun get(root: Any?, path: String): Any? {
        var cur = root
        for (part in path.split('.')) {
            cur = when (cur) {
                is Map<*, *> -> cur[part]
                is List<*> -> part.toIntOrNull()?.let { cur.getOrNull(it) }
                else -> return null
            }
        }
        return cur
    }

    /** Returns a copy of [root] with [value] written at a dotted path, creating containers. */
    fun set(root: Map<String, Any?>, path: String, value: Any?): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return setIn(root, path.split('.'), value) as Map<String, Any?>
    }

    /** Removes a top-level or dotted key. */
    fun remove(root: Map<String, Any?>, path: String): Map<String, Any?> {
        val parts = path.split('.')
        if (parts.size == 1) return root - path
        val parent = get(root, parts.dropLast(1).joinToString(".")) as? Map<*, *> ?: return root
        @Suppress("UNCHECKED_CAST")
        return set(root, parts.dropLast(1).joinToString("."), (parent as Map<String, Any?>) - parts.last())
    }

    private fun setIn(node: Any?, parts: List<String>, value: Any?): Any? {
        if (parts.isEmpty()) return value
        val head = parts.first()
        val index = head.toIntOrNull()
        return if (index != null && (node is List<*> || node == null)) {
            val list = (node as? List<*>)?.toMutableList() ?: mutableListOf()
            while (list.size <= index) list.add(null)
            list[index] = setIn(list[index], parts.drop(1), value)
            list
        } else {
            @Suppress("UNCHECKED_CAST")
            val map = LinkedHashMap((node as? Map<String, Any?>) ?: emptyMap())
            map[head] = setIn(map[head], parts.drop(1), value)
            map
        }
    }
}
