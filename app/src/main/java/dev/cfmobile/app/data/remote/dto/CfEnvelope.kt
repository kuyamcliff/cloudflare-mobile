package dev.cfmobile.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CfEnvelope<T>(
    val success: Boolean = false,
    // Nullable: some APIs (Queues, Durable Objects, Worker domains, zone holds) send
    // "errors": null on success, which a non-null property rejects outright.
    val errors: List<CfError>? = emptyList(),
    val messages: List<CfMessage>? = emptyList(),
    val result: T? = null,
    @Json(name = "result_info") val resultInfo: CfResultInfo? = null
)

@JsonClass(generateAdapter = true)
data class CfError(
    val code: Int = 0,
    val message: String = "Unknown error"
)

@JsonClass(generateAdapter = true)
data class CfMessage(
    val code: Int = 0,
    val message: String = ""
)

@JsonClass(generateAdapter = true)
data class CfResultInfo(
    val page: Int = 1,
    // Long: Load Balancing reports per_page as 9223372036854775807 when unpaginated.
    @Json(name = "per_page") val perPage: Long = 20,
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "total_pages") val totalPages: Int = 1,
    /** Cursor pagination (R2 objects, some newer APIs). */
    val cursor: String? = null,
    @Json(name = "is_truncated") val isTruncated: Boolean? = null,
    /** R2's equivalent of S3 CommonPrefixes when listing with a delimiter. */
    val delimited: List<String>? = null
)
