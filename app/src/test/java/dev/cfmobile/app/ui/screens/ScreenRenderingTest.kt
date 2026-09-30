package dev.cfmobile.app.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.capabilities.CapabilityRepository
import dev.cfmobile.app.core.capabilities.DestructiveRisk
import dev.cfmobile.app.core.net.ConnectionState
import dev.cfmobile.app.data.local.WorkingContext
import dev.cfmobile.app.data.local.db.SavedRequestDao
import dev.cfmobile.app.data.local.db.SavedRequestEntity
import dev.cfmobile.app.data.remote.AuthInterceptor
import dev.cfmobile.app.data.remote.CloudflareHosts
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.ui.components.DestructiveConfirmDialog
import dev.cfmobile.app.ui.components.MutationContext
import dev.cfmobile.app.ui.explorer.ApiCatalogScreen
import dev.cfmobile.app.ui.explorer.ApiCatalogViewModel
import dev.cfmobile.app.ui.explorer.ApiExplorerScreen
import dev.cfmobile.app.ui.explorer.ApiExplorerViewModel
import dev.cfmobile.app.ui.theme.CfMobileTheme
import dev.cfmobile.app.ui.tokens.TokenSecretPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders real screens with real view models. Network calls go to a MockWebServer through
 * the app's own interceptors, so these exercise the same code paths a device would.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class ScreenRenderingTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer

    private val registry: EndpointRegistry by lazy {
        val asset = listOf("src/main/assets", "app/src/main/assets").map { File(it, EndpointRegistry.ASSET_NAME) }.first { it.exists() }
        asset.inputStream().use { EndpointRegistry.load(it) }
    }

    private val noSavedRequests = object : SavedRequestDao {
        override suspend fun insert(entity: SavedRequestEntity) = 1L
        override fun observe(profileId: String, kind: String) = flowOf(emptyList<SavedRequestEntity>())
        override suspend fun delete(id: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    /** Advances Robolectric's main-looper clock (debounces, delays) while real OkHttp threads
     *  do their work, until [condition] holds. */
    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("Condition not met within $timeoutMs ms")
    }

    private fun scrollTo(text: String) {
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    private fun exists(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() = server.shutdown()

    private fun capabilities() = CapabilityRepository(
        api = testApi(server),
        registryProvider = { registry },
        cache = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("caps-test", 0),
        scope = CoroutineScope(Dispatchers.Unconfined),
        activeProfileId = { null }
    )

    @Test fun `explorer sends a GET through the app stack and shows the response`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"success":true,"result":[{"id":"z1","name":"example.com"}]}"""))
        val hosts = CloudflareHosts(server.url("/client/v4/").toString())
        val raw = RawApiClient(OkHttpClient.Builder().addInterceptor(AuthInterceptor({ "tok" }, hosts)).build(), hosts)
        val vm = ApiExplorerViewModel(
            { registry }, raw, capabilities(), noSavedRequests, { WorkingContext() }, { "p1" },
            initialEndpointId = null, initialMethod = "GET", initialPath = "zones", initialQuery = "per_page=5"
        )
        compose.setContent {
            CfMobileTheme(darkTheme = false) {
                ApiExplorerScreen(
                    vm, "Personal", "Acme", MutableStateFlow(ConnectionState(true, false, true)), flowOf(emptyList()), {}, {}, {}, {}
                )
            }
        }
        waitFor { vm.uiState.value.pathTemplate == "zones" }
        scrollTo("Send GET")
        compose.onNodeWithText("Send GET").performClick()
        waitFor { vm.uiState.value.result != null }
        scrollTo("HTTP 200")
        waitFor { exists("HTTP 200") }
        assertThat(vm.uiState.value.result!!.lines.joinToString("\n")).contains("\"example.com\"")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/client/v4/zones?per_page=5")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer tok")
    }

    @Test fun `explorer confirms mutations before sending`() {
        val hosts = CloudflareHosts(server.url("/client/v4/").toString())
        val raw = RawApiClient(OkHttpClient(), hosts)
        val vm = ApiExplorerViewModel({ registry }, raw, capabilities(), noSavedRequests, { WorkingContext() }, { "p1" }, null, "DELETE", "zones/abc/dns_records/def", null)
        compose.setContent {
            CfMobileTheme { ApiExplorerScreen(vm, "Personal", "Acme", MutableStateFlow(ConnectionState(true, false, true)), flowOf(emptyList()), {}, {}, {}, {}) }
        }
        waitFor { vm.uiState.value.method == "DELETE" }
        scrollTo("Send DELETE")
        compose.onAllNodesWithText("Send DELETE").onFirst().performClick()
        compose.onNodeWithText("This deletes the resource in Cloudflare. It may not be recoverable.").assertExists()
        compose.onNodeWithText("Profile: Personal").assertExists()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun `catalog lists schema operations and filters by search`() {
        val vm = ApiCatalogViewModel({ registry }, capabilities())
        compose.setContent { CfMobileTheme { ApiCatalogScreen(vm, onBack = {}, onOpen = {}) } }
        waitFor { vm.uiState.value.sections.isNotEmpty() }
        assertThat(vm.uiState.value.total).isGreaterThan(3000)
        assertThat(exists("of ${vm.uiState.value.total} operations", substring = true)).isTrue()
        compose.onNodeWithText("Search path, operation or permission").performTextInput("r2 buckets")
        waitFor { vm.uiState.value.query == "r2 buckets" && vm.uiState.value.shown in 1..300 && vm.uiState.value.expanded.isNotEmpty() }
        waitFor { exists("accounts/{account_id}/r2/buckets", substring = true) }
    }

    @Test fun `critical actions require typing the resource name`() {
        var confirmed = false
        compose.setContent {
            CfMobileTheme {
                DestructiveConfirmDialog(
                    title = "Delete example.com?", consequence = "Deletes the zone.", confirmLabel = "Delete zone",
                    risk = DestructiveRisk.CRITICAL, context = MutationContext(profile = "Company", zone = "example.com"),
                    typedConfirmation = "example.com", onConfirm = { confirmed = true }, onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Delete zone").assertIsNotEnabled()
        compose.onNodeWithText("Zone: example.com").assertExists()
        compose.onNode(hasText("") and androidx.compose.ui.test.hasSetTextAction()).performTextInput("example.com")
        compose.onNodeWithText("Delete zone").assertIsEnabled().performClick()
        assertThat(confirmed).isTrue()
    }

    @Test fun `new token secret is masked until revealed`() {
        val secret = "abcd1234SECRETVALUE9876wxyz"
        compose.setContent { CfMobileTheme { TokenSecretPanel("New token", secret, savedAsProfile = false, savingProfile = false, onSaveAsProfile = {}, onDone = {}) } }
        compose.onAllNodesWithText(secret).fetchSemanticsNodes().let { assertThat(it).isEmpty() }
        compose.onNodeWithText("Reveal").performClick()
        compose.onNodeWithText(secret).assertExists()
        compose.onNodeWithText("This secret is shown only now", substring = true).assertExists()
    }
}
