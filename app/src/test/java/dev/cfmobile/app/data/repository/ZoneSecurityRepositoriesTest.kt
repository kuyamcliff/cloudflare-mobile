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

/** Security Events (GraphQL), Page Shield, DDoS, and API Shield. */
class ZoneSecurityRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listEvents posts a GraphQL query and unwraps the nested zone result`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"viewer":{"zones":[{"firewallEventsAdaptive":[{"action":"block","clientIP":"203.0.113.10"}]}]}},"errors":null}"""
            )
        )

        val result = SecurityEventsRepository(testApi(server)).listEvents("zone1")

        assertThat((result as ApiResult.Success).data.single().action).isEqualTo("block")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/graphql")
        val body = request.body.readUtf8()
        assertThat(body).contains("firewallEventsAdaptive")
        assertThat(body).contains("\"zoneTag\":\"zone1\"")
    }

    @Test
    fun `listEvents treats a GraphQL errors array on HTTP 200 as a failure`() = runBlocking {
        // GraphQL reports query failures with a 200 status, so the errors array is the only
        // signal that something went wrong.
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":null,"errors":[{"message":"zone does not have access to firewallEventsAdaptive"}]}""")
        )

        val result = SecurityEventsRepository(testApi(server)).listEvents("zone1")

        assertThat((result as ApiResult.Failure).message).contains("does not have access")
    }

    @Test
    fun `listEvents returns an empty list when the zone reports no events`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"viewer":{"zones":[]}},"errors":null}"""))

        val result = SecurityEventsRepository(testApi(server)).listEvents("zone1")

        assertThat((result as ApiResult.Success).data).isEmpty()
    }

    @Test
    fun `page shield settings, scripts, and connections hit their endpoints`() = runBlocking {
        val repository = PageShieldRepository(testApi(server))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":true}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"s1","host":"cdn.example.com","js_integrity_score":4}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"c1","host":"analytics.example.com"}]}"""))

        val settings = repository.getSettings("zone1")
        val scripts = repository.listScripts("zone1")
        val connections = repository.listConnections("zone1")

        assertThat((settings as ApiResult.Success).data.enabled).isTrue()
        assertThat((scripts as ApiResult.Success).data.single().jsIntegrityScore).isEqualTo(4)
        assertThat((connections as ApiResult.Success).data.single().host).isEqualTo("analytics.example.com")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/page_shield")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/page_shield/scripts")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/page_shield/connections")
    }

    @Test
    fun `setEnabled PUTs the page shield flag`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":false}}"""))

        val result = PageShieldRepository(testApi(server)).setEnabled("zone1", false)

        assertThat((result as ApiResult.Success).data.enabled).isFalse()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.body.readUtf8()).contains("\"enabled\":false")
    }

    @Test
    fun `getDdosEntrypoint reads the ddos_l7 phase entrypoint`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"r1","name":"DDoS L7","rules":[{"id":"rule1","description":"HTTP flood","action":"managed_challenge","enabled":true}]}}"""
            )
        )

        val result = DdosRepository(testApi(server)).getEntrypoint("zone1")

        assertThat((result as ApiResult.Success).data.rules.single().action).isEqualTo("managed_challenge")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/rulesets/phases/ddos_l7/entrypoint")
    }

    @Test
    fun `getDdosEntrypoint surfaces a 404 with its http code so callers can treat it as empty`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))

        val result = DdosRepository(testApi(server)).getEntrypoint("zone1")

        assertThat((result as ApiResult.Failure).httpCode).isEqualTo(404)
    }

    @Test
    fun `listOperations reads discovered API endpoints`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"operation_id":"o1","method":"GET","endpoint":"/api/users/{id}"}]}""")
        )

        val result = ApiShieldRepository(testApi(server)).listOperations("zone1")

        assertThat((result as ApiResult.Success).data.single().endpoint).isEqualTo("/api/users/{id}")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/api_gateway/operations")
    }
}
