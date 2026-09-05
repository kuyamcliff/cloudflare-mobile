package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class TunnelsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TunnelsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = TunnelsRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listTunnels hits the account-level cfd_tunnel endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"tun1","name":"my-tunnel","status":"inactive"}]}"""))

        val result = repository.listTunnels("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().name).isEqualTo("my-tunnel")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/cfd_tunnel")
    }

    @Test
    fun `createTunnel sends the name with a remotely-managed config_src`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"tun2","name":"my-tunnel","status":"inactive"}}"""))

        val result = repository.createTunnel("acct1", "my-tunnel")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body.readUtf8()).contains("\"config_src\":\"cloudflare\"")
    }

    @Test
    fun `deleteTunnel maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":1000,"message":"Tunnel not found"}],"result":null}""")
        )

        val result = repository.deleteTunnel("acct1", "missing")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Tunnel not found")
    }
}
