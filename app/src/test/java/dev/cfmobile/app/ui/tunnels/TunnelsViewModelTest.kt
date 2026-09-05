package dev.cfmobile.app.ui.tunnels

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.TunnelsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TunnelsViewModelTest {

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

    private fun viewModel() = TunnelsViewModel("acct1", TunnelsRepository(testApi(server)))

    private suspend fun TunnelsViewModel.awaitLoaded() = uiState.first { it.tunnels !is UiState.Loading }

    @Test
    fun `validateTunnelName requires a non-blank name`() {
        assertThat(validateTunnelName("")).isEqualTo("Tunnel name is required")
        assertThat(validateTunnelName("my-tunnel")).isNull()
    }

    @Test
    fun `loads tunnels on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"tun1","name":"my-tunnel"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.tunnels as UiState.Data).value.map { it.name }).containsExactly("my-tunnel")
    }

    @Test
    fun `save rejects a blank name before calling the network`() = runTest {
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
    fun `a valid save creates the tunnel and closes the form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openForm()
        vm.updateForm { it.copy(name = "my-tunnel") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"tun1","name":"my-tunnel"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"tun1","name":"my-tunnel"}]}"""))

        vm.save()
        val state = vm.uiState.first { it.form == null && (it.tunnels as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.tunnels as UiState.Data).value.map { it.name }).containsExactly("my-tunnel")
    }

    @Test
    fun `delete removes the tunnel and refreshes the list`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"tun1","name":"my-tunnel"}]}"""))
        val vm = viewModel()
        val loaded = vm.awaitLoaded()
        val tunnel = (loaded.tunnels as UiState.Data).value.single()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"tun1","name":"my-tunnel","status":"deleted"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.delete(tunnel)
        val state = vm.uiState.first { it.deletingId == null && (it.tunnels as? UiState.Data)?.value?.isEmpty() == true }

        assertThat((state.tunnels as UiState.Data).value).isEmpty()
    }
}
