package dev.cfmobile.app.core.api

import dev.cfmobile.app.data.remote.NetworkModule
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ParsedCurl(
    val request: RawRequest,
    /** Headers that were dropped because they carry credentials; reported, never kept. */
    val removedCredentialHeaders: List<String>
)

/**
 * cURL import and export for the API Explorer (spec 267, 268). Export always prints a
 * placeholder token. Import only accepts Cloudflare API URLs and strips every credential.
 */
object CurlCodec {

    fun export(url: String, method: String, headers: List<Pair<String, String>>, body: String?): String {
        val sb = StringBuilder("curl")
        if (method != "GET") sb.append(" -X ").append(method)
        sb.append(' ').append(quote(url))
        sb.append(" \\\n  -H ").append(quote("Authorization: Bearer <REDACTED>"))
        headers.filter { !it.first.equals("Authorization", true) }.forEach { (k, v) ->
            sb.append(" \\\n  -H ").append(quote("$k: $v"))
        }
        if (!body.isNullOrBlank()) {
            if (headers.none { it.first.equals("Content-Type", true) }) {
                sb.append(" \\\n  -H ").append(quote("Content-Type: application/json"))
            }
            sb.append(" \\\n  --data ").append(quote(body))
        }
        return sb.toString()
    }

    fun parse(command: String, apiBase: String = NetworkModule.BASE_URL): ParsedCurl {
        val tokens = tokenize(command.replace("\\\n", " ").replace("\\\r\n", " "))
        require(tokens.firstOrNull() == "curl") { "Not a cURL command" }
        var method: String? = null
        var url: String? = null
        val headers = mutableListOf<Pair<String, String>>()
        val removed = mutableListOf<String>()
        var body: String? = null
        var i = 1
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                t == "-X" || t == "--request" -> method = tokens.getOrNull(++i)?.uppercase()
                t == "-H" || t == "--header" -> tokens.getOrNull(++i)?.let { h ->
                    val name = h.substringBefore(':').trim()
                    val value = h.substringAfter(':', "").trim()
                    if (name.lowercase() in CREDENTIAL_HEADERS) removed += name else headers += name to value
                }
                t == "-d" || t == "--data" || t == "--data-raw" || t == "--data-binary" -> body = tokens.getOrNull(++i)
                t == "--url" -> url = tokens.getOrNull(++i)
                t.startsWith("-") -> { /* flags such as -s, --compressed carry no request meaning */ }
                url == null -> url = t
            }
            i++
        }
        val parsedUrl = requireNotNull(url?.toHttpUrlOrNull()) { "The command has no valid URL" }
        val base = requireNotNull(apiBase.toHttpUrlOrNull())
        require(parsedUrl.host.equals(base.host, true)) {
            "Only ${base.host} requests can be imported. The token is never sent to other hosts."
        }
        val path = parsedUrl.encodedPath.removePrefix(base.encodedPath).trimStart('/')
        val query = (0 until parsedUrl.querySize).map { parsedUrl.queryParameterName(it) to parsedUrl.queryParameterValue(it).orEmpty() }
        val finalMethod = method ?: if (body != null) "POST" else "GET"
        val contentType = headers.firstOrNull { it.first.equals("Content-Type", true) }?.second ?: "application/json"
        return ParsedCurl(
            RawRequest(
                method = finalMethod,
                path = path,
                query = query,
                headers = headers.filter { !it.first.equals("Content-Type", true) },
                body = body,
                contentType = contentType
            ),
            removed
        )
    }

    private val CREDENTIAL_HEADERS = setOf("authorization", "x-auth-key", "x-auth-email", "cookie", "x-auth-user-service-key")

    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Shell-style tokenizer supporting single quotes, double quotes and backslash escapes. */
    internal fun tokenize(input: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inToken = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '\'' -> {
                    inToken = true
                    val end = input.indexOf('\'', i + 1).let { if (it < 0) input.length else it }
                    cur.append(input, i + 1, end)
                    i = end
                }
                c == '"' -> {
                    inToken = true
                    i++
                    while (i < input.length && input[i] != '"') {
                        if (input[i] == '\\' && i + 1 < input.length && input[i + 1] in "\"\\$`") i++
                        cur.append(input[i])
                        i++
                    }
                }
                c == '\\' && i + 1 < input.length -> { inToken = true; cur.append(input[i + 1]); i++ }
                c.isWhitespace() -> if (inToken) { out += cur.toString(); cur.clear(); inToken = false }
                else -> { inToken = true; cur.append(c) }
            }
            i++
        }
        if (inToken) out += cur.toString()
        return out
    }
}
