package dev.cfmobile.app.ui.dashboard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.local.AccountMetadataStore
import dev.cfmobile.app.data.local.AccountStore
import dev.cfmobile.app.data.local.CredentialStore
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.remote.testVerifierApi
import dev.cfmobile.app.data.repository.AccountsRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.ZonesRepository
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
class DashboardViewModelTest {

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
        val credentials = CredentialStore(context.getSharedPreferences("test_dashboard_credentials", android.content.Context.MODE_PRIVATE))
        val metadata = AccountMetadataStore(context.getSharedPreferences("test_dashboard_metadata", android.content.Context.MODE_PRIVATE))
        accountStore = AccountStore(credentials, metadata)
        authRepository = AuthRepository(testVerifierApi(server), accountStore)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun newViewModel() = DashboardViewModel(
        ZonesRepository(testApi(server)),
        AccountsRepository(testApi(server)),
        authRepository
    )

    @Test
    fun `loads zone count and Cloudflare accounts together`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"1","name":"example.com","status":"active"},{"id":"2","name":"test.dev","status":"active"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"acct1","name":"My Account"}]}"""))
        val viewModel = newViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertThat(state.zoneCount).isEqualTo(2)
        assertThat(state.cfAccounts).hasSize(1)
        assertThat(state.cfAccounts.single().id).isEqualTo("acct1")
    }

    @Test
    fun `a failed accounts fetch still lets the zone count load`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = newViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertThat(state.zoneCount).isEqualTo(0)
        assertThat(state.cfAccounts).isEmpty()
    }

    @Test
    fun `a failed zones fetch surfaces a load error without crashing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"success":false,"errors":[{"code":1,"message":"Internal error"}],"result":null}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val viewModel = newViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertThat(state.zoneCount).isNull()
        assertThat(state.loadError).contains("Internal error")
    }

    @Test
    fun `switchAccount switches the active account and reloads`() = runTest {
        val accountA = accountStore.add(label = "Personal", token = "tokA")
        val accountB = accountStore.add(label = "Work", token = "tokB")
        accountStore.setActive(accountA.id)

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val viewModel = newViewModel()
        viewModel.uiState.first { !it.isLoading }

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"9","name":"work-domain.com","status":"active"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        viewModel.switchAccount(accountB.id)
        val state = viewModel.uiState.first { it.zoneCount == 1 }

        assertThat(viewModel.activeAccountId).isEqualTo(accountB.id)
        assertThat(state.zoneCount).isEqualTo(1)
    }

    @Test
    fun `switchAccount to the already-active account is a no-op`() = runTest {
        val accountA = accountStore.add(label = "Personal", token = "tokA")
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val viewModel = newViewModel()
        viewModel.uiState.first { !it.isLoading }
        val requestsBefore = server.requestCount

        viewModel.switchAccount(accountA.id)

        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }
}
