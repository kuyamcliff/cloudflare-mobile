package dev.cfmobile.app.core.api

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okio.Buffer
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.InputStream
import java.util.zip.GZIPInputStream

enum class ParamLocation { PATH, QUERY }

data class EndpointParam(
    val name: String,
    val location: ParamLocation,
    val type: String,
    val required: Boolean,
    val enumValues: List<String>,
    val description: String
)

/** One top-level request body property, from the schema (spec 315). [type] is a JSON schema
 *  type, or "any" when the schema allows several (a zone setting's value). */
data class BodyField(
    val name: String,
    val type: String,
    val required: Boolean,
    val enumValues: List<String> = emptyList(),
    val description: String = "",
    /** Item type when [type] is "array". */
    val itemType: String? = null,
    /** Schema default (when [isDefault]) or example, as a JSON scalar. */
    val example: Any? = null,
    val isDefault: Boolean = false
)

enum class EndpointScope { USER, ACCOUNT, ZONE, OTHER }

/** One Cloudflare API operation, from the generated registry (spec 315). */
data class EndpointDef(
    val id: String,
    val method: String,
    /** Relative to the API base, with OpenAPI placeholders: `zones/{zone_id}/dns_records`. */
    val path: String,
    val summary: String,
    val description: String,
    val group: String,
    /** Accepted token permissions, any of which authorizes the call (spec 316). */
    val permissions: List<String>,
    /** Plans the endpoint is limited to; empty when available on every plan. */
    val plans: List<String>,
    val deprecated: Boolean,
    val params: List<EndpointParam>,
    val bodyContentType: String?,
    /** Pretty-printed JSON example body, when the schema provides or implies one. */
    val bodyExample: String?,
    /** True when a native screen calls this exact method and path. */
    val native: Boolean,
    val bodyFields: List<BodyField> = emptyList(),
    val bodyRequired: Boolean = false
) {
    val scope: EndpointScope = when {
        path.startsWith("accounts/{") -> EndpointScope.ACCOUNT
        path.startsWith("zones/{") -> EndpointScope.ZONE
        path == "user" || path.startsWith("user/") -> EndpointScope.USER
        else -> EndpointScope.OTHER
    }

    val isDestructive: Boolean get() = method == "DELETE"
    val isMutation: Boolean get() = method != "GET"
    val pathParams: List<EndpointParam> get() = params.filter { it.location == ParamLocation.PATH }
    val queryParams: List<EndpointParam> get() = params.filter { it.location == ParamLocation.QUERY }
    private val segments: List<String> = path.split('/')

    fun matches(method: String, actualSegments: List<String>): Boolean {
        if (!this.method.equals(method, ignoreCase = true) || segments.size != actualSegments.size) return false
        for (i in segments.indices) {
            val t = segments[i]
            if (t.startsWith("{") && t.endsWith("}")) {
                if (actualSegments[i].isEmpty()) return false
            } else if (t != actualSegments[i]) {
                return false
            }
        }
        return true
    }

    /** Path placeholder names, in order. */
    fun placeholderNames(): List<String> = segments.filter { it.startsWith("{") && it.endsWith("}") }.map { it.trim('{', '}') }
}

class EndpointRegistry(
    val schemaRevision: String,
    val apiVersion: String,
    val generatedAt: String,
    val endpoints: List<EndpointDef>
) {
    private val bySegmentCount: Map<Int, List<EndpointDef>> = endpoints.groupBy { it.path.count { c -> c == '/' } + 1 }

    val groups: List<String> by lazy { endpoints.map { it.group }.distinct().sortedBy { it.lowercase() } }

    val permissionNames: List<String> by lazy { endpoints.flatMap { it.permissions }.distinct().sorted() }

    /** Finds the operation a concrete request path belongs to, e.g. to label history rows. */
    fun match(method: String, path: String): EndpointDef? {
        val segments = path.trim('/').substringBefore('?').split('/')
        return bySegmentCount[segments.size]?.firstOrNull { it.matches(method, segments) }
    }

    fun search(query: String, limit: Int = 200): List<EndpointDef> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return endpoints.take(limit)
        return endpoints.asSequence()
            .filter { e ->
                val hay = "${e.method} ${e.path} ${e.summary} ${e.group} ${e.permissions.joinToString(" ")}".lowercase()
                terms.all { hay.contains(it) }
            }
            .take(limit)
            .toList()
    }

    companion object {
        const val ASSET_NAME = "cf_endpoints.bin"

        fun load(gzipped: InputStream): EndpointRegistry =
            GZIPInputStream(gzipped).source().buffer().use { parse(it) }

        fun parse(source: BufferedSource): EndpointRegistry {
            val reader = JsonReader.of(source)
            var revision = ""
            var version = ""
            var generated = ""
            val endpoints = ArrayList<EndpointDef>(4096)
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "schemaRevision" -> revision = reader.nextString()
                    "apiVersion" -> version = reader.nextString()
                    "generatedAt" -> generated = reader.nextString()
                    "endpoints" -> {
                        reader.beginArray()
                        while (reader.hasNext()) endpoints += readEndpoint(reader)
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return EndpointRegistry(revision, version, generated, endpoints)
        }

        private fun readEndpoint(reader: JsonReader): EndpointDef {
            var id = ""; var method = "GET"; var path = ""; var summary = ""; var description = ""; var group = "Other"
            var perms = emptyList<String>(); var plans = emptyList<String>(); var deprecated = false; var native = false
            val params = ArrayList<EndpointParam>()
            var bodyType: String? = null
            var bodyExample: String? = null
            var bodyFields = emptyList<BodyField>()
            var bodyRequired = false
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextString()
                    "m" -> method = reader.nextString()
                    "p" -> path = reader.nextString()
                    "s" -> summary = reader.nextString()
                    "d" -> description = reader.nextString()
                    "g" -> group = reader.nextString()
                    "perm" -> perms = readStrings(reader)
                    "plan" -> plans = readStrings(reader)
                    "dep" -> deprecated = reader.nextInt() == 1
                    "n" -> native = reader.nextInt() == 1
                    "q" -> {
                        reader.beginArray()
                        while (reader.hasNext()) params += readParam(reader)
                        reader.endArray()
                    }
                    "b" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "ct" -> bodyType = reader.nextString()
                                "ex" -> bodyExample = prettyJson(reader.readJsonValue())
                                "f" -> bodyFields = readFields(reader)
                                "r" -> bodyRequired = reader.nextInt() == 1
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return EndpointDef(id, method, path, summary, description, group, perms, plans, deprecated, params, bodyType, bodyExample, native, bodyFields, bodyRequired)
        }

        private fun readFields(reader: JsonReader): List<BodyField> {
            val out = ArrayList<BodyField>()
            reader.beginArray()
            while (reader.hasNext()) {
                var name = ""; var type = "string"; var required = false; var enums = emptyList<String>()
                var desc = ""; var item: String? = null; var example: Any? = null; var isDefault = false
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "n" -> name = reader.nextString()
                        "t" -> type = reader.nextString()
                        "r" -> required = reader.nextInt() == 1
                        "e" -> enums = readStrings(reader)
                        "d" -> desc = reader.nextString()
                        "it" -> item = reader.nextString()
                        "x" -> example = reader.readJsonValue()
                        "def" -> isDefault = reader.nextInt() == 1
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                out += BodyField(name, type, required, enums, desc, item, example, isDefault)
            }
            reader.endArray()
            return out
        }

        private fun readParam(reader: JsonReader): EndpointParam {
            var name = ""; var loc = ParamLocation.QUERY; var type = "string"; var required = false
            var enums = emptyList<String>(); var desc = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "n" -> name = reader.nextString()
                    "in" -> loc = if (reader.nextString() == "path") ParamLocation.PATH else ParamLocation.QUERY
                    "t" -> type = reader.nextString()
                    "r" -> required = reader.nextInt() == 1
                    "e" -> enums = readStrings(reader)
                    "d" -> desc = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return EndpointParam(name, loc, type, required || loc == ParamLocation.PATH, enums, desc)
        }

        private fun readStrings(reader: JsonReader): List<String> {
            val out = ArrayList<String>()
            reader.beginArray()
            while (reader.hasNext()) out += reader.nextString()
            reader.endArray()
            return out
        }

        fun prettyJson(value: Any?): String {
            val buffer = Buffer()
            JsonWriter.of(buffer).use { w ->
                w.indent = "  "
                w.serializeNulls = true
                writeValue(w, value)
            }
            return buffer.readUtf8()
        }

        private fun writeValue(w: JsonWriter, value: Any?) {
            when (value) {
                null -> w.nullValue()
                is Map<*, *> -> {
                    w.beginObject()
                    value.forEach { (k, v) -> w.name(k.toString()); writeValue(w, v) }
                    w.endObject()
                }
                is List<*> -> {
                    w.beginArray()
                    value.forEach { writeValue(w, it) }
                    w.endArray()
                }
                is Boolean -> w.value(value)
                is Double -> if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) w.value(value.toLong()) else w.value(value)
                is Number -> w.value(value)
                else -> w.value(value.toString())
            }
        }
    }
}
