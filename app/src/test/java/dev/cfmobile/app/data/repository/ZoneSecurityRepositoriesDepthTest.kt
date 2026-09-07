package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.LockdownConfiguration
import dev.cfmobile.app.data.remote.dto.UserAgentRuleConfiguration
import dev.cfmobile.app.data.remote.dto.UserAgentRuleWrite
import dev.cfmobile.app.data.remote.dto.ZoneLockdownWrite
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Endpoint coverage for the zone security surfaces added together. */
class ZoneSecurityRepositoriesDepthTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listZoneLockdowns hits the legacy firewall endpoint`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"l1","urls":["a.com/*"],"configurations":[{"target":"ip","value":"1.1.1.1"}]}]}"""
            )
        )

        val result = ZoneFirewallLegacyRepository(testApi(server)).listLockdowns("zone1")

        assertThat((result as ApiResult.Success).data.single().urls).containsExactly("a.com/*")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/firewall/lockdowns")
    }

    @Test
    fun `createLockdown sends the urls and configurations`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"l1"}}"""))

        ZoneFirewallLegacyRepository(testApi(server)).createLockdown(
            "zone1",
            ZoneLockdownWrite(
                urls = listOf("a.com/*"),
                configurations = listOf(LockdownConfiguration(target = "country", value = "GB"))
            )
        )

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"urls\":[\"a.com/*\"]")
        assertThat(body).contains("\"target\":\"country\"")
    }

    @Test
    fun `createUserAgentRule posts to the ua_rules endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"u1"}}"""))

        ZoneFirewallLegacyRepository(testApi(server)).createUserAgentRule(
            "zone1",
            UserAgentRuleWrite(mode = "block", configuration = UserAgentRuleConfiguration(value = "BadBot"))
        )

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/firewall/ua_rules")
        assertThat(request.body.readUtf8()).contains("\"value\":\"BadBot\"")
    }

    @Test
    fun `listClientCertificates parses status and expiry`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"c1","common_name":"client.a.com","status":"active","expires_on":"2027-01-01"}]}"""
            )
        )

        val result = MutualTlsRepository(testApi(server)).listClientCertificates("zone1")

        val certificate = (result as ApiResult.Success).data.single()
        assertThat(certificate.commonName).isEqualTo("client.a.com")
        assertThat(certificate.expiresOn).isEqualTo("2027-01-01")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/client_certificates")
    }

    @Test
    fun `revoking a certificate deletes it by id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"c1","status":"revoked"}}"""))

        val result = MutualTlsRepository(testApi(server)).revokeClientCertificate("zone1", "c1")

        assertThat((result as ApiResult.Success).data.status).isEqualTo("revoked")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/zones/zone1/client_certificates/c1")
    }

    @Test
    fun `setOriginPulls puts the enabled flag`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":true}}"""))

        MutualTlsRepository(testApi(server)).setOriginPulls("zone1", true)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/zones/zone1/origin_tls_client_auth/settings")
        assertThat(request.body.readUtf8()).isEqualTo("""{"enabled":true}""")
    }

    @Test
    fun `setTotalTls only sends a certificate authority when enabling`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":true}}"""))
        MutualTlsRepository(testApi(server)).setTotalTls("zone1", enabled = true, certificateAuthority = "google")
        assertThat(server.takeRequest().body.readUtf8()).contains("\"certificate_authority\":\"google\"")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":false}}"""))
        MutualTlsRepository(testApi(server)).setTotalTls("zone1", enabled = false, certificateAuthority = "google")
        assertThat(server.takeRequest().body.readUtf8()).doesNotContain("certificate_authority")
    }

    @Test
    fun `createHold passes include_subdomains as a query parameter`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"hold":true,"include_subdomains":true}}"""))

        val result = ZoneOwnershipRepository(testApi(server)).createHold("zone1", includeSubdomains = true)

        assertThat((result as ApiResult.Success).data.includeSubdomains).isTrue()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/zones/zone1/hold?include_subdomains=true")
    }

    @Test
    fun `setZoneNameservers drops the set when turning custom nameservers off`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":false}}"""))

        ZoneOwnershipRepository(testApi(server)).setZoneNameservers("zone1", enabled = false, nsSet = 1)

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"enabled\":false")
        assertThat(body).doesNotContain("ns_set")
    }

    @Test
    fun `listAccountCustomNameservers hits the account endpoint`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"ns_name":"ns1.example.com","ns_set":1}]}""")
        )

        val result = ZoneOwnershipRepository(testApi(server)).listAccountNameservers("acct1")

        assertThat((result as ApiResult.Success).data.single().nsSet).isEqualTo(1)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/custom_ns")
    }
}
