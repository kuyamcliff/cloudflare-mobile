package dev.cfmobile.app.ui.loadbalancing

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.LoadBalancingRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoadBalancingViewModelTest {

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

    private fun viewModel(): LoadBalancingViewModel =
        LoadBalancingViewModel("acct1", LoadBalancingRepository(testApi(server)), ZonesRepository(testApi(server)))

    private suspend fun LoadBalancingViewModel.awaitPoolsLoaded() = uiState.first { it.pools !is UiState.Loading }
    private suspend fun LoadBalancingViewModel.awaitLbLoaded() = uiState.first { it.loadBalancers !is UiState.Loading }

    @Test
    fun `loads pools and auto-selects the first zone for load balancers`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"pool1","name":"primary","origins":[]}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"zone1","name":"example.com","status":"active"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        val vm = viewModel()
        val poolsState = vm.awaitPoolsLoaded()
        val lbState = vm.awaitLbLoaded()

        assertThat((poolsState.pools as UiState.Data).value).hasSize(1)
        assertThat(vm.uiState.value.selectedZoneId).isEqualTo("zone1")
        assertThat((lbState.loadBalancers as UiState.Data).value).isEmpty()
    }

    @Test
    fun `switching zones reloads load balancers for the newly selected zone`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"zone1","name":"a.com","status":"active"},{"id":"zone2","name":"b.com","status":"active"}]}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}""")) // zone1 LBs
        val vm = viewModel()
        vm.awaitLbLoaded()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"lb1","name":"b.example.com"}]}"""))
        vm.selectZone("zone2")
        val state = vm.uiState.first { (it.loadBalancers as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat(vm.uiState.value.selectedZoneId).isEqualTo("zone2")
        assertThat((state.loadBalancers as UiState.Data).value.map { it.name }).containsExactly("b.example.com")
    }

    @Test
    fun `savePool rejects an invalid form before calling the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        // Both init-triggered requests (pools, zones-with-no-zones) need to have actually
        // completed before recording a baseline - awaiting pools alone races the still-in-
        // flight zones call, since the two are launched independently in init.
        vm.awaitPoolsLoaded()
        vm.awaitLbLoaded()
        val requestsBefore = server.requestCount

        vm.openPoolForm()
        vm.savePool()

        assertThat(vm.uiState.value.poolForm?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `savePool creates the pool and closes the form on success`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        // Both init-triggered requests (pools, zones-with-no-zones) need to have actually
        // completed before triggering savePool's own network call - awaiting pools alone
        // races the still-in-flight zones call, since real HTTP I/O for the two happens on
        // OkHttp's own dispatcher threads outside the test scheduler's control.
        vm.awaitPoolsLoaded()
        vm.awaitLbLoaded()

        vm.openPoolForm()
        vm.updatePoolForm { it.copy(name = "primary") }
        vm.updateOrigin(0) { it.copy(address = "203.0.113.10") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"pool1","name":"primary","origins":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"pool1","name":"primary","origins":[]}]}"""))

        vm.savePool()
        val state = vm.uiState.first { it.poolForm == null && (it.pools as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.pools as UiState.Data).value.map { it.name }).containsExactly("primary")
    }

    @Test
    fun `openLbForm preselects the first available pool`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"pool1","name":"primary","origins":[]}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        // Await the full init chain (not just pools) so the single init coroutine has
        // actually completed before the test ends - otherwise it can still be suspended on
        // the in-flight zones call when runTest checks for uncompleted coroutines.
        vm.awaitPoolsLoaded()
        vm.awaitLbLoaded()

        vm.openLbForm()

        assertThat(vm.uiState.value.lbForm?.poolId).isEqualTo("pool1")
    }
}
