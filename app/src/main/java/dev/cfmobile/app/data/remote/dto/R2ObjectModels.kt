package dev.cfmobile.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class R2HttpMetadata(
    val contentType: String? = null,
    val contentDisposition: String? = null,
    val contentEncoding: String? = null,
    val contentLanguage: String? = null,
    val cacheControl: String? = null,
    val cacheExpiry: String? = null
)

@JsonClass(generateAdapter = true)
data class R2Object(
    val key: String = "",
    /** Cloudflare documents this as an integer in lists but a string in upload results. */
    val size: Any? = null,
    val etag: String? = null,
    @Json(name = "last_modified") val lastModified: String? = null,
    @Json(name = "storage_class") val storageClass: String? = null,
    @Json(name = "http_metadata") val httpMetadata: R2HttpMetadata? = null,
    @Json(name = "custom_metadata") val customMetadata: Map<String, String>? = null
) {
    val sizeBytes: Long get() = when (size) {
        is Number -> size.toLong()
        is String -> size.toLongOrNull() ?: 0L
        else -> 0L
    }
}

@JsonClass(generateAdapter = true)
data class R2UploadResult(
    val key: String = "",
    val etag: String? = null,
    val size: Any? = null,
    val version: String? = null,
    @Json(name = "storage_class") val storageClass: String? = null,
    val uploaded: String? = null
)
