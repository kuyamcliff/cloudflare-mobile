package dev.cfmobile.app.ui

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.DnssecStatus
import dev.cfmobile.app.data.remote.dto.WaitingRoom
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.CertificatesRepository
import dev.cfmobile.app.data.repository.HealthChecksRepository
import dev.cfmobile.app.data.repository.WaitingRoomRepository
import dev.cfmobile.app.ui.certificates.CertificatesViewModel
import dev.cfmobile.app.ui.certificates.dnssecStatusLabel
import dev.cfmobile.app.ui.certificates.isDnssecActive
import dev.cfmobile.app.ui.certificates.validateHostname
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.healthchecks.HealthCheckFormState
import dev.cfmobile.app.ui.healthchecks.HealthChecksViewModel
import dev.cfmobile.app.ui.healthchecks.healthStatusTone
import dev.cfmobile.app.ui.healthchecks.validateHealthCheckForm
import dev.cfmobile.app.ui.waitingroom.WaitingRoomFormState
import dev.cfmobile.app.ui.waitingroom.WaitingRoomViewModel
import dev.cfmobile.app.ui.waitingroom.validateWaitingRoomForm
import dev.cfmobile.app.ui.waitingroom.waitingRoomStatusLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Certificates/DNSSEC/custom hostnames, Waiting Room, and standalone Health Checks. */
class ZoneInfraViewModelTest {

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
    fun `isDnssecActive treats pending as not yet enabled`() {
        // "pending" means the DS record still has to be published at the registrar - showing
        // that as on would tell the user they're protected when they aren't.
        assertThat(isDnssecActive(DnssecStatus(status = "active"))).isTrue()
        assertThat(isDnssecActive(DnssecStatus(status = "pending"))).isFalse()
        assertThat(isDnssecActive(DnssecStatus(status = "disabled"))).isFalse()
        assertThat(isDnssecActive(null)).isFalse()
    }

    @Test
    fun `dnssecStatusLabel explains the pending case and passes unknown states through`() {
        assertThat(dnssecStatusLabel(DnssecStatus(status = "pending"))).contains("registrar")
        assertThat(dnssecStatusLabel(DnssecStatus(status = "active"))).isEqualTo("Active")
        assertThat(dnssecStatusLabel(null)).isEqualTo("Disabled")
        assertThat(dnssecStatusLabel(DnssecStatus(status = "something-new"))).isEqualTo("Something-new")
    }

    @Test
    fun `validateHostname requires a real hostname`() {
        assertThat(validateHostname("")).isEqualTo("Hostname is required")
        assertThat(validateHostname("not a host")).contains("valid hostname")
        assertThat(validateHostname("app.customer.com")).isNull()
    }

    @Test
    fun `validateWaitingRoomForm checks the path and the numeric thresholds`() {
        assertThat(validateWaitingRoomForm(WaitingRoomFormState())).isEqualTo("Room name is required")
        assertThat(validateWaitingRoomForm(WaitingRoomFormState(name = "Sale"))).contains("Host")
        assertThat(
            validateWaitingRoomForm(WaitingRoomFormState(name = "Sale", host = "shop.example.com", path = "checkout"))
        ).contains("start with /")
        assertThat(
            validateWaitingRoomForm(
                WaitingRoomFormState(name = "Sale", host = "shop.example.com", newUsersPerMinute = "0")
            )
        ).contains("positive")
        assertThat(
            validateWaitingRoomForm(WaitingRoomFormState(name = "Sale", host = "shop.example.com"))
        ).isNull()
    }

    @Test
    fun `waitingRoomStatusLabel reflects suspension`() {
        assertThat(waitingRoomStatusLabel(WaitingRoom(id = "w", name = "Sale", host = "h"))).isEqualTo("Active")
        assertThat(waitingRoomStatusLabel(WaitingRoom(id = "w", name = "Sale", host = "h", suspended = true)))
            .isEqualTo("Suspended")
    }

    @Test
    fun `healthStatusTone maps healthy, unhealthy, and not-yet-probed`() {
        assertThat(healthStatusTone("healthy")).isEqualTo("healthy")
        assertThat(healthStatusTone("unhealthy")).isEqualTo("unhealthy")
        assertThat(healthStatusTone("unknown")).isEqualTo("unknown")
        assertThat(healthStatusTone(null)).isEqualTo("unknown")
    }

    @Test
    fun `validateHealthCheckForm requires a name and an address`() {
        assertThat(validateHealthCheckForm(HealthCheckFormState())).isEqualTo("Check name is required")
        assertThat(validateHealthCheckForm(HealthCheckFormState(name = "Origin"))).contains("Address")
        assertThat(validateHealthCheckForm(HealthCheckFormState(name = "Origin", address = "origin.example.com"))).isNull()
    }

    @Test
    fun `setDnssecEnabled sends the status string Cloudflare expects`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"status":"active"}}"""))

        val result = CertificatesRepository(testApi(server)).setDnssecEnabled("zone1", true)

        assertThat((result as ApiResult.Success).data.status).isEqualTo("active")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.body.readUtf8()).contains("\"status\":\"active\"")
    }

    @Test
    fun `certificates screen loads packs, hostnames, and dnssec together`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/ssl/certificate_packs") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"p1","type":"universal","hosts":["example.com"],"status":"active"}]}""")
                    path.endsWith("/custom_hostnames") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"h1","hostname":"app.customer.com"}]}""")
                    path.endsWith("/dnssec") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":{"status":"pending","ds":"example.com. IN DS 2371 13 2 ABC"}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val vm = CertificatesViewModel("zone1", CertificatesRepository(testApi(server)))
        val state = vm.uiState.first { it.packs !is UiState.Loading && it.hostnames !is UiState.Loading && it.dnssec != null }

        assertThat((state.packs as UiState.Data).value).hasSize(1)
        assertThat((state.hostnames as UiState.Data).value.single().hostname).isEqualTo("app.customer.com")
        assertThat(state.dnssec?.ds).contains("IN DS")
        assertThat(isDnssecActive(state.dnssec)).isFalse()
    }

    @Test
    fun `waiting room save rejects an invalid form before calling the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = WaitingRoomViewModel("zone1", WaitingRoomRepository(testApi(server)))
        vm.uiState.first { it.rooms !is UiState.Loading }
        val requestsBefore = server.requestCount

        vm.openForm()
        vm.save()

        assertThat(vm.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `a valid health check save creates it and closes the form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = HealthChecksViewModel("zone1", HealthChecksRepository(testApi(server)))
        vm.uiState.first { it.checks !is UiState.Loading }

        vm.openForm()
        vm.updateForm { it.copy(name = "Origin", address = "origin.example.com") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"c1","name":"Origin","address":"origin.example.com"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"c1","name":"Origin","address":"origin.example.com"}]}"""))

        vm.save()
        val state = vm.uiState.first { it.form == null && (it.checks as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.checks as UiState.Data).value.single().name).isEqualTo("Origin")
    }
}
