package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.LoadBalancerOrigin
import dev.cfmobile.app.data.remote.dto.LoadBalancerPoolWrite
import dev.cfmobile.app.data.remote.dto.LoadBalancerWrite
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class LoadBalancingRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: LoadBalancingRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = LoadBalancingRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listPools hits the account-level pools endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        repository.listPools("acct1")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/load_balancers/pools")
    }

    @Test
    fun `createPool sends origins in the request body`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"pool1","name":"primary","origins":[]}}"""))

        val result = repository.createPool(
            "acct1",
            LoadBalancerPoolWrite(name = "primary", origins = listOf(LoadBalancerOrigin(name = "origin-1", address = "203.0.113.10")))
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/load_balancers/pools")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body.readUtf8()).contains("203.0.113.10")
    }

    @Test
    fun `deletePool maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":1234,"message":"Pool not found"}],"result":null}""")
        )

        val result = repository.deletePool("acct1", "missing")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Pool not found")
    }

    @Test
    fun `listLoadBalancers hits the zone-level endpoint, not the account-level one`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        repository.listLoadBalancers("zone1")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/load_balancers")
    }

    @Test
    fun `createLoadBalancer sends the hostname and pool references`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"lb1","name":"www.example.com"}}"""))

        val result = repository.createLoadBalancer(
            "zone1",
            LoadBalancerWrite(name = "www.example.com", defaultPools = listOf("pool1"), fallbackPool = "pool1")
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/load_balancers")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"name\":\"www.example.com\"")
        assertThat(body).contains("\"fallback_pool\":\"pool1\"")
    }
}
