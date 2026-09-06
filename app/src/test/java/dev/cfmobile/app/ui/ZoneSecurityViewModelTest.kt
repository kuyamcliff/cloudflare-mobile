package dev.cfmobile.app.ui

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.ApiOperation
import dev.cfmobile.app.data.remote.dto.FirewallEvent
import dev.cfmobile.app.data.remote.dto.PageShieldScript
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.ApiShieldRepository
import dev.cfmobile.app.data.repository.DdosRepository
import dev.cfmobile.app.data.repository.PageShieldRepository
import dev.cfmobile.app.data.repository.SecurityEventsRepository
import dev.cfmobile.app.ui.apishield.ApiShieldViewModel
import dev.cfmobile.app.ui.apishield.operationLabel
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.ddos.DdosViewModel
import dev.cfmobile.app.ui.pageshield.PageShieldViewModel
import dev.cfmobile.app.ui.pageshield.scriptIntegrityLabel
import dev.cfmobile.app.ui.securityevents.EventWindow
import dev.cfmobile.app.ui.securityevents.SecurityEventsViewModel
import dev.cfmobile.app.ui.securityevents.eventActionLabel
import dev.cfmobile.app.ui.securityevents.eventOriginLabel
import dev.cfmobile.app.ui.securityevents.eventRequestLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ZoneSecurityViewModelTest {

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

    @Test
    fun `event labels tolerate the fields a plan didn't return`() {
        val full = FirewallEvent(
            action = "managed_challenge",
            clientIP = "203.0.113.10",
            clientCountryName = "Canada",
            clientAsn = "13335",
            clientRequestHTTPHost = "example.com",
            clientRequestPath = "/login",
            clientRequestHTTPMethodName = "POST"
        )
        assertThat(eventActionLabel(full)).isEqualTo("Managed challenge")
        assertThat(eventRequestLabel(full)).isEqualTo("POST example.com/login")
        assertThat(eventOriginLabel(full)).isEqualTo("203.0.113.10 · Canada · AS13335")

        val bare = FirewallEvent()
        assertThat(eventActionLabel(bare)).isEqualTo("Unknown action")
        assertThat(eventRequestLabel(bare)).isNull()
        assertThat(eventOriginLabel(bare)).isNull()
    }

    @Test
    fun `scriptIntegrityLabel flags a low score and stays silent without one`() {
        assertThat(scriptIntegrityLabel(PageShieldScript(id = "s", jsIntegrityScore = 4))).contains("review")
        assertThat(scriptIntegrityLabel(PageShieldScript(id = "s", jsIntegrityScore = 80))).isEqualTo("Integrity score 80")
        assertThat(scriptIntegrityLabel(PageShieldScript(id = "s"))).isNull()
    }

    @Test
    fun `operationLabel reads as method plus endpoint`() {
        assertThat(operationLabel(ApiOperation(operationId = "o1", method = "GET", endpoint = "/api/users")))
            .isEqualTo("GET /api/users")
        assertThat(operationLabel(ApiOperation(operationId = "o1"))).isEqualTo("o1")
    }

    @Test
    fun `security events load on init and reload when the window changes`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"data":{"viewer":{"zones":[{"firewallEventsAdaptive":[{"action":"block"}]}]}}}""")
        )
        val vm = SecurityEventsViewModel("zone1", SecurityEventsRepository(testApi(server)))
        vm.uiState.first { it.events !is UiState.Loading }
        server.takeRequest()

        server.enqueue(
            MockResponse().setBody("""{"data":{"viewer":{"zones":[{"firewallEventsAdaptive":[]}]}}}""")
        )
        vm.selectWindow(EventWindow.LAST_7_DAYS)
        val state = vm.uiState.first { (it.events as? UiState.Data)?.value?.isEmpty() == true }

        assertThat(state.window).isEqualTo(EventWindow.LAST_7_DAYS)
        assertThat(server.takeRequest().body.readUtf8()).contains("\"limit\"")
    }

    @Test
    fun `page shield loads settings and both lists in one pass`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":true}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"s1"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"c1"}]}"""))

        val vm = PageShieldViewModel("zone1", PageShieldRepository(testApi(server)))
        val state = vm.uiState.first { it.scripts !is UiState.Loading && it.connections !is UiState.Loading }

        assertThat(state.isEnabled).isTrue()
        assertThat((state.scripts as UiState.Data).value).hasSize(1)
        assertThat((state.connections as UiState.Data).value).hasSize(1)
    }

    @Test
    fun `a failed page shield toggle keeps the switch on the server's value and reports why`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":true}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = PageShieldViewModel("zone1", PageShieldRepository(testApi(server)))
        vm.uiState.first { it.scripts !is UiState.Loading && it.connections !is UiState.Loading }

        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Requires a paid plan"}],"result":null}""")
        )
        vm.setEnabled(false)
        val state = vm.uiState.first { !it.isTogglingEnabled && it.settingsError != null }

        // The switch must not claim the change took effect.
        assertThat(state.isEnabled).isTrue()
        assertThat(state.settingsError).contains("Requires a paid plan")
    }

    @Test
    fun `a zone with no ddos entrypoint ruleset renders as empty, not as an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))

        val vm = DdosViewModel("zone1", DdosRepository(testApi(server)))
        val state = vm.uiState.first { it.ruleset !is UiState.Loading }

        assertThat(state.ruleset).isInstanceOf(UiState.Data::class.java)
        assertThat((state.ruleset as UiState.Data).value).isNull()
    }

    @Test
    fun `a real ddos failure still surfaces as an error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Authentication error"}],"result":null}""")
        )

        val vm = DdosViewModel("zone1", DdosRepository(testApi(server)))
        val state = vm.uiState.first { it.ruleset !is UiState.Loading }

        assertThat(state.ruleset).isInstanceOf(UiState.Error::class.java)
    }

    @Test
    fun `api shield loads discovered operations`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"operation_id":"o1","method":"GET","endpoint":"/api"}]}""")
        )

        val vm = ApiShieldViewModel("zone1", ApiShieldRepository(testApi(server)))
        val state = vm.uiState.first { it.operations !is UiState.Loading }

        assertThat((state.operations as UiState.Data).value).hasSize(1)
    }
}
