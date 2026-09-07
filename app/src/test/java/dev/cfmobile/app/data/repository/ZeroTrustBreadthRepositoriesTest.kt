package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AccessEmailDomainRule
import dev.cfmobile.app.data.remote.dto.AccessPolicyIncludeRule
import dev.cfmobile.app.data.remote.dto.GatewayLocationNetwork
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Endpoint coverage for the Zero Trust surfaces added together. */
class ZeroTrustBreadthRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `access groups are listed and created under the account`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"g1","name":"Team","include":[{"email_domain":{"domain":"a.com"}}]}]}""")
        )

        val result = AccessRepository(testApi(server)).listGroups("acct1")

        assertThat((result as ApiResult.Success).data.single().include.single().emailDomain?.domain).isEqualTo("a.com")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/access/groups")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"g2","name":"Team"}}"""))
        AccessRepository(testApi(server)).createGroup(
            "acct1",
            "Team",
            listOf(AccessPolicyIncludeRule(emailDomain = AccessEmailDomainRule("a.com")))
        )
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"name\":\"Team\"")
        assertThat(body).contains("\"email_domain\"")
    }

    @Test
    fun `bookmarks, tags, and mTLS roots each have their own endpoint`() = runBlocking {
        val repository = AccessRepository(testApi(server))

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        repository.listBookmarks("acct1")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/access/bookmarks")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        repository.listTags("acct1")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/access/tags")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        repository.listMtlsCertificates("acct1")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/access/certificates")
    }

    @Test
    fun `a tag is deleted by name, not by an id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        AccessRepository(testApi(server)).deleteTag("acct1", "internal")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/accounts/acct1/access/tags/internal")
    }

    @Test
    fun `gateway locations carry their source networks`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"l1","name":"Office","doh_subdomain":"abc","networks":[{"network":"203.0.113.0/24"}]}]}"""
            )
        )

        val result = GatewayRepository(testApi(server)).listLocations("acct1")

        assertThat((result as ApiResult.Success).data.single().networks?.single()?.network)
            .isEqualTo("203.0.113.0/24")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/gateway/locations")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"l2","name":"Office"}}"""))
        GatewayRepository(testApi(server)).createLocation(
            "acct1",
            "Office",
            clientDefault = true,
            networks = listOf(GatewayLocationNetwork(network = "203.0.113.0/24"))
        )
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"client_default\":true")
        assertThat(body).contains("203.0.113.0/24")
    }

    @Test
    fun `tunnel routes and virtual networks live under teamnet`() = runBlocking {
        val repository = TunnelsRepository(testApi(server))

        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","network":"10.0.0.0/8","tunnel_name":"office"}]}""")
        )
        val routes = repository.listRoutes("acct1")
        assertThat((routes as ApiResult.Success).data.single().tunnelName).isEqualTo("office")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/teamnet/routes")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"v1","name":"default"}]}"""))
        repository.listVirtualNetworks("acct1")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/teamnet/virtual_networks")
    }

    @Test
    fun `creating a route sends the network and tunnel`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"r1","network":"10.0.0.0/8"}}"""))

        TunnelsRepository(testApi(server)).createRoute("acct1", "10.0.0.0/8", "t1", "HQ", null)

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"network\":\"10.0.0.0/8\"")
        assertThat(body).contains("\"tunnel_id\":\"t1\"")
        assertThat(body).contains("\"comment\":\"HQ\"")
    }

    @Test
    fun `deleting a route maps Cloudflare's returned route to a unit result`() = runBlocking {
        // This endpoint answers with the deleted route, not an empty object.
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"r1","network":"10.0.0.0/8"}}"""))

        val result = TunnelsRepository(testApi(server)).deleteRoute("acct1", "r1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().method).isEqualTo("DELETE")
    }

    @Test
    fun `WARP device profiles come from the device policies endpoint`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"policy_id":"p1","name":"Default","default":true,"service_mode_v2":{"mode":"warp"}}]}"""
            )
        )

        val result = DevicePostureRepository(testApi(server)).listSettingsPolicies("acct1")

        assertThat((result as ApiResult.Success).data.single().serviceMode?.mode).isEqualTo("warp")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/devices/policies")
    }
}
