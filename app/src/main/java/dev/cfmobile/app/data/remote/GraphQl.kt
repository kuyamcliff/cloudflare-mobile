package dev.cfmobile.app.data.remote

import com.squareup.moshi.JsonClass

/**
 * Cloudflare's analytics GraphQL API answers in the standard GraphQL envelope rather than the
 * REST `CfEnvelope` shape, and reports query failures as a populated `errors` array on an
 * HTTP 200 - see [safeGraphQlCall], which treats that as the failure signal.
 */
@JsonClass(generateAdapter = true)
data class GraphQlResponse<T>(
    val data: T? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class GraphQlError(
    val message: String = "Unknown GraphQL error"
)

@JsonClass(generateAdapter = true)
data class GraphQlRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap()
)
