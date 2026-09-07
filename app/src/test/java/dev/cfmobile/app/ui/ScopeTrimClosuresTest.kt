package dev.cfmobile.app.ui

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.BotManagementConfig
import dev.cfmobile.app.data.remote.dto.EmailCatchAll
import dev.cfmobile.app.data.remote.dto.EmailCatchAllAction
import dev.cfmobile.app.data.remote.dto.EmailDestinationAddress
import dev.cfmobile.app.data.remote.dto.LoadBalancerMonitor
import dev.cfmobile.app.data.remote.dto.LoadBalancerPool
import dev.cfmobile.app.data.remote.dto.PageShieldPolicy
import dev.cfmobile.app.data.remote.dto.WorkflowInstance
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.BotManagementRepository
import dev.cfmobile.app.data.repository.WorkflowsRepository
import dev.cfmobile.app.ui.botmanagement.BotManagementViewModel
import dev.cfmobile.app.ui.botmanagement.hasSuperBotFightMode
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.emailrouting.DestinationFormState
import dev.cfmobile.app.ui.emailrouting.catchAllSummary
import dev.cfmobile.app.ui.emailrouting.destinationStatus
import dev.cfmobile.app.ui.emailrouting.validateDestination
import dev.cfmobile.app.ui.loadbalancing.MonitorFormState
import dev.cfmobile.app.ui.loadbalancing.PoolFormState
import dev.cfmobile.app.ui.loadbalancing.OriginFormState
import dev.cfmobile.app.ui.loadbalancing.buildMonitorWrite
import dev.cfmobile.app.ui.loadbalancing.buildPoolWrite
import dev.cfmobile.app.ui.loadbalancing.monitorSummary
import dev.cfmobile.app.ui.loadbalancing.poolMonitorLabel
import dev.cfmobile.app.ui.loadbalancing.validateMonitorForm
import dev.cfmobile.app.ui.pageshield.PageShieldPolicyForm
import dev.cfmobile.app.ui.pageshield.policyFormOf
import dev.cfmobile.app.ui.pageshield.validatePageShieldPolicy
import dev.cfmobile.app.ui.workflows.WorkflowsViewModel
import dev.cfmobile.app.ui.workflows.canPause
import dev.cfmobile.app.ui.workflows.canResume
import dev.cfmobile.app.ui.workflows.canTerminate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The per-feature gaps closed together: LB health monitors, Super Bot Fight Mode, email
 *  destinations and catch-all, Workflow run control, and Page Shield policies. */
class ScopeTrimClosuresTest {

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

    // ---- Load balancing monitors ----

    @Test
    fun `a monitor timeout has to fit inside its interval`() {
        val form = MonitorFormState(interval = "10", timeout = "10")
        assertThat(validateMonitorForm(form)).contains("shorter than the interval")
        assertThat(validateMonitorForm(form.copy(timeout = "5"))).isNull()
    }

    @Test
    fun `a monitor path has to be a path and the interval a sane number`() {
        val form = MonitorFormState()
        assertThat(validateMonitorForm(form.copy(path = "health"))).contains("start with /")
        assertThat(validateMonitorForm(form.copy(interval = "5"))).contains("at least 10")
        assertThat(validateMonitorForm(form.copy(expectedCodes = ""))).contains("Expected status codes")
        assertThat(validateMonitorForm(form)).isNull()
    }

    @Test
    fun `buildMonitorWrite sends an HTTP GET monitor with the numbers parsed`() {
        val write = buildMonitorWrite(
            MonitorFormState(description = "Origin health", path = "/health", interval = "30", retries = "1", timeout = "5")
        )

        assertThat(write.type).isEqualTo("http")
        assertThat(write.method).isEqualTo("GET")
        assertThat(write.interval).isEqualTo(30)
        assertThat(write.timeout).isEqualTo(5)
    }

    @Test
    fun `a pool carries the monitor chosen for it`() {
        val write = buildPoolWrite(
            PoolFormState(name = "eu", origins = listOf(OriginFormState(address = "203.0.113.10")), monitorId = "m1")
        )

        assertThat(write.monitor).isEqualTo("m1")
        assertThat(buildPoolWrite(PoolFormState(name = "eu", origins = listOf(OriginFormState(address = "1.1.1.1")))).monitor).isNull()
    }

    @Test
    fun `a pool with no monitor says what that costs`() {
        val monitors = listOf(LoadBalancerMonitor(id = "m1", description = "Origin health"))

        assertThat(poolMonitorLabel(LoadBalancerPool(id = "p1"), monitors)).contains("no automatic failover")
        assertThat(poolMonitorLabel(LoadBalancerPool(id = "p1", monitor = "m1"), monitors)).isEqualTo("Origin health")
        // A monitor the account didn't return still identifies itself rather than reading as absent.
        assertThat(poolMonitorLabel(LoadBalancerPool(id = "p1", monitor = "gone"), monitors)).contains("gone")
    }

    @Test
    fun `monitorSummary describes the check it performs`() {
        val monitor = LoadBalancerMonitor(method = "GET", path = "/health", interval = 60, expectedCodes = "2xx")

        assertThat(monitorSummary(monitor)).isEqualTo("GET /health · every 60s · expects 2xx")
    }

    // ---- Super Bot Fight Mode ----

    @Test
    fun `Super Bot Fight Mode is detected by the fields Cloudflare returns, not a plan field`() {
        assertThat(hasSuperBotFightMode(BotManagementConfig(fightMode = true))).isFalse()
        assertThat(hasSuperBotFightMode(BotManagementConfig(definitelyAutomated = "block"))).isTrue()
    }

    @Test
    fun `a bot setting change sends back the config the zone reported, with one field changed`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"sbfm_definitely_automated":"managed_challenge","sbfm_likely_automated":"allow","sbfm_verified_bots":"allow"}}"""
            )
        )
        val vm = BotManagementViewModel("zone1", BotManagementRepository(testApi(server)))
        vm.uiState.first { it.config !is UiState.Loading }
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"sbfm_definitely_automated":"block","sbfm_likely_automated":"allow","sbfm_verified_bots":"allow"}}"""
            )
        )

        vm.setDefinitelyAutomated("block")
        val state = vm.uiState.first { !it.isSaving && (it.config as? UiState.Data)?.value?.definitelyAutomated == "block" }

        assertThat(state.error).isNull()
        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"sbfm_definitely_automated\":\"block\"")
        // The untouched fields go back too, and a field the plan never sent stays absent.
        assertThat(body).contains("\"sbfm_likely_automated\":\"allow\"")
        assertThat(body).doesNotContain("fight_mode")
    }

    @Test
    fun `a rejected bot setting leaves the shown value alone`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"fight_mode":false}}"""))
        val vm = BotManagementViewModel("zone1", BotManagementRepository(testApi(server)))
        vm.uiState.first { it.config !is UiState.Loading }
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":1004,"message":"Not available on this plan"}],"result":null}""")
        )

        vm.setFightMode(true)
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("Not available on this plan")
        assertThat((state.config as UiState.Data).value.fightMode).isFalse()
    }

    // ---- Email destinations and catch-all ----

    @Test
    fun `a destination address has to look like an email`() {
        assertThat(validateDestination("")).contains("required")
        assertThat(validateDestination("not an email")).contains("valid email")
        assertThat(validateDestination("me@example.com")).isNull()
        assertThat(DestinationFormState(email = "me@example.com").email).isEqualTo("me@example.com")
    }

    @Test
    fun `an unverified destination says so rather than looking usable`() {
        assertThat(destinationStatus(EmailDestinationAddress(email = "a@b.com"))).contains("Not verified")
        assertThat(destinationStatus(EmailDestinationAddress(email = "a@b.com", verified = "2026-01-01")))
            .isEqualTo("Verified")
    }

    @Test
    fun `catchAllSummary spells out where unmatched mail goes`() {
        assertThat(catchAllSummary(null)).contains("off")
        assertThat(catchAllSummary(EmailCatchAll(enabled = false))).contains("off")
        assertThat(
            catchAllSummary(
                EmailCatchAll(enabled = true, actions = listOf(EmailCatchAllAction(type = "forward", value = listOf("me@b.com"))))
            )
        ).isEqualTo("Catch-all forwards to me@b.com")
        assertThat(catchAllSummary(EmailCatchAll(enabled = true, actions = listOf(EmailCatchAllAction(type = "drop")))))
            .contains("drops unmatched mail")
    }

    // ---- Workflow run control ----

    @Test
    fun `only the transitions Cloudflare accepts for a run's state are offered`() {
        val running = WorkflowInstance(id = "i1", status = "running")
        val paused = WorkflowInstance(id = "i2", status = "paused")
        val done = WorkflowInstance(id = "i3", status = "complete")

        assertThat(canPause(running)).isTrue()
        assertThat(canResume(running)).isFalse()
        assertThat(canTerminate(running)).isTrue()

        assertThat(canPause(paused)).isFalse()
        assertThat(canResume(paused)).isTrue()

        assertThat(canPause(done)).isFalse()
        assertThat(canResume(done)).isFalse()
        assertThat(canTerminate(done)).isFalse()
    }

    @Test
    fun `terminating a run patches its status and reloads the instances`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-flow"}]}"""))
        val vm = WorkflowsViewModel("acct1", WorkflowsRepository(testApi(server)))
        val workflow = (vm.uiState.first { it.workflows !is UiState.Loading }.workflows as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"i1","status":"running"}]}"""))
        vm.selectWorkflow(workflow)
        val instance = (vm.uiState.first { it.instances is UiState.Data }.instances as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"i1","status":"terminated"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"i1","status":"terminated"}]}"""))

        vm.setInstanceStatus(instance, "terminate")
        vm.uiState.first { (it.instances as? UiState.Data)?.value?.single()?.status == "terminated" }

        server.takeRequest()
        server.takeRequest()
        val patch = server.takeRequest()
        assertThat(patch.method).isEqualTo("PATCH")
        assertThat(patch.path).isEqualTo("/accounts/acct1/workflows/my-flow/instances/i1/status")
        assertThat(patch.body.readUtf8()).isEqualTo("""{"status":"terminate"}""")
    }

    @Test
    fun `triggering a run posts to the instances collection`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-flow"}]}"""))
        val vm = WorkflowsViewModel("acct1", WorkflowsRepository(testApi(server)))
        val workflow = (vm.uiState.first { it.workflows !is UiState.Loading }.workflows as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        vm.selectWorkflow(workflow)
        vm.uiState.first { it.instances is UiState.Data }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"new","status":"queued"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"new","status":"queued"}]}"""))

        vm.trigger()
        val state = vm.uiState.first { (it.instances as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat(state.message).isNotNull()
        assertThat(state.isTriggering).isFalse()
        server.takeRequest()
        server.takeRequest()
        val post = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.path).isEqualTo("/accounts/acct1/workflows/my-flow/instances")
    }

    // ---- Page Shield policies ----

    @Test
    fun `a policy needs both expressions, since an empty one would allow nothing`() {
        assertThat(validatePageShieldPolicy(PageShieldPolicyForm(value = "x"))).contains("page expression")
        assertThat(validatePageShieldPolicy(PageShieldPolicyForm(expression = "x")))
            .contains("would allow no scripts")
        assertThat(validatePageShieldPolicy(PageShieldPolicyForm(expression = "x", value = "y"))).isNull()
    }

    @Test
    fun `editing a policy starts from what Cloudflare stored`() {
        val policy = PageShieldPolicy(
            id = "p1",
            action = "log",
            description = "allow the cdn",
            enabled = false,
            expression = "http.host eq \"a.com\"",
            value = "http.request.uri.host in {\"cdn.a.com\"}"
        )

        val form = policyFormOf(policy)

        assertThat(form.editingId).isEqualTo("p1")
        assertThat(form.action).isEqualTo("log")
        assertThat(form.enabled).isFalse()
        assertThat(form.value).contains("cdn.a.com")
    }
}
