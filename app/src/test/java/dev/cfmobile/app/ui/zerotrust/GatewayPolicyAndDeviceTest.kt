package dev.cfmobile.app.ui.zerotrust

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.EnrolledDevice
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.DevicePostureRepository
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.deviceposture.DevicePostureViewModel
import dev.cfmobile.app.ui.gateway.GatewayFilter
import dev.cfmobile.app.ui.gateway.GatewayFormState
import dev.cfmobile.app.ui.gateway.buildGatewayTraffic
import dev.cfmobile.app.ui.gateway.validateGatewayForm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Gateway's per-engine expressions and the device revoke added alongside them. */
class GatewayPolicyAndDeviceTest {

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
    fun `each Gateway engine matches on its own field`() {
        val form = GatewayFormState(name = "p", domain = "example.com")

        assertThat(buildGatewayTraffic(form.copy(filter = GatewayFilter.DNS)))
            .isEqualTo("dns.fqdn == \"example.com\"")
        assertThat(buildGatewayTraffic(form.copy(filter = GatewayFilter.HTTP)))
            .isEqualTo("http.request.host == \"example.com\"")
        // An IP literal is not quoted in Wirefilter.
        assertThat(buildGatewayTraffic(form.copy(filter = GatewayFilter.NETWORK, domain = "203.0.113.10")))
            .isEqualTo("net.dst.ip == 203.0.113.10")
    }

    @Test
    fun `a network policy wants an IP and a DNS policy wants a hostname`() {
        val network = GatewayFormState(name = "p", filter = GatewayFilter.NETWORK)
        assertThat(validateGatewayForm(network.copy(domain = "example.com"))).contains("IPv4")
        assertThat(validateGatewayForm(network.copy(domain = "203.0.113.10"))).isNull()
        assertThat(validateGatewayForm(network.copy(domain = "999.0.0.1"))).contains("IPv4")

        val dns = GatewayFormState(name = "p", filter = GatewayFilter.DNS)
        assertThat(validateGatewayForm(dns.copy(domain = "203.0.113.10/24"))).contains("hostname")
        assertThat(validateGatewayForm(dns.copy(domain = "example.com"))).isNull()
    }

    @Test
    fun `an empty match value names the field the chosen engine asked for`() {
        assertThat(validateGatewayForm(GatewayFormState(name = "p", filter = GatewayFilter.NETWORK)))
            .isEqualTo("Destination IP is required")
        assertThat(validateGatewayForm(GatewayFormState(name = "p", filter = GatewayFilter.HTTP)))
            .isEqualTo("Host is required")
    }

    private fun serveDevices(vararg deviceIds: String) {
        val devices = deviceIds.joinToString(",") { """{"id":"$it","name":"laptop-$it"}""" }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "POST" -> MockResponse().setBody("""{"success":true,"errors":[],"result":{}}""")
                    path.endsWith("/devices") -> MockResponse().setBody("""{"success":true,"errors":[],"result":[$devices]}""")
                    else -> MockResponse().setBody("""{"success":true,"errors":[],"result":[]}""")
                }
            }
        }
    }

    private suspend fun DevicePostureViewModel.awaitLoaded() =
        uiState.first { it.devices !is UiState.Loading && it.postureRules !is UiState.Loading }

    @Test
    fun `revoking a device posts its id to the revoke endpoint`() = runTest {
        serveDevices("d1")
        val vm = DevicePostureViewModel("acct1", DevicePostureRepository(testApi(server)))
        vm.awaitLoaded()

        vm.revoke(EnrolledDevice(id = "d1"))
        vm.uiState.first { it.revokingId == null && !it.isRefreshing }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val revoke = requests.single { it.method == "POST" }
        assertThat(revoke.path).isEqualTo("/accounts/acct1/devices/revoke")
        assertThat(revoke.body.readUtf8()).isEqualTo("""["d1"]""")
    }

    @Test
    fun `a rejected revoke is reported and clears the busy flag`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "POST") {
                    MockResponse().setResponseCode(403)
                        .setBody("""{"success":false,"errors":[{"code":10000,"message":"Insufficient permissions"}],"result":null}""")
                } else {
                    MockResponse().setBody("""{"success":true,"errors":[],"result":[]}""")
                }
        }
        val vm = DevicePostureViewModel("acct1", DevicePostureRepository(testApi(server)))
        vm.awaitLoaded()

        vm.revoke(EnrolledDevice(id = "d1"))
        val state = vm.uiState.first { it.revokeError != null }

        assertThat(state.revokeError).contains("Insufficient permissions")
        assertThat(state.revokingId).isNull()

        vm.dismissRevokeError()
        assertThat(vm.uiState.value.revokeError).isNull()
    }
}
