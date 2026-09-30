package dev.cfmobile.app.core.transfers

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class FileRangeRequestBodyTest {
    @Test fun `writes exactly the requested range with its md5 and never overcounts progress`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val file = File(context.cacheDir, "range.bin").apply { writeBytes(data) }
        var progress = 0L
        val body = FileRangeRequestBody(context.contentResolver, Uri.fromFile(file), offset = 100_000, length = 150_000, mediaType = null) { progress += it }

        val first = Buffer().also { body.writeTo(it) }
        val second = Buffer().also { body.writeTo(it) } // a retry rewrites the body

        val expected = data.copyOfRange(100_000, 250_000)
        assertThat(first.readByteArray()).isEqualTo(expected)
        assertThat(second.readByteArray()).isEqualTo(expected)
        assertThat(body.contentLength()).isEqualTo(150_000)
        assertThat(progress).isEqualTo(150_000)
        val md5 = MessageDigest.getInstance("MD5").digest(expected).joinToString("") { "%02x".format(it) }
        assertThat(body.md5Hex).isEqualTo(md5)
        assertThat(encodeObjectKey("a b/ü.txt")).isEqualTo("a%20b/%C3%BC.txt")
    }
}
