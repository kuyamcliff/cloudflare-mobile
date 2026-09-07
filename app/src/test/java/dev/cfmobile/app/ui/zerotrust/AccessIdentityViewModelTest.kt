package dev.cfmobile.app.ui.zerotrust

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.AccessIdentityProvider
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.AccessRepository
import dev.cfmobile.app.ui.common.UiState
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

class AccessIdentityViewModelTest {

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

    /** Keyed by path: the screen loads providers and tokens in the same pass, and a queued
     *  dispatcher would tie the tests to the order they happen to fire in. */
    private fun serve(
        providers: String = """[]""",
        tokens: String = """[]""",
        createResponse: String = """{"success":true,"errors":[],"result":{}}"""
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "POST" || request.method == "DELETE" ->
                        MockResponse().setBody(createResponse)
                    path.endsWith("identity_providers") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":$providers}""")
                    else -> MockResponse().setBody("""{"success":true,"errors":[],"result":$tokens}""")
                }
            }
        }
    }

    private fun viewModel() = AccessIdentityViewModel("acct1", AccessRepository(testApi(server)))

    private suspend fun AccessIdentityViewModel.awaitLoaded() =
        uiState.first { it.providers !is UiState.Loading && it.tokens !is UiState.Loading }

    @Test
    fun `identityProviderLabel names the known types and falls back to the raw type`() {
        assertThat(identityProviderLabel(AccessIdentityProvider(type = "onetimepin"))).isEqualTo("One-time PIN")
        assertThat(identityProviderLabel(AccessIdentityProvider(type = "azureAD"))).isEqualTo("Microsoft Entra ID")
        // An unknown type is shown rather than hidden - the provider is real either way.
        assertThat(identityProviderLabel(AccessIdentityProvider(type = "some-new-idp"))).isEqualTo("some-new-idp")
        assertThat(identityProviderLabel(AccessIdentityProvider(type = ""))).isEqualTo("Unknown provider")
    }

    @Test
    fun `isOneTimePin only matches Cloudflare's configuration-free provider`() {
        assertThat(isOneTimePin(AccessIdentityProvider(type = "onetimepin"))).isTrue()
        assertThat(isOneTimePin(AccessIdentityProvider(type = "okta"))).isFalse()
    }

    @Test
    fun `loads providers and tokens on init`() = runTest {
        serve(
            providers = """[{"id":"p1","name":"PIN","type":"onetimepin"}]""",
            tokens = """[{"id":"t1","name":"ci","client_id":"abc.access"}]"""
        )

        val state = viewModel().awaitLoaded()

        assertThat((state.providers as UiState.Data).value.single().id).isEqualTo("p1")
        assertThat((state.tokens as UiState.Data).value.single().clientId).isEqualTo("abc.access")
    }

    @Test
    fun `a failure on one list still shows the other`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().endsWith("identity_providers")) {
                    MockResponse().setResponseCode(403)
                        .setBody("""{"success":false,"errors":[{"code":10000,"message":"Not entitled"}],"result":null}""")
                } else {
                    MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"t1","name":"ci"}]}""")
                }
        }

        val state = viewModel().awaitLoaded()

        assertThat(state.providers).isInstanceOf(UiState.Error::class.java)
        assertThat((state.tokens as UiState.Data).value).hasSize(1)
    }

    @Test
    fun `creating a provider sends the one-time PIN type`() = runTest {
        serve(createResponse = """{"success":true,"errors":[],"result":{"id":"p1","name":"PIN","type":"onetimepin"}}""")
        val vm = viewModel()
        vm.awaitLoaded()

        vm.selectTab(IdentityTab.PROVIDERS)
        vm.openForm()
        vm.updateForm { it.copy(name = "PIN") }
        vm.save()
        vm.uiState.first { it.form == null && !it.isRefreshing }

        val bodies = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val post = bodies.single { it.method == "POST" }
        assertThat(post.path).isEqualTo("/accounts/acct1/access/identity_providers")
        assertThat(post.body.readUtf8()).contains("\"type\":\"onetimepin\"")
    }

    @Test
    fun `creating a service token surfaces the secret exactly once`() = runTest {
        serve(createResponse = """{"success":true,"errors":[],"result":{"id":"t1","name":"ci","client_id":"abc.access","client_secret":"s3cret"}}""")
        val vm = viewModel()
        vm.awaitLoaded()

        vm.selectTab(IdentityTab.SERVICE_TOKENS)
        vm.openForm()
        vm.updateForm { it.copy(name = "ci") }
        vm.save()
        val state = vm.uiState.first { it.newToken != null }

        assertThat(state.newToken?.clientSecret).isEqualTo("s3cret")
        assertThat(state.newToken?.clientId).isEqualTo("abc.access")

        vm.uiState.first { !it.isRefreshing }
        vm.dismissNewToken()
        // Nothing holds the secret after the dialog closes.
        assertThat(vm.uiState.value.newToken).isNull()
    }

    @Test
    fun `a create response without a secret shows no dialog rather than an empty one`() = runTest {
        serve(createResponse = """{"success":true,"errors":[],"result":{"id":"t1","name":"ci","client_id":"abc.access"}}""")
        val vm = viewModel()
        vm.awaitLoaded()

        vm.selectTab(IdentityTab.SERVICE_TOKENS)
        vm.openForm()
        vm.updateForm { it.copy(name = "ci") }
        vm.save()
        vm.uiState.first { it.form == null && !it.isRefreshing }

        assertThat(vm.uiState.value.newToken).isNull()
    }

    @Test
    fun `a blank name never reaches the network`() = runTest {
        serve()
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.openForm()
        vm.save()

        assertThat(vm.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }
}
