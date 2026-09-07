package dev.cfmobile.app.ui.dns

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.DnsFirewallCluster
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.DnsFirewallRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DnsFirewallViewModelTest {

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

    private fun viewModel() = DnsFirewallViewModel("acct1", DnsFirewallRepository(testApi(server)))

    private suspend fun DnsFirewallViewModel.awaitLoaded() =
        uiState.first { it.clusters !is UiState.Loading }

    @Test
    fun `upstreams are addresses, not hostnames`() {
        assertThat(looksLikeIpAddress("192.0.2.1")).isTrue()
        assertThat(looksLikeIpAddress("2001:db8::1")).isTrue()
        assertThat(looksLikeIpAddress("ns1.example.com")).isFalse()
        // 999 is not a byte, even though the shape matches.
        assertThat(looksLikeIpAddress("192.0.2.999")).isFalse()
    }

    @Test
    fun `upstreams can be separated by newlines or commas`() {
        assertThat(parseUpstreamIps("192.0.2.1\n192.0.2.2")).containsExactly("192.0.2.1", "192.0.2.2").inOrder()
        assertThat(parseUpstreamIps(" 192.0.2.1 , 192.0.2.2 ")).containsExactly("192.0.2.1", "192.0.2.2").inOrder()
        assertThat(parseUpstreamIps("\n\n")).isEmpty()
    }

    @Test
    fun `the form says which upstream is wrong`() {
        assertThat(validateDnsFirewallForm(DnsFirewallFormState(name = ""))).contains("name is required")
        assertThat(validateDnsFirewallForm(DnsFirewallFormState(name = "eu", upstreamIps = "")))
            .contains("At least one upstream")
        assertThat(validateDnsFirewallForm(DnsFirewallFormState(name = "eu", upstreamIps = "ns1.example.com")))
            .contains("ns1.example.com")
        assertThat(validateDnsFirewallForm(DnsFirewallFormState(name = "eu", upstreamIps = "192.0.2.1"))).isNull()
    }

    @Test
    fun `clusterSummary reads as the cluster's configuration`() {
        val cluster = DnsFirewallCluster(
            id = "c1",
            name = "eu",
            upstreamIps = listOf("192.0.2.1", "192.0.2.2"),
            minimumCacheTtl = 60,
            maximumCacheTtl = 900,
            rateLimit = 100
        )

        assertThat(clusterSummary(cluster)).isEqualTo("2 upstreams · cache 60-900s · 100 qps limit")
        assertThat(clusterSummary(DnsFirewallCluster(id = "c1", name = "eu"))).isEqualTo("0 upstreams")
    }

    @Test
    fun `clusters load from the account's dns_firewall collection`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"c1","name":"eu","dns_firewall_ips":["1.2.3.4"]}]}"""
            )
        )

        val state = viewModel().awaitLoaded()

        assertThat((state.clusters as UiState.Data).value.single().dnsFirewallIps).containsExactly("1.2.3.4")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/dns_firewall")
    }

    @Test
    fun `creating a cluster posts the parsed upstream list`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"c1","name":"eu"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"c1","name":"eu"}]}"""))

        vm.openForm()
        vm.updateForm { it.copy(name = "eu", upstreamIps = "192.0.2.1\n192.0.2.2") }
        vm.save()
        vm.uiState.first { it.form == null && (it.clusters as? UiState.Data)?.value?.isNotEmpty() == true }

        server.takeRequest()
        val post = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.body.readUtf8())
            .isEqualTo("""{"name":"eu","upstream_ips":["192.0.2.1","192.0.2.2"]}""")
    }

    @Test
    fun `an invalid form never reaches the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        val before = server.requestCount

        vm.openForm()
        vm.updateForm { it.copy(name = "eu", upstreamIps = "ns1.example.com") }
        vm.save()

        assertThat(server.requestCount).isEqualTo(before)
        assertThat(vm.uiState.value.form?.error).contains("ns1.example.com")
    }

    @Test
    fun `deleting a cluster addresses it by id and reloads`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"c1","name":"eu"}]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.delete(DnsFirewallCluster(id = "c1", name = "eu"))
        vm.uiState.first { it.deletingId == null && (it.clusters as? UiState.Data)?.value?.isEmpty() == true }

        server.takeRequest()
        val delete = server.takeRequest()
        assertThat(delete.method).isEqualTo("DELETE")
        assertThat(delete.path).isEqualTo("/accounts/acct1/dns_firewall/c1")
    }

    @Test
    fun `a rejected delete keeps the cluster listed and reports why`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"c1","name":"eu"}]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":1004,"message":"Cluster is in use"}],"result":null}""")
        )

        vm.delete(DnsFirewallCluster(id = "c1", name = "eu"))
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("Cluster is in use")
        assertThat((state.clusters as UiState.Data).value).hasSize(1)
    }
}
