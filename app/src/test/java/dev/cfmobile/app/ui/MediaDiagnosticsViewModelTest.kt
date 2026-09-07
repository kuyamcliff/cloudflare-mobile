package dev.cfmobile.app.ui

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.TurnstileWidget
import dev.cfmobile.app.data.remote.dto.WaitingRoom
import dev.cfmobile.app.data.remote.dto.WaitingRoomEvent
import dev.cfmobile.app.data.remote.dto.ZoneDnsSettings
import dev.cfmobile.app.data.remote.dto.ZoneNameserverSettings
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.TurnstileRepository
import dev.cfmobile.app.data.repository.WaitingRoomRepository
import dev.cfmobile.app.data.repository.ZoneDnsSettingsRepository
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.dns.DnsSetting
import dev.cfmobile.app.ui.dns.DnsSettingsViewModel
import dev.cfmobile.app.ui.dns.nameserverSummary
import dev.cfmobile.app.ui.turnstile.TurnstileViewModel
import dev.cfmobile.app.ui.waitingroom.EventFormState
import dev.cfmobile.app.ui.waitingroom.WaitingRoomViewModel
import dev.cfmobile.app.ui.waitingroom.buildEventWrite
import dev.cfmobile.app.ui.waitingroom.eventOverrides
import dev.cfmobile.app.ui.waitingroom.eventSummary
import dev.cfmobile.app.ui.waitingroom.validateEventForm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Turnstile secret rotation, Waiting Room events, and zone DNS settings. */
class MediaDiagnosticsViewModelTest {

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

    // ---- Turnstile rotation ----

    @Test
    fun `rotating a secret shows the new value exactly once`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"sitekey":"0x1","name":"Checkout"}]}""")
        )
        val vm = TurnstileViewModel("acct1", TurnstileRepository(testApi(server)))
        val widget = (vm.uiState.first { it.widgets !is UiState.Loading }.widgets as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"sitekey":"0x1","secret":"0xNEW","name":"Checkout"}}""")
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"sitekey":"0x1","name":"Checkout"}]}"""))

        vm.rotateSecret(widget)
        val state = vm.uiState.first { it.rotatedSecret != null }

        assertThat(state.rotatedSecret?.secret).isEqualTo("0xNEW")
        assertThat(state.rotatingSitekey).isNull()
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "POST" }.path)
            .isEqualTo("/accounts/acct1/challenges/widgets/0x1/rotate_secret")

        // Dismissing is what drops it - nothing else holds a reference.
        vm.dismissRotatedSecret()
        assertThat(vm.uiState.value.rotatedSecret).isNull()
    }

    @Test
    fun `a rotation that returns no secret says where to read it`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"sitekey":"0x1","name":"Checkout"}]}"""))
        val vm = TurnstileViewModel("acct1", TurnstileRepository(testApi(server)))
        vm.uiState.first { it.widgets !is UiState.Loading }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"sitekey":"0x1"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.rotateSecret(TurnstileWidget(sitekey = "0x1", name = "Checkout"))
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("dashboard")
        assertThat(state.rotatedSecret).isNull()
    }

    @Test
    fun `a rejected rotation reports why and shows no secret`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"sitekey":"0x1","name":"Checkout"}]}"""))
        val vm = TurnstileViewModel("acct1", TurnstileRepository(testApi(server)))
        vm.uiState.first { it.widgets !is UiState.Loading }
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Unauthorized"}],"result":null}""")
        )

        vm.rotateSecret(TurnstileWidget(sitekey = "0x1", name = "Checkout"))
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("Unauthorized")
        assertThat(state.rotatedSecret).isNull()
        assertThat(state.rotatingSitekey).isNull()
    }

    // ---- Waiting Room events ----

    @Test
    fun `an event window has to be well-formed UTC and end after it starts`() {
        val valid = EventFormState(
            name = "Sale",
            startTime = "2026-01-02T15:00:00Z",
            endTime = "2026-01-02T18:00:00Z"
        )

        assertThat(validateEventForm(valid)).isNull()
        assertThat(validateEventForm(valid.copy(name = ""))).contains("name is required")
        assertThat(validateEventForm(valid.copy(startTime = "2026-01-02 15:00"))).contains("Start time")
        assertThat(validateEventForm(valid.copy(endTime = "tomorrow"))).contains("End time")
        assertThat(validateEventForm(valid.copy(endTime = "2026-01-02T15:00:00Z"))).contains("end after it starts")
        assertThat(validateEventForm(valid.copy(newUsersPerMinute = "0"))).contains("New users")
        assertThat(validateEventForm(valid.copy(totalActiveUsers = "lots"))).contains("Total active")
    }

    @Test
    fun `blank overrides mean keep the room's own thresholds`() {
        val write = buildEventWrite(
            EventFormState(name = "Sale", startTime = "2026-01-02T15:00:00Z", endTime = "2026-01-02T18:00:00Z")
        )

        assertThat(write.newUsersPerMinute).isNull()
        assertThat(write.totalActiveUsers).isNull()
        assertThat(write.description).isNull()
    }

    @Test
    fun `eventSummary and eventOverrides read as the window and its overrides`() {
        val event = WaitingRoomEvent(
            id = "e1",
            name = "Sale",
            eventStartTime = "2026-01-02T15:00:00Z",
            eventEndTime = "2026-01-02T18:00:00Z",
            newUsersPerMinute = 500
        )

        assertThat(eventSummary(event)).isEqualTo("2026-01-02T15:00:00Z → 2026-01-02T18:00:00Z")
        assertThat(eventOverrides(event)).isEqualTo("500 new users/min")
        assertThat(eventOverrides(WaitingRoomEvent(id = "e1", name = "Sale"))).isNull()
    }

    @Test
    fun `opening a room loads the events stored under it`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","name":"Checkout","host":"a.com"}]}""")
        )
        val vm = WaitingRoomViewModel("zone1", WaitingRoomRepository(testApi(server)))
        val room = (vm.uiState.first { it.rooms !is UiState.Loading }.rooms as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"e1","name":"Sale"}]}""")
        )

        vm.openEvents(room)
        val state = vm.uiState.first { it.events?.isLoading == false }

        assertThat(state.events?.events?.single()?.name).isEqualTo("Sale")
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.last().path).isEqualTo("/zones/zone1/waiting_rooms/r1/events")
    }

    @Test
    fun `scheduling an event posts it under its room`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","name":"Checkout","host":"a.com"}]}"""))
        val vm = WaitingRoomViewModel("zone1", WaitingRoomRepository(testApi(server)))
        val room = (vm.uiState.first { it.rooms !is UiState.Loading }.rooms as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        vm.openEvents(room)
        vm.uiState.first { it.events?.isLoading == false }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"e1","name":"Sale"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"e1","name":"Sale"}]}"""))

        vm.openEventForm()
        vm.updateEventForm {
            it.copy(name = "Sale", startTime = "2026-01-02T15:00:00Z", endTime = "2026-01-02T18:00:00Z")
        }
        vm.saveEvent()
        vm.uiState.first { it.events?.form == null && it.events?.events?.isNotEmpty() == true }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val post = requests.single { it.method == "POST" }
        assertThat(post.path).isEqualTo("/zones/zone1/waiting_rooms/r1/events")
        assertThat(post.body.readUtf8()).contains("\"event_start_time\":\"2026-01-02T15:00:00Z\"")
    }

    @Test
    fun `an invalid event never reaches the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","name":"Checkout","host":"a.com"}]}"""))
        val vm = WaitingRoomViewModel("zone1", WaitingRoomRepository(testApi(server)))
        val room = (vm.uiState.first { it.rooms !is UiState.Loading }.rooms as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        vm.openEvents(room)
        vm.uiState.first { it.events?.isLoading == false }
        val before = server.requestCount

        vm.openEventForm()
        vm.updateEventForm { it.copy(name = "Sale", startTime = "soon", endTime = "later") }
        vm.saveEvent()

        assertThat(server.requestCount).isEqualTo(before)
        assertThat(vm.uiState.value.events?.form?.error).contains("Start time")
    }

    @Test
    fun `deleting an event addresses it under its room`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","name":"Checkout","host":"a.com"}]}"""))
        val vm = WaitingRoomViewModel("zone1", WaitingRoomRepository(testApi(server)))
        val room = (vm.uiState.first { it.rooms !is UiState.Loading }.rooms as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"e1","name":"Sale"}]}"""))
        vm.openEvents(room)
        vm.uiState.first { it.events?.isLoading == false }
        // Cloudflare answers an event delete with the event it removed.
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"e1","name":"Sale"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.deleteEvent(WaitingRoomEvent(id = "e1", name = "Sale"))
        vm.uiState.first { it.events?.deletingId == null && it.events?.events?.isEmpty() == true }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "DELETE" }.path)
            .isEqualTo("/zones/zone1/waiting_rooms/r1/events/e1")
    }

    // ---- Zone DNS settings ----

    @Test
    fun `each DNS switch sends only its own field`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"flatten_all_cnames":false,"multi_provider":true}}""")
        )
        val vm = DnsSettingsViewModel("zone1", ZoneDnsSettingsRepository(testApi(server)))
        vm.uiState.first { it.settings !is UiState.Loading }
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"flatten_all_cnames":true,"multi_provider":true}}""")
        )

        vm.setSetting(DnsSetting.FLATTEN_CNAMES, true)
        val state = vm.uiState.first { it.busySetting == null && (it.settings as? UiState.Data)?.value?.flattenAllCnames == true }

        // The other switch keeps its value, because it was never sent.
        assertThat((state.settings as UiState.Data).value.multiProvider).isTrue()
        server.takeRequest()
        val patch = server.takeRequest()
        assertThat(patch.method).isEqualTo("PATCH")
        assertThat(patch.path).isEqualTo("/zones/zone1/dns_settings")
        assertThat(patch.body.readUtf8()).isEqualTo("""{"flatten_all_cnames":true}""")
    }

    @Test
    fun `a rejected DNS setting change reports why and leaves the switches alone`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"foundation_dns":false}}"""))
        val vm = DnsSettingsViewModel("zone1", ZoneDnsSettingsRepository(testApi(server)))
        vm.uiState.first { it.settings !is UiState.Loading }
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":1000,"message":"Not available on this plan"}],"result":null}""")
        )

        vm.setSetting(DnsSetting.FOUNDATION_DNS, true)
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("Not available on this plan")
        assertThat((state.settings as UiState.Data).value.foundationDns).isFalse()
        assertThat(state.busySetting).isNull()
    }

    @Test
    fun `nameserverSummary names the assignment without pretending to know every type`() {
        assertThat(
            nameserverSummary(
                ZoneDnsSettings(nameservers = ZoneNameserverSettings(type = "cloudflare.standard"), nsTtl = 86400)
            )
        ).isEqualTo("Cloudflare nameservers · NS TTL 86400")
        assertThat(
            nameserverSummary(ZoneDnsSettings(nameservers = ZoneNameserverSettings(type = "custom.account", nsSet = 2)))
        ).isEqualTo("Custom nameservers · set 2")
        // An unfamiliar type is shown as-is rather than mislabelled.
        assertThat(nameserverSummary(ZoneDnsSettings(nameservers = ZoneNameserverSettings(type = "future.kind"))))
            .isEqualTo("future.kind")
        assertThat(nameserverSummary(ZoneDnsSettings())).isEqualTo("No nameserver details reported")
    }
}
