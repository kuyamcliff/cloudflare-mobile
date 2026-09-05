package dev.cfmobile.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SafeApiCallTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CloudflareApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = testApi(server)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful envelope returns Success with result`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"messages":[],"result":{"id":"abc123","status":"active"}}"""
            )
        )

        val result = safeApiCall { api.verifyToken() }

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.status).isEqualTo("active")
    }

    @Test
    fun `non-2xx response surfaces Cloudflare's own error message`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":1003,"message":"Invalid zone identifier"}],"result":null}""")
        )

        val result = safeApiCall { api.getZone("bad-id") }

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        val failure = result as ApiResult.Failure
        assertThat(failure.message).contains("Invalid zone identifier")
        assertThat(failure.httpCode).isEqualTo(400)
    }

    @Test
    fun `success true but null result is treated as a failure`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":null}"""))

        val result = safeApiCall { api.getZone("z1") }

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
    }

    @Test
    fun `unreachable server is reported as a network failure, not a crash`() = runBlocking {
        val unreachableApi = testApi(server)
        server.shutdown()

        val result = safeApiCall { unreachableApi.verifyToken() }

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).ignoringCase().contains("network")
    }
}
