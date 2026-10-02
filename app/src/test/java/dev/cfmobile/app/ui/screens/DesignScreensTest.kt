package dev.cfmobile.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.capabilities.CapabilityRepository
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.core.command.ActionDraft
import dev.cfmobile.app.core.command.ActionStore
import dev.cfmobile.app.core.command.AiPlanner
import dev.cfmobile.app.core.command.CommandEngine
import dev.cfmobile.app.core.command.SavedAction
import dev.cfmobile.app.core.net.ConnectionState
import dev.cfmobile.app.data.local.AccountMetadataStore
import dev.cfmobile.app.data.local.AccountStore
import dev.cfmobile.app.data.local.ContextStore
import dev.cfmobile.app.data.local.CredentialStore
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.data.local.db.CfDatabase
import dev.cfmobile.app.data.remote.AuthInterceptor
import dev.cfmobile.app.data.remote.CloudflareHosts
import dev.cfmobile.app.data.remote.NetworkStatus
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.remote.testVerifierApi
import dev.cfmobile.app.data.repository.AccountsRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.DnsRepository
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.action.ActionScreen
import dev.cfmobile.app.ui.action.ActionViewModel
import dev.cfmobile.app.ui.activity.ActivityContent
import dev.cfmobile.app.ui.activity.ActivityViewModel
import dev.cfmobile.app.ui.command.CommandScreen
import dev.cfmobile.app.ui.command.CommandViewModel
import dev.cfmobile.app.ui.dns.DnsScreen
import dev.cfmobile.app.ui.dns.DnsViewModel
import dev.cfmobile.app.ui.home.HomeContent
import dev.cfmobile.app.ui.home.HomeViewModel
import dev.cfmobile.app.ui.home.ResourcesContent
import dev.cfmobile.app.ui.home.ResourcesViewModel
import dev.cfmobile.app.ui.login.LoginScreen
import dev.cfmobile.app.ui.login.LoginViewModel
import dev.cfmobile.app.ui.shell.MainShell
import dev.cfmobile.app.ui.shell.ProfileScreen
import dev.cfmobile.app.ui.theme.CfMobileTheme
import dev.cfmobile.app.ui.zonedetail.ZoneMenuScreen
import dev.cfmobile.app.ui.zonedetail.ZoneMenuViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
 * Renders the redesigned top-level screens with real view models against a fake Cloudflare,
 * checks what each must show, and, when CF_SCREENSHOTS_DIR is set, writes a PNG of each in
 * light and dark so the design can be reviewed without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h852dp-xhdpi")
class DesignScreensTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private val registry: EndpointRegistry by lazy {
        val asset = listOf("src/main/assets", "app/src/main/assets").map { File(it, EndpointRegistry.ASSET_NAME) }.first { it.exists() }
        asset.inputStream().use { EndpointRegistry.load(it) }
    }

    private val zones = listOf(
        Triple("z1", "acme.dev", "Pro"), Triple("z2", "shop.acme.dev", "Business"),
        Triple("z3", "status-page.io", "Free"), Triple("z4", "legacy-site.net", "Free")
    )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private val fake = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty().substringBefore('?').removePrefix("/client/v4").removePrefix("/")
            return when {
                path == "accounts" -> json("""{"success":true,"result":[{"id":"acc1","name":"Acme Corp"},{"id":"acc2","name":"Side Projects"}],"result_info":{"page":1,"total_pages":1}}""")
                path == "zones" -> json(
                    """{"success":true,"result":[""" + zones.joinToString(",") { (id, name, plan) ->
                        """{"id":"$id","name":"$name","status":"${if (id == "z4") "pending" else "active"}","paused":false,"name_servers":["ada.ns.cloudflare.com","bob.ns.cloudflare.com"],"plan":{"id":"p","name":"$plan Website"},"account":{"id":"acc1","name":"Acme Corp"}}"""
                    } + """],"result_info":{"page":1,"total_pages":1}}"""
                )
                path.matches(Regex("zones/z\\d")) -> {
                    val (id, name, plan) = zones.first { it.first == path.removePrefix("zones/") }
                    json("""{"success":true,"result":{"id":"$id","name":"$name","status":"active","paused":false,"name_servers":["ada.ns.cloudflare.com","bob.ns.cloudflare.com"],"plan":{"id":"p","name":"$plan Website"},"account":{"id":"acc1","name":"Acme Corp"}}}""")
                }
                path.endsWith("settings/development_mode") -> json("""{"success":true,"result":{"id":"development_mode","value":"off","editable":true}}""")
                path.endsWith("settings/security_level") -> json("""{"success":true,"result":{"id":"security_level","value":"medium","editable":true}}""")
                path.endsWith("settings/always_use_https") -> json("""{"success":true,"result":{"id":"always_use_https","value":"on","editable":true}}""")
                path.endsWith("dns_records") -> json(
                    """{"success":true,"result":[
                    {"id":"r1","type":"A","name":"acme.dev","content":"192.0.2.10","proxied":true,"ttl":1},
                    {"id":"r2","type":"CNAME","name":"www.acme.dev","content":"acme.dev","proxied":true,"ttl":1},
                    {"id":"r3","type":"MX","name":"acme.dev","content":"mx.mail.example","priority":10,"proxied":false,"ttl":3600},
                    {"id":"r4","type":"TXT","name":"_dmarc.acme.dev","content":"v=DMARC1; p=none","proxied":false,"ttl":3600}
                    ],"result_info":{"page":1,"total_pages":1,"total_count":4}}"""
                )
                else -> json("""{"success":true,"result":[]}""")
            }
        }
    }

    private lateinit var accountStore: AccountStore
    private lateinit var contextStore: ContextStore

    @Before fun setUp() {
        server = MockWebServer().apply { dispatcher = fake; start() }
        val credentials = CredentialStore(context.getSharedPreferences("design_creds", 0))
        val metadata = AccountMetadataStore(context.getSharedPreferences("design_meta", 0))
        accountStore = AccountStore(credentials, metadata)
        val p = accountStore.add("Personal", "test-token", tokenId = "tok1")
        accountStore.setActive(p.id)
        contextStore = ContextStore(context.getSharedPreferences("design_ctx", 0)).apply {
            load(p.id)
            selectAccount(NamedRef("acc1", "Acme Corp"))
            selectZone(NamedRef("z1", "acme.dev", "acc1"))
        }
    }

    /** View models made here are never attached to a store, so their scopes are cancelled by
     *  hand; a collector left running would touch Dispatchers.Main during the next test. */
    private val viewModels = ArrayList<androidx.lifecycle.ViewModel>()
    private fun <T : androidx.lifecycle.ViewModel> track(vm: T): T = vm.also { viewModels += it }

    @After fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        server.shutdown()
    }

    private fun api() = testApi(server)
    private fun raw(): RawApiClient {
        val hosts = CloudflareHosts(server.url("/client/v4/").toString())
        return RawApiClient(OkHttpClient.Builder().addInterceptor(AuthInterceptor({ "tok" }, hosts)).build(), hosts)
    }

    private fun capabilities() = CapabilityRepository(
        api = api(), registryProvider = { registry },
        cache = context.getSharedPreferences("design-caps", 0),
        scope = CoroutineScope(Dispatchers.Unconfined), activeProfileId = { null }
    )

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

    private fun exists(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private val dark = MutableStateFlow(false)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            val d = dark.collectAsStateValue()
            CfMobileTheme(darkTheme = d) { androidx.compose.material3.Surface { content() } }
        }
    }

    @Composable
    private fun MutableStateFlow<Boolean>.collectAsStateValue(): Boolean = collectAsState().value

    /** Saves light and dark captures of the current content. */
    private fun capture(name: String) {
        val dir = System.getenv("CF_SCREENSHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        for (d in listOf(false, true)) {
            dark.value = d
            waitFor { true }
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "$name-${if (d) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        dark.value = false
        waitFor { true }
    }

    private fun shell(tab: @Composable () -> Unit = {}) {
        val home = track(HomeViewModel(AuthRepository(testVerifierApi(server), accountStore), AccountsRepository(api()), ZonesRepository(api()), contextStore, capabilities()))
        val pins = listOf(
            SavedAction("POST", "zones/{zone_id}/purge_cache", "Purge everything", mapOf("zone_id" to "z1"), context = "acme.dev"),
            SavedAction("GET", "accounts/{account_id}/workers/scripts", "Workers", mapOf("account_id" to "acc1"))
        )
        val recents = listOf(SavedAction("PATCH", "zones/{zone_id}/settings/{setting_id}", "Development mode", mapOf("zone_id" to "z1", "setting_id" to "development_mode"), context = "acme.dev"))
        show {
            MainShell(
                profile = accountStore.getActive(), accountName = "Acme Corp", networkStatus = NetworkStatus(),
                connection = MutableStateFlow(ConnectionState(true, false, true)),
                onSwitchAccount = {}, onOpenProfile = {}, onOpenCommand = {}, onReconnect = {},
                home = { HomeContent(home, pins, recents, onNavigate = {}, onOpenSaved = {}) },
                browse = { ResourcesContent(remember { track(ResourcesViewModel(contextStore, capabilities())) }) {} },
                activity = { tab() }
            )
        }
    }

    @Test fun `login explains the token and connects`() {
        val login = track(LoginViewModel(AuthRepository(testVerifierApi(server), accountStore)))
        show { LoginScreen(login, onLoggedIn = {}) }
        assertThat(exists("Run Cloudflare from your phone")).isTrue()
        assertThat(exists("Connect")).isTrue()
        capture("01-login")
    }

    @Test fun `home lists zones pins and the command bar`() {
        shell()
        waitFor { exists("shop.acme.dev") }
        assertThat(exists("Purge everything")).isTrue()
        compose.onNodeWithTag("command-bar").assertExists()
        capture("02-home")
        compose.onNodeWithTag("tab-browse").performClick()
        waitFor { exists("DNS Records") || exists("Workers") }
        capture("03-browse")
    }

    @Test fun `command turns a sentence into a prefilled DNS record`() {
        val actions = ActionStore(null)
        var opened: ActionDraft? = null
        val vm = track(CommandViewModel(
            engineProvider = { CommandEngine(registry) },
            planner = AiPlanner(raw(), { registry }),
            contextFlow = contextStore.state,
            pinsFlow = actions.pins, recentsFlow = actions.recents,
            loadZones = { zones.map { NamedRef(it.first, it.second, "acc1") } },
            loadAccounts = { listOf(NamedRef("acc1", "Acme Corp")) },
            loadResources = { emptyList() },
            selectAccount = {}, selectZone = {}
        ))
        show { CommandScreen(vm, onClose = {}, onOpenDraft = { opened = it }, onNavigate = {}) }
        waitFor { exists("Try") }
        capture("04-command-idle")
        compose.onNodeWithTag("command-input").performTextInput("add A record www 192.0.2.10 on shop.acme.dev")
        waitFor { exists("Add DNS record") }
        capture("05-command-typed")
        compose.onNodeWithTag("command-best").performClick()
        waitFor { opened != null }
        assertThat(opened!!.body).containsEntry("name", "www.shop.acme.dev")
        assertThat(opened!!.pathValues["zone_id"]).isEqualTo("z2")
    }

    private fun actionVm(draft: ActionDraft) = track(ActionViewModel(
        registryProvider = { registry }, client = raw(),
        tokenState = { _, _, _ -> CapabilityState.AVAILABLE_WRITE },
        workingContext = { contextStore.state.value },
        zones = { zones.map { NamedRef(it.first, it.second, "acc1") } },
        accounts = { listOf(NamedRef("acc1", "Acme Corp")) },
        onRan = {}, isPinned = { false }, togglePin = {},
        draft = draft, endpointId = null
    ))

    @Test fun `action form shows typed fields and a review step`() {
        val vm = actionVm(
            ActionDraft(
                "POST", "zones/{zone_id}/dns_records", "Add DNS record", mapOf("zone_id" to "z1"),
                body = mapOf("type" to "A", "name" to "www.acme.dev", "content" to "192.0.2.10", "proxied" to true, "ttl" to 1),
                source = ActionDraft.Source.RECIPE
            )
        )
        show { ActionScreen(vm, "acme.dev · Acme Corp", onBack = {}, onOpenDraft = {}, onOpenExplorer = { _, _, _ -> }) }
        waitFor { exists("Target") && exists("acme.dev", substring = true) }
        assertThat(exists("192.0.2.10")).isTrue()
        capture("06-action-form")
        compose.onNodeWithTag("action-run").performClick()
        waitFor { exists("This changes your Cloudflare configuration", substring = true) }
        capture("07-action-confirm")
    }

    @Test fun `action runs a read and lists results with follow ups`() {
        val vm = actionVm(ActionDraft("GET", "zones/{zone_id}/dns_records", "List DNS records", mapOf("zone_id" to "z1"), autoRun = true))
        show { ActionScreen(vm, "acme.dev · Acme Corp", onBack = {}, onOpenDraft = {}, onOpenExplorer = { _, _, _ -> }) }
        waitFor { exists("4 items") }
        assertThat(exists("_dmarc.acme.dev")).isTrue()
        capture("08-action-results")
        assertThat(vm.followUps(isList = true).map { it.endpoint.method }).containsAtLeast("PATCH", "DELETE")
    }

    @Test fun `zone hub shows live toggles and grouped features`() {
        val vm = track(ZoneMenuViewModel("z1", ZonesRepository(api()), ZoneSettingsRepository(api())))
        show { ZoneMenuScreen("acme.dev", vm, onBack = {}, onFeatureClick = {}) }
        waitFor { exists("Development mode") && exists("Quick controls") && exists("Pro Website") }
        capture("09-zone-hub")
    }

    @Test fun `profile groups token tools and app settings`() {
        show {
            ProfileScreen("acc1", accountStore.getActive(), accountStore.getAll(), "10cdded1d9e9", {}, {}, {}, {})
        }
        assertThat(exists("What this token can do")).isTrue()
        capture("10-profile")
    }

    @Test fun `existing screens inherit the design system`() {
        val vm = track(DnsViewModel("z1", DnsRepository(api())))
        show { DnsScreen(vm, zoneName = "acme.dev", onBack = {}) }
        waitFor { exists("www.acme.dev", substring = true) }
        capture("11-dns-native")
    }

    @Test fun `activity renders in the new shell style`() {
        val db = Room.inMemoryDatabaseBuilder(context, CfDatabase::class.java).allowMainThreadQueries().build()
        val activity = track(ActivityViewModel(db.requestHistoryDao(), "p", { registry }))
        show { ActivityContent(activity, onRepeat = {}, onOpenTransfers = {}, onOpenAuditLogs = {}) }
        waitFor { true }
        capture("12-activity")
    }
}
