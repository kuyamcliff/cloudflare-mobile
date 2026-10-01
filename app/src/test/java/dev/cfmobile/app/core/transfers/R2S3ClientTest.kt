package dev.cfmobile.app.core.transfers

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class R2S3ClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: R2S3Client
    private val creds = R2S3Credentials("AKID", "SECRET")

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = R2S3Client(OkHttpClient(), baseUrlOverride = server.url("/").toString())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `multipart create upload complete round trip`() = runBlocking {
        server.enqueue(MockResponse().setBody("<InitiateMultipartUploadResult><UploadId>up-1</UploadId></InitiateMultipartUploadResult>"))
        server.enqueue(MockResponse().setHeader("ETag", "\"etag-1\""))
        server.enqueue(MockResponse().setBody("<CompleteMultipartUploadResult><ETag>\"final-2\"</ETag></CompleteMultipartUploadResult>"))

        val acct = "0123456789abcdef0123456789abcdef"
        val id = client.createMultipartUpload(creds, acct, "media", "dir/my file.bin", "application/octet-stream")
        assertThat(id).isEqualTo("up-1")
        val create = server.takeRequest()
        assertThat(create.method).isEqualTo("POST")
        assertThat(create.path).isEqualTo("/media/dir/my%20file.bin?uploads=")
        assertThat(create.getHeader("Authorization")).startsWith("AWS4-HMAC-SHA256 Credential=AKID/")
        assertThat(create.getHeader("Authorization")).doesNotContain("SECRET")

        val etag = client.uploadPart(creds, acct, "media", "dir/my file.bin", id, 1, "hello".toRequestBody())
        assertThat(etag).isEqualTo("etag-1")
        val part = server.takeRequest()
        assertThat(part.path).isEqualTo("/media/dir/my%20file.bin?partNumber=1&uploadId=up-1")
        assertThat(part.getHeader("x-amz-content-sha256")).isEqualTo("UNSIGNED-PAYLOAD")

        val final = client.completeMultipartUpload(creds, acct, "media", "dir/my file.bin", id, listOf(1 to etag))
        assertThat(final).isEqualTo("final-2")
        assertThat(server.takeRequest().body.readUtf8()).contains("<PartNumber>1</PartNumber><ETag>\"etag-1\"</ETag>")
    }

    @Test fun `S3 errors surface R2 code and message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("<Error><Code>AccessDenied</Code><Message>Access Denied</Message></Error>"))
        val e = assertThrows(S3Exception::class.java) {
            runBlocking { client.createMultipartUpload(creds, "0123456789abcdef0123456789abcdef", "b", "k", null) }
        }
        assertThat(e.status).isEqualTo(403)
        assertThat(e.code).isEqualTo("AccessDenied")
        Unit
    }

    @Test fun `complete reports an error embedded in a 200 response`() {
        server.enqueue(MockResponse().setBody("<Error><Code>InternalError</Code></Error>"))
        assertThrows(S3Exception::class.java) {
            runBlocking { client.completeMultipartUpload(creds, "0123456789abcdef0123456789abcdef", "b", "k", "u", listOf(1 to "e")) }
        }
    }

    @Test fun `production endpoint only targets the account r2 host`() {
        val real = R2S3Client(OkHttpClient())
        assertThat(real.endpoint("0123456789abcdef0123456789abcdef", null).host).isEqualTo("0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com")
        assertThat(real.endpoint("0123456789abcdef0123456789abcdef", "eu").host).isEqualTo("0123456789abcdef0123456789abcdef.eu.r2.cloudflarestorage.com")
    }

    @Test fun `part size keeps uploads under the 10000 part limit`() {
        assertThat(TransferRepository.partSizeFor(100L * 1024 * 1024)).isEqualTo(8L * 1024 * 1024)
        val fiveTiB = 5L * 1024 * 1024 * 1024 * 1024
        val size = TransferRepository.partSizeFor(fiveTiB)
        assertThat((fiveTiB + size - 1) / size).isAtMost(10_000)
        assertThat(size % (1024 * 1024)).isEqualTo(0)
    }
}
