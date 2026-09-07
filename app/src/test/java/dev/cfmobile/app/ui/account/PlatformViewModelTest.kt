package dev.cfmobile.app.ui.account

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.AiGateway
import dev.cfmobile.app.data.remote.dto.CallsApp
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.AiGatewayRepository
import dev.cfmobile.app.data.repository.CallsRepository
import dev.cfmobile.app.data.repository.PipelinesRepository
import dev.cfmobile.app.data.repository.SecretsStoreRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The four account platform products behind one screen: AI Gateway, Calls, Pipelines,
 *  and the Secrets Store. */
class PlatformViewModelTest {

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

    private fun viewModel() = PlatformViewModel(
        "acct1",
        AiGatewayRepository(testApi(server)),
        CallsRepository(testApi(server)),
        PipelinesRepository(testApi(server)),
        SecretsStoreRepository(testApi(server))
    )

    private fun enqueueEmptyLists(count: Int = 4) =
        repeat(count) { server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}""")) }

    private suspend fun PlatformViewModel.awaitLoaded() =
        uiState.first { it.gateways !is UiState.Loading && it.stores !is UiState.Loading }

    // ---- Validation and formatting ----

    @Test
    fun `a gateway name has to be a usable URL segment`() {
        assertThat(validateGatewayForm(AiGatewayFormState(id = ""))).contains("required")
        assertThat(validateGatewayForm(AiGatewayFormState(id = "My Gateway"))).contains("lowercase")
        assertThat(validateGatewayForm(AiGatewayFormState(id = "my-gateway"))).isNull()
    }

    @Test
    fun `a rate limit without an interval would never reset`() {
        val form = AiGatewayFormState(id = "g", rateLimitLimit = "100", rateLimitInterval = "0")

        assertThat(validateGatewayForm(form)).contains("interval")
        assertThat(validateGatewayForm(form.copy(rateLimitInterval = "60"))).isNull()
    }

    @Test
    fun `gateway numbers have to be numbers`() {
        assertThat(validateGatewayForm(AiGatewayFormState(id = "g", cacheTtl = "soon"))).contains("Cache TTL")
        assertThat(validateGatewayForm(AiGatewayFormState(id = "g", cacheTtl = "-1"))).contains("Cache TTL")
        assertThat(validateGatewayForm(AiGatewayFormState(id = "g", rateLimitLimit = "lots"))).contains("Rate limit")
    }

    @Test
    fun `gatewaySummary says what the gateway actually does`() {
        val gateway = AiGateway(id = "g", cacheTtl = 60, rateLimitingLimit = 100, rateLimitingInterval = 60)

        assertThat(gatewaySummary(gateway)).isEqualTo("caches 60s · 100 req/60s · logging on")
        assertThat(gatewaySummary(AiGateway(id = "g", collectLogs = false)))
            .isEqualTo("no caching · logging off")
    }

    // ---- Loading ----

    @Test
    fun `each tab loads from its own product endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"g1"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"uid":"a1","name":"room"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"p1","name":"logs"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"s1","name":"prod"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.gateways as UiState.Data).value.single().id).isEqualTo("g1")
        assertThat((state.callsApps as UiState.Data).value.single().uid).isEqualTo("a1")
        assertThat((state.pipelines as UiState.Data).value.single().name).isEqualTo("logs")
        assertThat((state.stores as UiState.Data).value.single().name).isEqualTo("prod")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/ai-gateway/gateways")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/calls/apps")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/pipelines")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/secrets_store/stores")
    }

    @Test
    fun `a product the plan doesn't include leaves the other tabs usable`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"g1"}]}"""))
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Not entitled"}],"result":null}""")
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        val state = viewModel().awaitLoaded()

        assertThat(state.gateways).isInstanceOf(UiState.Data::class.java)
        assertThat(state.callsApps).isInstanceOf(UiState.Error::class.java)
        assertThat(state.stores).isInstanceOf(UiState.Data::class.java)
    }

    // ---- Creating ----

    @Test
    fun `creating a gateway posts the form's cache and rate limit settings`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"g1"}}"""))
        enqueueEmptyLists()

        vm.openForm()
        vm.updateGatewayForm { it.copy(id = "my-gateway", cacheTtl = "60", rateLimitLimit = "100", rateLimitInterval = "60") }
        vm.saveGateway()
        vm.uiState.first { it.gatewayForm == null }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val post = requests.single { it.method == "POST" }
        assertThat(post.path).isEqualTo("/accounts/acct1/ai-gateway/gateways")
        val body = post.body.readUtf8()
        assertThat(body).contains("\"id\":\"my-gateway\"")
        assertThat(body).contains("\"cache_ttl\":60")
        assertThat(body).contains("\"rate_limiting_limit\":100")
    }

    @Test
    fun `an invalid gateway form never reaches the network`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        val before = server.requestCount

        vm.openForm()
        vm.updateGatewayForm { it.copy(id = "Not A Slug") }
        vm.saveGateway()

        assertThat(server.requestCount).isEqualTo(before)
        assertThat(vm.uiState.value.gatewayForm?.error).contains("lowercase")
    }

    @Test
    fun `a new Calls app surfaces its secret exactly once`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"uid":"a1","name":"room","secret":"s3cret"}}""")
        )
        enqueueEmptyLists()

        vm.selectTab(PlatformTab.CALLS)
        vm.openForm()
        vm.updateNameForm { it.copy(name = "room") }
        vm.saveName()
        val state = vm.uiState.first { it.newCallsApp != null }

        assertThat(state.newCallsApp?.secret).isEqualTo("s3cret")
        assertThat(state.newCallsApp?.appId).isEqualTo("a1")
        // Dismissing is what drops it - nothing else holds a reference.
        vm.dismissNewCallsApp()
        assertThat(vm.uiState.value.newCallsApp).isNull()
    }

    @Test
    fun `a Calls app created without a secret shows no dialog rather than an empty one`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"uid":"a1","name":"room"}}"""))
        enqueueEmptyLists()

        vm.selectTab(PlatformTab.CALLS)
        vm.openForm()
        vm.updateNameForm { it.copy(name = "room") }
        vm.saveName()
        vm.uiState.first { it.nameForm == null }

        assertThat(vm.uiState.value.newCallsApp).isNull()
    }

    @Test
    fun `the create button does nothing on the pipelines tab`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()

        vm.selectTab(PlatformTab.PIPELINES)
        vm.openForm()

        // Creating a pipeline needs a source, a bucket, and its credentials - no form here.
        assertThat(vm.uiState.value.gatewayForm).isNull()
        assertThat(vm.uiState.value.nameForm).isNull()
    }

    // ---- Deleting ----

    @Test
    fun `deleting a pipeline addresses it by name, not id`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        enqueueEmptyLists()

        vm.deletePipeline(dev.cfmobile.app.data.remote.dto.Pipeline(id = "p1", name = "logs"))
        vm.uiState.first { it.deletingId == null && it.isRefreshing.not() }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "DELETE" }.path)
            .isEqualTo("/accounts/acct1/pipelines/logs")
    }

    @Test
    fun `a rejected delete reports the reason and clears the busy row`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"success":false,"errors":[{"code":1,"message":"Store is not empty"}],"result":null}""")
        )

        vm.deleteStore(dev.cfmobile.app.data.remote.dto.SecretStore(id = "s1", name = "prod"))
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("Store is not empty")
        assertThat(state.deletingId).isNull()
    }

    @Test
    fun `deleting a Calls app addresses it by uid`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"uid":"a1"}}"""))
        enqueueEmptyLists()

        vm.deleteCallsApp(CallsApp(uid = "a1", name = "room"))
        vm.uiState.first { it.deletingId == null && !it.isRefreshing }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "DELETE" }.path).isEqualTo("/accounts/acct1/calls/apps/a1")
    }
}
