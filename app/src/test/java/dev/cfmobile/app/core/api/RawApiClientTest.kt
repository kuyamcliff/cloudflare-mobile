package dev.cfmobile.app.core.api

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.AuthInterceptor
import dev.cfmobile.app.data.remote.CloudflareHosts
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class RawApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RawApiClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val hosts = CloudflareHosts(server.url("/client/v4/").toString())
        client = RawApiClient(OkHttpClient.Builder().addInterceptor(AuthInterceptor({ "tok" }, hosts)).build(), hosts)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `absolute and scheme-relative targets are rejected`() {
        for (bad in listOf("https://evil.com/x", "//evil.com/x", "http://api.cloudflare.com/client/v4/zones", "zones/..\\x")) {
            assertThrows(RequestRejected::class.java) { client.buildUrl(bad, emptyList()) }
        }
    }

    @Test fun `dot segments and unfilled placeholders are rejected`() {
        assertThrows(RequestRejected::class.java) { client.buildUrl("zones/../../evil", emptyList()) }
        assertThrows(RequestRejected::class.java) { client.buildUrl("zones/{zone_id}/dns_records", emptyList()) }
    }

    @Test fun `paths are built under the API base and a pasted base prefix is tolerated`() {
        val url = client.buildUrl("/client/v4/zones/abc/dns_records", listOf("type" to "A"))
        assertThat(url.encodedPath).isEqualTo("/client/v4/zones/abc/dns_records")
        assertThat(url.queryParameter("type")).isEqualTo("A")
    }

    @Test fun `credential headers cannot be set by the user`() {
        for (h in listOf("Authorization", "cookie", "X-Auth-Key", "Host", "Proxy-Authorization")) {
            assertThrows(RequestRejected::class.java) {
                client.buildRequest(RawRequest("GET", "zones", headers = listOf(h to "x")))
            }
        }
    }

    @Test fun `executes with injected token and returns status and body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody("""{"success":true}"""))
        val response = client.execute(RawRequest("POST", "zones", body = """{"name":"example.com"}"""))
        assertThat(response.statusCode).isEqualTo(201)
        assertThat(response.body).contains("success")
        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer tok")
        assertThat(recorded.body.readUtf8()).isEqualTo("""{"name":"example.com"}""")
    }

    @Test fun `large bodies are truncated inline but saved in full when a destination is given`() = runBlocking {
        val big = "x".repeat((RawApiClient.MAX_INLINE_BYTES + 1000).toInt())
        server.enqueue(MockResponse().setBody(big))
        server.enqueue(MockResponse().setBody(big))
        val inline = client.execute(RawRequest("GET", "zones"))
        assertThat(inline.truncated).isTrue()
        assertThat(inline.body.length.toLong()).isEqualTo(RawApiClient.MAX_INLINE_BYTES)

        val out = ByteArrayOutputStream()
        val saved = client.execute(RawRequest("GET", "zones"), saveTo = out)
        assertThat(saved.totalBytes).isEqualTo(big.length.toLong())
        assertThat(out.size()).isEqualTo(big.length)
    }
}
