package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.ManagedHeader
import dev.cfmobile.app.data.remote.dto.ManagedHeaders
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The routing and cache endpoints that sit outside /zones/{id}/settings. */
class PerformanceRepositoryTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `smart routing reads and writes its own argo endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"smart_routing","value":"on"}}"""))

        val result = PerformanceRepository(testApi(server)).getSmartRouting("zone1")

        assertThat((result as ApiResult.Success).data.value).isEqualTo("on")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/argo/smart_routing")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"value":"off"}}"""))
        PerformanceRepository(testApi(server)).setSmartRouting("zone1", "off")
        val write = server.takeRequest()
        assertThat(write.method).isEqualTo("PATCH")
        assertThat(write.body.readUtf8()).isEqualTo("""{"value":"off"}""")
    }

    @Test
    fun `cache reserve lives under the cache endpoint, not argo`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"cache_reserve","value":"on"}}"""))

        PerformanceRepository(testApi(server)).getCacheReserve("zone1")

        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/cache/cache_reserve")
    }

    @Test
    fun `the two tiered cache topology settings are separate endpoints`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"value":"on"}}"""))
        PerformanceRepository(testApi(server)).getRegionalTieredCache("zone1")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/cache/regional_tiered_cache")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"value":"on"}}"""))
        PerformanceRepository(testApi(server)).getSmartTieredCache("zone1")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/cache/tiered_cache_smart_topology_enable")
    }

    @Test
    fun `managed headers parse both request and response lists`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{
                    "managed_request_headers":[{"id":"add_true_client_ip_headers","enabled":true}],
                    "managed_response_headers":[{"id":"remove_x_powered_by_header","enabled":false,"has_conflict":true,"conflicts_with":["my_rule"]}]}}"""
            )
        )

        val result = PerformanceRepository(testApi(server)).getManagedHeaders("zone1")

        val headers = (result as ApiResult.Success).data
        assertThat(headers.requestHeaders.single().enabled).isTrue()
        assertThat(headers.responseHeaders.single().conflictsWith).containsExactly("my_rule")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/managed_headers")
    }

    @Test
    fun `setManagedHeaders patches the whole document`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"managed_request_headers":[],"managed_response_headers":[]}}"""))

        PerformanceRepository(testApi(server)).setManagedHeaders(
            "zone1",
            ManagedHeaders(
                requestHeaders = listOf(ManagedHeader(id = "a", enabled = true)),
                responseHeaders = listOf(ManagedHeader(id = "b", enabled = false))
            )
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        val body = request.body.readUtf8()
        assertThat(body).contains("managed_request_headers")
        assertThat(body).contains("managed_response_headers")
    }

    @Test
    fun `url normalization is a PUT of type and scope`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"type":"rfc3986","scope":"both"}}"""))

        val result = PerformanceRepository(testApi(server)).setUrlNormalization("zone1", "rfc3986", "both")

        assertThat((result as ApiResult.Success).data.scope).isEqualTo("both")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/zones/zone1/url_normalization")
        assertThat(request.body.readUtf8()).contains("\"type\":\"rfc3986\"")
    }
}
