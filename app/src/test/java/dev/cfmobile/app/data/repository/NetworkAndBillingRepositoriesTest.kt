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

/** Email Routing, Spectrum, Magic WAN, Billing, and Browser Rendering. */
class NetworkAndBillingRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `createForwardRule builds Cloudflare's matcher and action shape`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"tag":"r1","name":"Support"}}"""))

        val result = EmailRoutingRepository(testApi(server))
            .createForwardRule("zone1", "Support", "hello@example.com", "me@gmail.com")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"type\":\"literal\"")
        assertThat(body).contains("\"field\":\"to\"")
        assertThat(body).contains("\"value\":\"hello@example.com\"")
        assertThat(body).contains("\"type\":\"forward\"")
        assertThat(body).contains("me@gmail.com")
    }

    @Test
    fun `listEmailRoutingRules parses matchers and actions`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"tag":"r1","name":"Support","enabled":true,"matchers":[{"type":"literal","field":"to","value":"hello@example.com"}],"actions":[{"type":"forward","value":["me@gmail.com"]}]}]}"""
            )
        )

        val result = EmailRoutingRepository(testApi(server)).listRules("zone1")

        val rule = (result as ApiResult.Success).data.single()
        assertThat(rule.matchers.single().value).isEqualTo("hello@example.com")
        assertThat(rule.actions.single().value).containsExactly("me@gmail.com")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/email/routing/rules")
    }

    // Explicit Unit: the last expression is a Truth assertion that returns Ordered, and a
    // JUnit4 test method has to be void.
    @Test
    fun `listSpectrumApps parses the nested dns block`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"a1","protocol":"tcp/22","dns":{"type":"CNAME","name":"ssh.example.com"},"origin_direct":["tcp://203.0.113.10:22"]}]}"""
            )
        )

        val result = SpectrumRepository(testApi(server)).listApps("zone1")

        val app = (result as ApiResult.Success).data.single()
        assertThat(app.dns?.name).isEqualTo("ssh.example.com")
        assertThat(app.originDirect).containsExactly("tcp://203.0.113.10:22")
    }

    @Test
    fun `magic network lists unwrap their nested keys`() = runBlocking {
        val repository = MagicNetworkRepository(testApi(server))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"gre_tunnels":[{"id":"g1","name":"site-a"}]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"ipsec_tunnels":[{"id":"i1","name":"site-b"}]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"routes":[{"id":"rt1","prefix":"10.0.0.0/8"}]}}"""))

        assertThat((repository.listGreTunnels("acct1") as ApiResult.Success).data.single().name).isEqualTo("site-a")
        assertThat((repository.listIpsecTunnels("acct1") as ApiResult.Success).data.single().name).isEqualTo("site-b")
        assertThat((repository.listRoutes("acct1") as ApiResult.Success).data.single().prefix).isEqualTo("10.0.0.0/8")
    }

    @Test
    fun `listSubscriptions parses the nested product and rate plan`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"s1","state":"Paid","price":20.0,"currency":"USD","frequency":"monthly","rate_plan":{"public_name":"Pro Plan"}}]}"""
            )
        )

        val result = BillingRepository(testApi(server)).listSubscriptions("acct1")

        val subscription = (result as ApiResult.Success).data.single()
        assertThat(subscription.ratePlan?.publicName).isEqualTo("Pro Plan")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/subscriptions")
    }

    @Test
    fun `screenshot returns the raw image bytes`() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(png))
        )

        val result = BrowserRenderingRepository(testApi(server)).screenshot("acct1", "https://example.com")

        assertThat((result as ApiResult.Success).data).isEqualTo(png)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/browser-rendering/screenshot")
        assertThat(request.body.readUtf8()).contains("\"url\":\"https://example.com\"")
    }

    @Test
    fun `screenshot surfaces Cloudflare's JSON error rather than the raw status line`() = runBlocking {
        // Success is binary, but failures still come back as JSON - the repository has to read
        // the body to tell which it got.
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Rate limit exceeded"}]}""")
        )

        val result = BrowserRenderingRepository(testApi(server)).screenshot("acct1", "https://example.com")

        assertThat((result as ApiResult.Failure).message).contains("Rate limit exceeded")
        assertThat(result.httpCode).isEqualTo(429)
    }

    @Test
    fun `screenshot rejects an empty body instead of reporting a blank success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = BrowserRenderingRepository(testApi(server)).screenshot("acct1", "https://example.com")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
    }
}
