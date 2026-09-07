package dev.cfmobile.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody

/**
 * A file chosen on the device, ready to send. The bytes stay behind [body] rather than being
 * read into memory here, so a large video streams straight from the content provider to the
 * socket instead of being buffered whole.
 */
data class UploadPayload(
    val fileName: String,
    val body: RequestBody
) {
    /** Cloudflare's upload endpoints all take the file in a part named "file". */
    fun toPart(): MultipartBody.Part = MultipartBody.Part.createFormData("file", fileName, body)
}
