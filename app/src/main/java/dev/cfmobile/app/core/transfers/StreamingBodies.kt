package dev.cfmobile.app.core.transfers

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Streams [length] bytes starting at [offset] of a user-picked file straight to the socket,
 * never holding more than one 64 KiB buffer in memory (spec 13, 188). Each write recomputes
 * the MD5, which is compared with R2's ETag to verify what arrived.
 */
class FileRangeRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val offset: Long,
    private val length: Long,
    private val mediaType: MediaType?,
    private val onProgress: (bytesSinceLastCall: Long) -> Unit
) : RequestBody() {
    @Volatile var md5Hex: String? = null
        private set
    private var writtenLastAttempt = 0L

    override fun contentType(): MediaType? = mediaType
    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        // OkHttp may write the body again on a retry; take back the previous attempt's bytes
        // so reported progress never exceeds what was actually sent.
        if (writtenLastAttempt > 0) onProgress(-writtenLastAttempt)
        writtenLastAttempt = 0
        val md5 = MessageDigest.getInstance("MD5")
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw IOException("Could not open the file")
        pfd.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                if (offset > 0) input.channel.position(offset)
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw IOException("File ended early; it may have changed since it was selected")
                    sink.write(buffer, 0, read)
                    md5.update(buffer, 0, read)
                    remaining -= read
                    writtenLastAttempt += read
                    onProgress(read.toLong())
                }
            }
        }
        md5Hex = md5.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Percent-encodes each segment of an object key while keeping its `/` separators, as the
 *  R2 REST API requires. */
fun encodeObjectKey(key: String): String = SigV4.encode(key, keepSlash = true)
