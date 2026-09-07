package dev.cfmobile.app.data.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import dev.cfmobile.app.data.remote.UploadPayload
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Streams a file the user picked - a `content://` URI from Android's photo picker - straight
 * into a request. Reading it into a ByteArray first would work for a photo and fail for a
 * video, so the bytes are copied from the content provider to the socket as OkHttp writes
 * them.
 */
private class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val length: Long
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    /** -1 tells OkHttp the length is unknown, which makes it chunk the upload. */
    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("Couldn't open the selected file")
        stream.use { sink.writeAll(it.source()) }
    }
}

/**
 * Reads the picked file's display name, size, and MIME type from the content provider and
 * wraps it for upload. Returns null when the provider can't be read at all - a URI whose
 * permission has already lapsed, say - so the caller can say so rather than sending an empty
 * file.
 */
fun contentUriPayload(resolver: ContentResolver, uri: Uri, fallbackName: String): UploadPayload? {
    return try {
        var name = fallbackName
        var length = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) length = cursor.getLong(sizeIndex)
                }
            }
        val mediaType = resolver.getType(uri)?.toMediaTypeOrNull()
        UploadPayload(
            fileName = name,
            body = ContentUriRequestBody(resolver, uri, mediaType, length)
        )
    } catch (e: SecurityException) {
        null
    } catch (e: FileNotFoundException) {
        null
    }
}
