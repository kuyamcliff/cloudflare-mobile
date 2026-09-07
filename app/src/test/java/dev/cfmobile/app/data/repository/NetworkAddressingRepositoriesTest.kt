package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.MagicRouteWrite
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Endpoint coverage for addressing, Magic WAN writes, and Magic Firewall. */
class NetworkAddressingRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `a prefix carries the flags that decide whether it can be announced`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"p1","cidr":"203.0.113.0/24","approved":"approved","on_demand_enabled":true,"on_demand_locked":false,"advertised":true,"asn":64512}]}"""
            )
        )

        val result = AddressingRepository(testApi(server)).listPrefixes("acct1")

        val prefix = (result as ApiResult.Success).data.single()
        assertThat(prefix.approved).isEqualTo("approved")
        assertThat(prefix.onDemandEnabled).isTrue()
        assertThat(prefix.advertised).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/addressing/prefixes")
    }

    @Test
    fun `advertisement is read and written on its own sub-resource`(): Unit = runBlocking {
        val repository = AddressingRepository(testApi(server))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"advertised":true}}"""))

        val status = repository.getBgpStatus("acct1", "p1")

        assertThat((status as ApiResult.Success).data.advertised).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/addressing/prefixes/p1/bgp/status")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"advertised":false}}"""))
        repository.setAdvertised("acct1", "p1", false)
        val patch = server.takeRequest()
        assertThat(patch.method).isEqualTo("PATCH")
        assertThat(patch.body.readUtf8()).isEqualTo("""{"advertised":false}""")
    }

    @Test
    fun `a prefix description is patched on the prefix itself`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"p1","description":"EU space"}}"""))

        val result = AddressingRepository(testApi(server)).setDescription("acct1", "p1", "EU space")

        assertThat((result as ApiResult.Success).data.description).isEqualTo("EU space")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/addressing/prefixes/p1")
        assertThat(request.body.readUtf8()).isEqualTo("""{"description":"EU space"}""")
    }

    @Test
    fun `only the single address map read carries its ips and memberships`(): Unit = runBlocking {
        val repository = AddressingRepository(testApi(server))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"m1","description":"EU"}]}"""))

        val listed = repository.listAddressMaps("acct1")

        assertThat((listed as ApiResult.Success).data.single().ips).isNull()

        server.takeRequest()
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"m1","ips":[{"ip":"192.0.2.1"}],"memberships":[{"identifier":"z1","kind":"zone"}]}}"""
            )
        )
        val single = repository.getAddressMap("acct1", "m1")
        assertThat((single as ApiResult.Success).data.ips?.single()?.ip).isEqualTo("192.0.2.1")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/addressing/address_maps/m1")
    }

    @Test
    fun `a magic route create unwraps the routes Cloudflare answers with`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"routes":[{"id":"r1","prefix":"10.0.0.0/8"}]}}""")
        )

        val result = MagicNetworkRepository(testApi(server))
            .createRoute("acct1", MagicRouteWrite(prefix = "10.0.0.0/8", nexthop = "10.0.0.1", priority = 100))

        assertThat((result as ApiResult.Success).data.single().id).isEqualTo("r1")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/magic/routes")
    }

    @Test
    fun `a magic route delete succeeds even though it echoes the deleted route`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"deleted":true,"deleted_route":{"id":"r1","prefix":"10.0.0.0/8"}}}""")
        )

        val result = MagicNetworkRepository(testApi(server)).deleteRoute("acct1", "r1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/magic/routes/r1")
    }

    @Test
    fun `site interfaces arrive nested under their own keys`(): Unit = runBlocking {
        val repository = MagicNetworkRepository(testApi(server))
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"lans":[{"id":"l1","name":"office","static_addressing":{"address":"192.168.1.1/24"}}]}}""")
        )

        val lans = repository.listSiteLans("acct1", "s1")

        assertThat((lans as ApiResult.Success).data.single().staticAddressing?.address).isEqualTo("192.168.1.1/24")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/magic/sites/s1/lans")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"wans":[{"id":"w1","priority":10}]}}"""))
        val wans = repository.listSiteWans("acct1", "s1")
        assertThat((wans as ApiResult.Success).data.single().priority).isEqualTo(10)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/magic/sites/s1/wans")
    }

    @Test
    fun `magic firewall treats a missing ruleset as no rules, not as a failure`(): Unit = runBlocking {
        // Cloudflare has no entrypoint ruleset until the account's first rule exists.
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":10005,"message":"could not find ruleset"}],"result":null}""")
        )

        val result = MagicFirewallRepository(testApi(server)).getRuleset("acct1")

        assertThat((result as ApiResult.Success).data).isNull()
        assertThat(server.takeRequest().path)
            .isEqualTo("/accounts/acct1/rulesets/phases/magic_transit/entrypoint")
    }

    @Test
    fun `a real magic firewall failure stays a failure`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Unauthorized"}],"result":null}""")
        )

        val result = MagicFirewallRepository(testApi(server)).getRuleset("acct1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
    }

    @Test
    fun `the first magic firewall rule PUTs the phase, later ones POST`(): Unit = runBlocking {
        val repository = MagicFirewallRepository(testApi(server))
        val rule = RulesetRuleWrite(action = "block", expression = "tcp.dstport == 22", description = "Drop SSH")
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[]}}"""))

        repository.addRule("acct1", existingRulesetId = null, rule = rule)

        val put = server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.path).isEqualTo("/accounts/acct1/rulesets/phases/magic_transit/entrypoint")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[]}}"""))
        repository.addRule("acct1", existingRulesetId = "rs1", rule = rule)
        val post = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.path).isEqualTo("/accounts/acct1/rulesets/rs1/rules")
    }
}
