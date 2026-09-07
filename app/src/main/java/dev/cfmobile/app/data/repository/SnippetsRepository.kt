package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.Snippet
import dev.cfmobile.app.data.remote.dto.SnippetRule
import dev.cfmobile.app.data.remote.dto.SnippetRulesWrite
import dev.cfmobile.app.data.remote.extractMultipartContent
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/**
 * Snippets: the small JavaScript programs a zone runs on matching requests. Listing, reading
 * source, and deleting are here; uploading a snippet isn't, for the same reason Workers can't
 * be deployed from this app - it needs an editor, and Cloudflare wants the code as a multipart
 * upload alongside a metadata document.
 */
class SnippetsRepository(private val api: CloudflareApi) {

    suspend fun listSnippets(zoneId: String): ApiResult<List<Snippet>> =
        safeApiCall { api.listSnippets(zoneId) }

    /** The snippet's JavaScript. Cloudflare returns it as a file, so the body is read directly
     *  and unwrapped if it arrives as multipart. */
    suspend fun getContent(zoneId: String, snippetName: String): ApiResult<String> {
        return try {
            val response = api.getSnippetContent(zoneId, snippetName)
            if (response.isSuccessful) {
                ApiResult.Success(extractMultipartContent(response.body()?.string().orEmpty()))
            } else {
                ApiResult.Failure("HTTP ${response.code()}: ${response.message()}", response.code())
            }
        } catch (e: java.io.IOException) {
            ApiResult.Failure(e.message?.let { "Network error: $it" } ?: "Unable to reach Cloudflare")
        } catch (e: Exception) {
            ApiResult.Failure(e.message?.let { "Unexpected error: $it" } ?: "Unexpected error")
        }
    }

    suspend fun deleteSnippet(zoneId: String, snippetName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteSnippet(zoneId, snippetName) }

    suspend fun listRules(zoneId: String): ApiResult<List<SnippetRule>> =
        safeApiCall { api.listSnippetRules(zoneId) }

    /** Cloudflare replaces the zone's whole rule list, so callers send the full list they want
     *  to end up with rather than a single change. */
    suspend fun putRules(zoneId: String, rules: List<SnippetRule>): ApiResult<List<SnippetRule>> =
        safeApiCall { api.putSnippetRules(zoneId, SnippetRulesWrite(rules)) }
}
