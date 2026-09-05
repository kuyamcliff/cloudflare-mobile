package dev.cfmobile.app.ui.zones

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.local.AccountMetadataStore
import dev.cfmobile.app.data.local.AccountStore
import dev.cfmobile.app.data.local.CredentialStore
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.remote.testVerifierApi
import dev.cfmobile.app.data.repository.AuthRepository
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pinned to sdk=36 because Robolectric doesn't yet ship shadows for the app's targetSdk (37).
@Config(application = Application::class, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class ZonesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var accountStore: AccountStore
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val credentials = CredentialStore(context.getSharedPreferences("test_zones_credentials", android.content.Context.MODE_PRIVATE))
        val metadata = AccountMetadataStore(context.getSharedPreferences("test_zones_metadata", android.content.Context.MODE_PRIVATE))
        accountStore = AccountStore(credentials, metadata)
        authRepository = AuthRepository(testVerifierApi(server), accountStore)
    }

    @After
    fun tearDown() = server.shutdown()

    private val zonesJson = """
        {"success":true,"errors":[],"result":[
            {"id":"1","name":"example.com","status":"active"},
            {"id":"2","name":"test.dev","status":"pending"},
            {"id":"3","name":"another-example.net","status":"active"}
        ]}
    """.trimIndent()

    // The initial load happens via a real (if local) network round trip, so it completes
    // asynchronously relative to the test - waiting for the first non-Loading state is what
    // actually observes that, instead of racing ahead of it by reading .value immediately.
    private suspend fun ZonesViewModel.awaitLoaded(): UiState<List<dev.cfmobile.app.data.remote.dto.CfZone>> =
        state.first { it !is UiState.Loading }

    @Test
    fun `loads zones on init`() = runTest {
        server.enqueue(MockResponse().setBody(zonesJson))
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)), authRepository)

        val state = viewModel.awaitLoaded()

        assertThat(state).isInstanceOf(UiState.Data::class.java)
        assertThat((state as UiState.Data).value).hasSize(3)
    }

    @Test
    fun `records when zones were last successfully loaded`() = runTest {
        server.enqueue(MockResponse().setBody(zonesJson))
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)), authRepository)

        viewModel.awaitLoaded()

        assertThat(viewModel.lastUpdatedAt.value).isNotNull()
    }

    @Test
    fun `a failed load does not touch the last-updated timestamp`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Invalid API token"}],"result":null}""")
        )
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)), authRepository)

        viewModel.awaitLoaded()

        assertThat(viewModel.lastUpdatedAt.value).isNull()
    }

    @Test
    fun `query filters by domain name substring, case-insensitively`() = runTest {
        server.enqueue(MockResponse().setBody(zonesJson))
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)), authRepository)
        viewModel.awaitLoaded()

        viewModel.onQueryChange("EXAMPLE")

        val state = viewModel.state.value as UiState.Data
        assertThat(state.value.map { it.name }).containsExactly("example.com", "another-example.net")
    }

    @Test
    fun `failure surfaces the Cloudflare error message`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Invalid API token"}],"result":null}""")
        )
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)), authRepository)

        val state = viewModel.awaitLoaded()

        assertThat(state).isInstanceOf(UiState.Error::class.java)
        assertThat((state as UiState.Error).message).contains("Invalid API token")
    }

    @Test
    fun `switchAccount switches the active account, resets the query, and reloads zones`() = runTest {
        val accountA = accountStore.add(label = "Personal", token = "tokA")
        val accountB = accountStore.add(label = "Work", token = "tokB")
        accountStore.setActive(accountA.id)

        server.enqueue(MockResponse().setBody(zonesJson)) // initial load for account A
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)), authRepository)
        viewModel.awaitLoaded()
        viewModel.onQueryChange("example")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"9","name":"work-domain.com","status":"active"}]}"""))
        viewModel.switchAccount(accountB.id)
        val state = viewModel.state.first { it is UiState.Data && (it.value as List<*>).isNotEmpty() }

        assertThat(viewModel.activeAccountId).isEqualTo(accountB.id)
        assertThat(viewModel.query.value).isEmpty()
        assertThat((state as UiState.Data).value.map { it.name }).containsExactly("work-domain.com")
    }

    @Test
    fun `switchAccount to the already-active account is a no-op`() = runTest {
        val accountA = accountStore.add(label = "Personal", token = "tokA")

        server.enqueue(MockResponse().setBody(zonesJson))
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)), authRepository)
        viewModel.awaitLoaded()

        viewModel.switchAccount(accountA.id)

        // No second request was made - switching to the account that's already active
        // shouldn't trigger a redundant reload.
        assertThat(server.requestCount).isEqualTo(1)
    }
}
