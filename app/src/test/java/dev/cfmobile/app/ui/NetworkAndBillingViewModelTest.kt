package dev.cfmobile.app.ui

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.AccountSubscription
import dev.cfmobile.app.data.remote.dto.EmailRoutingAction
import dev.cfmobile.app.data.remote.dto.EmailRoutingMatcher
import dev.cfmobile.app.data.remote.dto.EmailRoutingRule
import dev.cfmobile.app.data.remote.dto.SpectrumApp
import dev.cfmobile.app.data.remote.dto.SpectrumDns
import dev.cfmobile.app.data.remote.dto.SubscriptionProduct
import dev.cfmobile.app.data.remote.dto.SubscriptionRatePlan
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.BrowserRenderingRepository
import dev.cfmobile.app.data.repository.EmailRoutingRepository
import dev.cfmobile.app.data.repository.MagicNetworkRepository
import dev.cfmobile.app.ui.billing.subscriptionPriceLabel
import dev.cfmobile.app.ui.billing.subscriptionTitle
import dev.cfmobile.app.ui.browserrendering.BrowserRenderingViewModel
import dev.cfmobile.app.ui.browserrendering.validateScreenshotUrl
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.emailrouting.EmailRoutingFormState
import dev.cfmobile.app.ui.emailrouting.EmailRoutingViewModel
import dev.cfmobile.app.ui.emailrouting.ruleRouteLabel
import dev.cfmobile.app.ui.emailrouting.validateEmailRoutingForm
import dev.cfmobile.app.ui.magicnetwork.MagicNetworkViewModel
import dev.cfmobile.app.ui.magicnetwork.tunnelEndpointsLabel
import dev.cfmobile.app.ui.spectrum.spectrumAppLabel
import dev.cfmobile.app.ui.spectrum.spectrumRouteLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NetworkAndBillingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `validateEmailRoutingForm checks both addresses`() {
        assertThat(validateEmailRoutingForm(EmailRoutingFormState())).isEqualTo("Rule name is required")
        assertThat(validateEmailRoutingForm(EmailRoutingFormState(name = "Support"))).contains("Custom address")
        assertThat(validateEmailRoutingForm(EmailRoutingFormState(name = "Support", fromAddress = "nope")))
            .contains("valid custom address")
        assertThat(validateEmailRoutingForm(EmailRoutingFormState(name = "Support", fromAddress = "a@example.com")))
            .contains("Destination address")
        assertThat(
            validateEmailRoutingForm(
                EmailRoutingFormState(name = "Support", fromAddress = "a@example.com", toAddress = "me@gmail.com")
            )
        ).isNull()
    }

    @Test
    fun `ruleRouteLabel reads as from arrow to, and copes with either half missing`() {
        val full = EmailRoutingRule(
            tag = "r1",
            matchers = listOf(EmailRoutingMatcher(value = "hello@example.com")),
            actions = listOf(EmailRoutingAction(value = listOf("me@gmail.com")))
        )
        assertThat(ruleRouteLabel(full)).isEqualTo("hello@example.com → me@gmail.com")
        assertThat(ruleRouteLabel(EmailRoutingRule(tag = "r2"))).isNull()
    }

    @Test
    fun `spectrum labels fall back when fields are missing`() {
        val app = SpectrumApp(
            id = "a1",
            protocol = "tcp/22",
            dns = SpectrumDns(name = "ssh.example.com"),
            originDirect = listOf("tcp://203.0.113.10:22")
        )
        assertThat(spectrumAppLabel(app)).isEqualTo("ssh.example.com")
        assertThat(spectrumRouteLabel(app)).isEqualTo("tcp/22 → tcp://203.0.113.10:22")
        assertThat(spectrumAppLabel(SpectrumApp(id = "a2"))).isEqualTo("a2")
        assertThat(spectrumRouteLabel(SpectrumApp(id = "a2"))).isNull()
    }

    @Test
    fun `tunnelEndpointsLabel handles a one-sided tunnel`() {
        assertThat(tunnelEndpointsLabel("203.0.113.1", "198.51.100.1")).isEqualTo("203.0.113.1 → 198.51.100.1")
        assertThat(tunnelEndpointsLabel("203.0.113.1", null)).isEqualTo("203.0.113.1")
        assertThat(tunnelEndpointsLabel(null, null)).isNull()
    }

    @Test
    fun `subscription labels prefer the plan name and omit an absent price`() {
        val paid = AccountSubscription(
            id = "s1",
            price = 20.0,
            currency = "USD",
            frequency = "monthly",
            ratePlan = SubscriptionRatePlan(publicName = "Pro Plan")
        )
        assertThat(subscriptionTitle(paid)).isEqualTo("Pro Plan")
        assertThat(subscriptionPriceLabel(paid)).isEqualTo("20.00 USD / monthly")

        val productOnly = AccountSubscription(id = "s2", product = SubscriptionProduct(name = "Workers"))
        assertThat(subscriptionTitle(productOnly)).isEqualTo("Workers")
        // No price returned must not render as a misleading "0".
        assertThat(subscriptionPriceLabel(productOnly)).isNull()
    }

    @Test
    fun `validateScreenshotUrl insists on an absolute http url`() {
        assertThat(validateScreenshotUrl("")).contains("Enter a URL")
        assertThat(validateScreenshotUrl("example.com")).contains("scheme")
        assertThat(validateScreenshotUrl("https://example.com")).isNull()
        assertThat(validateScreenshotUrl("  http://example.com  ")).isNull()
    }

    @Test
    fun `email routing loads rules and keeps them visible when settings fail`() = runTest {
        // Settings are supplementary context; a failure there must not hide the rules.
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"success":false,"errors":[],"result":null}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"tag":"r1","name":"Support"}]}"""))

        val vm = EmailRoutingViewModel("zone1", EmailRoutingRepository(testApi(server)))
        val state = vm.uiState.first { it.rules !is UiState.Loading }

        assertThat((state.rules as UiState.Data).value).hasSize(1)
        assertThat(state.isEnabled).isNull()
    }

    @Test
    fun `magic network loads all three lists in one pass`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"gre_tunnels":[{"id":"g1","name":"a"}]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"ipsec_tunnels":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"routes":[{"id":"r1","prefix":"10.0.0.0/8"}]}}"""))

        val vm = MagicNetworkViewModel("acct1", MagicNetworkRepository(testApi(server)))
        val state = vm.uiState.first {
            it.greTunnels !is UiState.Loading && it.ipsecTunnels !is UiState.Loading && it.routes !is UiState.Loading
        }

        assertThat((state.greTunnels as UiState.Data).value).hasSize(1)
        assertThat((state.ipsecTunnels as UiState.Data).value).isEmpty()
        assertThat((state.routes as UiState.Data).value).hasSize(1)
        assertThat(state.isRefreshing).isFalse()
    }

    @Test
    fun `render rejects a schemeless url without calling the network`() = runTest {
        val vm = BrowserRenderingViewModel("acct1", BrowserRenderingRepository(testApi(server)))

        vm.updateUrl("example.com")
        vm.render()

        assertThat(vm.uiState.value.error).contains("scheme")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a successful render exposes the bytes and the url they belong to`() = runTest {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(png)))
        val vm = BrowserRenderingViewModel("acct1", BrowserRenderingRepository(testApi(server)))

        vm.updateUrl("https://example.com")
        vm.render()
        val state = vm.uiState.first { !it.isRendering && it.screenshot != null }

        assertThat(state.screenshot).isEqualTo(png)
        assertThat(state.renderedUrl).isEqualTo("https://example.com")
        assertThat(state.error).isNull()
    }
}
