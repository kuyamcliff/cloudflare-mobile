package dev.cfmobile.app.ui.performance

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.ManagedHeader
import dev.cfmobile.app.data.remote.dto.RuleActionParameters
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.PerformanceRepository
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.rules.ConfigRuleForm
import dev.cfmobile.app.ui.rules.ConfigSetting
import dev.cfmobile.app.ui.rules.buildConfigRuleWrite
import dev.cfmobile.app.ui.rules.configFormOf
import dev.cfmobile.app.ui.rules.configSettingOf
import dev.cfmobile.app.ui.rules.configSummary
import dev.cfmobile.app.ui.rules.validateConfigForm
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

/** The routing/cache controls outside /settings, and Config Rules. */
class PerformanceAndConfigRulesTest {

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

    /** Answers every performance endpoint; [failing] names paths that should 403 instead. */
    private fun servePerformance(failing: Set<String> = emptySet()) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (failing.any { path.contains(it) }) {
                    return MockResponse().setResponseCode(403)
                        .setBody("""{"success":false,"errors":[{"code":1004,"message":"Not available on this plan"}],"result":null}""")
                }
                return when {
                    path.contains("managed_headers") -> MockResponse().setBody(
                        """{"success":true,"errors":[],"result":{"managed_request_headers":[{"id":"add_true_client_ip_headers","enabled":false}],
                            "managed_response_headers":[{"id":"remove_x_powered_by_header","enabled":true}]}}"""
                    )
                    path.contains("url_normalization") -> MockResponse().setBody(
                        """{"success":true,"errors":[],"result":{"type":"cloudflare","scope":"incoming"}}"""
                    )
                    else -> MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"x","value":"on"}}""")
                }
            }
        }
    }

    private fun viewModel() = PerformanceViewModel("zone1", PerformanceRepository(testApi(server)))

    private suspend fun PerformanceViewModel.awaitLoaded() = uiState.first { !it.isLoading }

    @Test
    fun `on and off round-trip as Cloudflare's strings`() {
        assertThat(isOn("on")).isTrue()
        assertThat(isOn("off")).isFalse()
        assertThat(isOn(null)).isFalse()
        assertThat(onOff(true)).isEqualTo("on")
        assertThat(onOff(false)).isEqualTo("off")
    }

    @Test
    fun `every control loads in one pass`() = runTest {
        servePerformance()

        val state = viewModel().awaitLoaded()

        assertThat(state.smartRouting).isEqualTo("on")
        assertThat(state.cacheReserve).isEqualTo("on")
        assertThat(state.managedHeaders?.requestHeaders).hasSize(1)
        assertThat((state.urlNormalization as UiState.Data).value.type).isEqualTo("cloudflare")
        assertThat(state.unavailable).isEmpty()
    }

    @Test
    fun `a control the plan doesn't include is listed as unavailable, not an error screen`() = runTest {
        servePerformance(failing = setOf("argo/smart_routing", "cache/cache_reserve"))

        val state = viewModel().awaitLoaded()

        assertThat(state.smartRouting).isNull()
        assertThat(state.unavailable).containsExactly("Argo Smart Routing", "Cache Reserve")
        // The rest of the screen still works.
        assertThat(state.tieredCaching).isEqualTo("on")
    }

    @Test
    fun `a rejected toggle leaves the shown value alone`() = runTest {
        servePerformance()
        val vm = viewModel()
        vm.awaitLoaded()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":1004,"message":"Not entitled"}],"result":null}""")
        }

        vm.setSmartRouting(false)
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("Not entitled")
        assertThat(state.smartRouting).isEqualTo("on")
    }

    @Test
    fun `toggling a managed header sends the whole document back`() = runTest {
        servePerformance()
        val vm = viewModel()
        val header = vm.awaitLoaded().managedHeaders!!.requestHeaders.single()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"managed_request_headers":[{"id":"add_true_client_ip_headers","enabled":true}],
                    "managed_response_headers":[{"id":"remove_x_powered_by_header","enabled":true}]}}"""
            )
        }

        vm.setManagedHeader(header, enabled = true, isRequest = true)
        val state = vm.uiState.first { it.managedHeaders?.requestHeaders?.single()?.enabled == true }

        assertThat(state.isSaving).isFalse()
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val patch = requests.last { it.method == "PATCH" }
        val body = patch.body.readUtf8()
        // The untouched response header has to be resent or Cloudflare would clear it.
        assertThat(body).contains("remove_x_powered_by_header")
        assertThat(body).contains("add_true_client_ip_headers")
    }

    @Test
    fun `managedHeaderLabel makes Cloudflare's ids readable and flags conflicts`() {
        val header = ManagedHeader(id = "add_true_client_ip_headers")
        assertThat(managedHeaderLabel(header)).isEqualTo("Add True Client Ip Headers")
        assertThat(managedHeaderConflict(header)).isNull()

        val conflicted = header.copy(hasConflict = true, conflictsWith = listOf("my_rule"))
        assertThat(managedHeaderConflict(conflicted)).contains("my_rule")
    }

    // ---- Config Rules ----

    @Test
    fun `a config rule writes the chosen setting as its own key`() {
        val write = buildConfigRuleWrite(
            ConfigRuleForm(expression = "http.host eq \"a.com\"", setting = ConfigSetting.ROCKET_LOADER, settingEnabled = false)
        )

        assertThat(write.action).isEqualTo("set_config")
        assertThat(write.actionParameters?.rocketLoader).isFalse()
        // Only the chosen setting is sent; the rest stay absent.
        assertThat(write.actionParameters?.mirage).isNull()
        assertThat(write.actionParameters?.emailObfuscation).isNull()
    }

    @Test
    fun `configSettingOf finds whichever setting the stored rule populated`() {
        val parameters = RuleActionParameters(disableZaraz = true)

        val found = configSettingOf(parameters)

        assertThat(found?.first).isEqualTo(ConfigSetting.DISABLE_ZARAZ)
        assertThat(found?.second).isTrue()
        assertThat(configSettingOf(RuleActionParameters())).isNull()
        assertThat(configSettingOf(null)).isNull()
    }

    @Test
    fun `editing a config rule starts from what Cloudflare stored`() {
        val rule = RulesetRule(
            id = "c1",
            action = "set_config",
            expression = "true",
            actionParameters = RuleActionParameters(mirage = false)
        )

        val form = configFormOf(rule)

        assertThat(form.editingId).isEqualTo("c1")
        assertThat(form.setting).isEqualTo(ConfigSetting.MIRAGE)
        assertThat(form.settingEnabled).isFalse()
    }

    @Test
    fun `configSummary says which setting the rule changes`() {
        val rule = RulesetRule(id = "c1", actionParameters = RuleActionParameters(rocketLoader = false))

        assertThat(configSummary(rule)).isEqualTo("Rocket Loader off")
        // A rule this app can't read falls back to its expression rather than lying.
        assertThat(configSummary(RulesetRule(id = "c2", expression = "true"))).isEqualTo("true")
    }

    @Test
    fun `a config rule still needs an expression`() {
        assertThat(validateConfigForm(ConfigRuleForm(expression = ""))).contains("Expression")
        assertThat(validateConfigForm(ConfigRuleForm())).isNull()
    }
}
