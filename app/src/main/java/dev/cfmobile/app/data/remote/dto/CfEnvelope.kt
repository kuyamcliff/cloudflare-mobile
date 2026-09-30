package dev.cfmobile.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CfEnvelope<T>(
    val success: Boolean = false,
    val errors: List<CfError> = emptyList(),
    val messages: List<CfMessage> = emptyList(),
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
    @Json(name = "per_page") val perPage: Int = 20,
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "total_pages") val totalPages: Int = 1,
    /** Cursor pagination (R2 objects, some newer APIs). */
    val cursor: String? = null,
    @Json(name = "is_truncated") val isTruncated: Boolean? = null,
    /** R2's equivalent of S3 CommonPrefixes when listing with a delimiter. */
    val delimited: List<String>? = null
)
