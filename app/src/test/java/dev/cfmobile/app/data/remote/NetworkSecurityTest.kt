package dev.cfmobile.app.data.remote

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test

class NetworkSecurityTest {
    private lateinit var api: MockWebServer
    private lateinit var other: MockWebServer

    @Before fun setUp() {
        api = MockWebServer().apply { start() }
        other = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        api.shutdown(); other.shutdown()
    }

    private fun client(recorder: RequestRecorder? = null, status: NetworkStatus? = null, sleeps: MutableList<Long>? = null): OkHttpClient {
        val hosts = CloudflareHosts(api.url("/client/v4/").toString())
        return OkHttpClient.Builder()
            .addInterceptor(RequestHistoryInterceptor(hosts, recorder, status))
            .addInterceptor(RetryPolicyInterceptor(status, sleeper = { sleeps?.add(it) }))
            .addInterceptor(AuthInterceptor({ "secret-token" }, hosts))
            .build()
    }

    @Test fun `token is attached only for the API host`() {
        api.enqueue(MockResponse().setBody("{}"))
        other.enqueue(MockResponse().setBody("{}"))
        val c = client()
        c.newCall(Request.Builder().url(api.url("/client/v4/zones")).build()).execute().close()
        c.newCall(Request.Builder().url(other.url("/steal")).header("Authorization", "Bearer smuggled").build()).execute().close()

        assertThat(api.takeRequest().getHeader("Authorization")).isEqualTo("Bearer secret-token")
        // Different port means a different origin: no token, and a caller-set header is stripped.
        assertThat(other.takeRequest().getHeader("Authorization")).isNull()
    }

    @Test fun `429 honors Retry-After then succeeds`() {
        api.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3"))
        api.enqueue(MockResponse().setBody("ok"))
        val sleeps = mutableListOf<Long>()
        val status = NetworkStatus()
        val response = client(status = status, sleeps = sleeps).newCall(Request.Builder().url(api.url("/client/v4/zones")).build()).execute()
        assertThat(response.code).isEqualTo(200)
        assertThat(sleeps.sum()).isEqualTo(3000)
        assertThat(api.requestCount).isEqualTo(2)
    }

    @Test fun `429 with a long Retry-After is returned instead of waited out`() {
        api.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "300"))
        val status = NetworkStatus()
        val response = client(status = status).newCall(Request.Builder().url(api.url("/client/v4/zones")).build()).execute()
        assertThat(response.code).isEqualTo(429)
        assertThat(api.requestCount).isEqualTo(1)
        assertThat(status.health()).isEqualTo(ApiHealth.RATE_LIMITED)
    }

    @Test fun `503 is retried for GET but never for POST`() {
        api.enqueue(MockResponse().setResponseCode(503))
        api.enqueue(MockResponse().setBody("ok"))
        val get = client(sleeps = mutableListOf()).newCall(Request.Builder().url(api.url("/client/v4/zones")).build()).execute()
        assertThat(get.code).isEqualTo(200)

        api.enqueue(MockResponse().setResponseCode(503))
        val post = client(sleeps = mutableListOf()).newCall(
            Request.Builder().url(api.url("/client/v4/zones")).post("{}".toRequestBody()).build()
        ).execute()
        assertThat(post.code).isEqualTo(503)
        assertThat(api.requestCount).isEqualTo(3)
    }

    @Test fun `400 401 403 are never retried`() {
        for (code in listOf(400, 401, 403)) {
            api.enqueue(MockResponse().setResponseCode(code))
            val r = client(sleeps = mutableListOf()).newCall(Request.Builder().url(api.url("/client/v4/zones")).build()).execute()
            assertThat(r.code).isEqualTo(code)
        }
        assertThat(api.requestCount).isEqualTo(3)
    }

    @Test fun `history records metadata only with credential-like query values redacted`() {
        api.enqueue(MockResponse().setBody("{}"))
        val records = mutableListOf<RequestRecord>()
        client(recorder = { records += it }).newCall(
            Request.Builder().url(api.url("/client/v4/zones?name=example.com&api_token=abc123&cf_signature=x")).build()
        ).execute().close()
        val r = records.single()
        assertThat(r.path).isEqualTo("zones")
        assertThat(r.query).isEqualTo("name=example.com&api_token=REDACTED&cf_signature=REDACTED")
        assertThat(r.toString()).doesNotContain("secret-token")
        assertThat(r.statusCode).isEqualTo(200)
    }

    @Test fun `log lines template identifiers and never include headers`() {
        api.enqueue(MockResponse().setBody("{}"))
        val lines = mutableListOf<String>()
        val c = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor({ "secret-token" }, CloudflareHosts(api.url("/client/v4/").toString())))
            .addInterceptor(RedactingLogInterceptor(true) { lines += it })
            .build()
        c.newCall(Request.Builder().url(api.url("/client/v4/zones/023e105f4ecef8ad9ca31a8372d0c353/dns_records?page=2")).build()).execute().close()
        assertThat(lines.single()).startsWith("GET /client/v4/zones/{id}/dns_records status=200")
        assertThat(lines.single()).doesNotContain("secret-token")
        assertThat(lines.single()).doesNotContain("page=2")
    }

    @Test fun `release logging is a pass-through`() {
        api.enqueue(MockResponse().setBody("{}"))
        val lines = mutableListOf<String>()
        OkHttpClient.Builder().addInterceptor(RedactingLogInterceptor(false) { lines += it }).build()
            .newCall(Request.Builder().url(api.url("/x")).build()).execute().close()
        assertThat(lines).isEmpty()
    }

    @Test fun `r2 S3 host pattern accepts only account endpoints over https`() {
        assertThat(CloudflareHosts.isR2S3Host("https://023e105f4ecef8ad9ca31a8372d0c353.r2.cloudflarestorage.com/b".toHttpUrl())).isTrue()
        assertThat(CloudflareHosts.isR2S3Host("https://023e105f4ecef8ad9ca31a8372d0c353.eu.r2.cloudflarestorage.com/b".toHttpUrl())).isTrue()
        assertThat(CloudflareHosts.isR2S3Host("https://evil.com/023e105f4ecef8ad9ca31a8372d0c353.r2.cloudflarestorage.com".toHttpUrl())).isFalse()
        assertThat(CloudflareHosts.isR2S3Host("https://x.r2.cloudflarestorage.com.evil.com/".toHttpUrl())).isFalse()
    }
}
