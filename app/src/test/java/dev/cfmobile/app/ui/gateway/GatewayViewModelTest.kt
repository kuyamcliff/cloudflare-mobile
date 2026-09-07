package dev.cfmobile.app.ui.gateway

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.GatewayRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GatewayViewModelTest {

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

    private fun viewModel() = GatewayViewModel("acct1", GatewayRepository(testApi(server)))

    private suspend fun GatewayViewModel.awaitLoaded() = uiState.first { it.rules !is UiState.Loading }

    @Test
    fun `validateGatewayForm requires a name and a valid domain`() {
        assertThat(validateGatewayForm(GatewayFormState())).isEqualTo("Policy name is required")
        assertThat(validateGatewayForm(GatewayFormState(name = "Block malware"))).isEqualTo("Domain is required")
        assertThat(validateGatewayForm(GatewayFormState(name = "Block malware", domain = "not a domain"))).contains("valid hostname")
        assertThat(validateGatewayForm(GatewayFormState(name = "Block malware", domain = "malware.example.com"))).isNull()
    }

    @Test
    fun `buildGatewayTraffic wraps the domain in a dns fqdn match`() {
        val form = GatewayFormState(domain = "malware.example.com")
        assertThat(buildGatewayTraffic(form)).isEqualTo("dns.fqdn == \"malware.example.com\"")
    }

    @Test
    fun `loads rules on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"rule1","name":"Block malware","action":"block"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.rules as UiState.Data).value.map { it.name }).containsExactly("Block malware")
    }

    @Test
    fun `save rejects an invalid form before calling the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.openForm()
        vm.save()

        assertThat(vm.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `a valid save creates the rule and closes the form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openForm()
        vm.updateForm { it.copy(name = "Block malware", domain = "malware.example.com") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rule1","name":"Block malware","action":"block"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"rule1","name":"Block malware","action":"block"}]}"""))

        vm.save()
        val state = vm.uiState.first { it.form == null && (it.rules as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.rules as UiState.Data).value.map { it.name }).containsExactly("Block malware")
    }
}
