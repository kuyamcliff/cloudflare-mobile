package dev.cfmobile.app.ui.web3

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.Web3Hostname
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.Web3Repository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Web3ViewModelTest {

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

    private fun viewModel() = Web3ViewModel("zone1", Web3Repository(testApi(server)))

    private suspend fun Web3ViewModel.awaitLoaded() = uiState.first { it.hostnames !is UiState.Loading }

    @Test
    fun `an unknown gateway target falls back rather than crashing`() {
        assertThat(web3TargetFromValue("ethereum")).isEqualTo(Web3Target.ETHEREUM)
        assertThat(web3TargetFromValue("ipfs_universal_path")).isEqualTo(Web3Target.IPFS_UNIVERSAL)
        assertThat(web3TargetFromValue("solana")).isEqualTo(Web3Target.IPFS)
    }

    @Test
    fun `the form checks the hostname and the DNSLink shape`() {
        val valid = Web3FormState(name = "web3.example.com")

        assertThat(validateWeb3Form(valid)).isNull()
        assertThat(validateWeb3Form(valid.copy(name = ""))).contains("hostname is required")
        assertThat(validateWeb3Form(valid.copy(name = "not a hostname"))).contains("hostname")
        assertThat(validateWeb3Form(valid.copy(dnslink = "bafy123"))).contains("/ipfs/")
        assertThat(validateWeb3Form(valid.copy(dnslink = "/ipfs/bafy123"))).isNull()
        // Only the DNSLink flavour reads that field, so it isn't checked for the others.
        assertThat(validateWeb3Form(valid.copy(target = Web3Target.ETHEREUM, dnslink = "nonsense"))).isNull()
    }

    @Test
    fun `web3Summary names the gateway and its status`() {
        assertThat(web3Summary(Web3Hostname(id = "h1", name = "a.com", target = "ethereum", status = "active")))
            .isEqualTo("Ethereum · active")
    }

    @Test
    fun `hostnames load from the zone's web3 collection`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"h1","name":"web3.example.com","target":"ipfs"}]}""")
        )

        val state = viewModel().awaitLoaded()

        assertThat((state.hostnames as UiState.Data).value.single().name).isEqualTo("web3.example.com")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/web3/hostnames")
    }

    @Test
    fun `a DNSLink is only sent for the gateway that uses one`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"h1"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.openForm()
        vm.updateForm {
            it.copy(name = "web3.example.com", target = Web3Target.ETHEREUM, dnslink = "/ipfs/bafy123")
        }
        vm.save()
        vm.uiState.first { it.form == null }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val body = requests.single { it.method == "POST" }.body.readUtf8()
        assertThat(body).contains("\"target\":\"ethereum\"")
        // Moshi drops the null, so the field is simply absent.
        assertThat(body).doesNotContain("dnslink")
    }

    @Test
    fun `an IPFS gateway does send its DNSLink`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"h1"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.openForm()
        vm.updateForm { it.copy(name = "web3.example.com", dnslink = "/ipfs/bafy123") }
        vm.save()
        vm.uiState.first { it.form == null }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "POST" }.body.readUtf8())
            .contains("\"dnslink\":\"/ipfs/bafy123\"")
    }

    @Test
    fun `deleting a hostname addresses it by id`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"h1","name":"web3.example.com"}]}""")
        )
        val vm = viewModel()
        val hostname = (vm.awaitLoaded().hostnames as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.delete(hostname)
        vm.uiState.first { it.deletingId == null && (it.hostnames as? UiState.Data)?.value?.isEmpty() == true }

        server.takeRequest()
        val delete = server.takeRequest()
        assertThat(delete.method).isEqualTo("DELETE")
        assertThat(delete.path).isEqualTo("/zones/zone1/web3/hostnames/h1")
    }
}
