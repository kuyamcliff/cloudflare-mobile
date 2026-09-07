package dev.cfmobile.app.ui.addressing

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.AddressMap
import dev.cfmobile.app.data.remote.dto.AddressMapIp
import dev.cfmobile.app.data.remote.dto.AddressMapMembership
import dev.cfmobile.app.data.remote.dto.AddressingPrefix
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.AddressingRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AddressingViewModelTest {

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

    private fun viewModel() = AddressingViewModel("acct1", AddressingRepository(testApi(server)))

    private suspend fun AddressingViewModel.awaitLoaded() =
        uiState.first { it.prefixes !is UiState.Loading && it.addressMaps !is UiState.Loading }

    private fun enqueueEmptyLists(count: Int = 2) =
        repeat(count) { server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}""")) }

    private val approvedPrefix = AddressingPrefix(
        id = "p1",
        cidr = "203.0.113.0/24",
        approved = "approved",
        onDemandEnabled = true
    )

    // ---- Advertisement gating ----

    @Test
    fun `advertisement can only be toggled on an approved, unlocked, on-demand prefix`() {
        assertThat(canToggleAdvertisement(approvedPrefix)).isTrue()
        assertThat(canToggleAdvertisement(approvedPrefix.copy(approved = "pending"))).isFalse()
        assertThat(canToggleAdvertisement(approvedPrefix.copy(onDemandEnabled = false))).isFalse()
        assertThat(canToggleAdvertisement(approvedPrefix.copy(onDemandLocked = true))).isFalse()
    }

    @Test
    fun `the reason the switch is inert is specific, not just disabled`() {
        assertThat(advertisementBlockedReason(approvedPrefix)).isNull()
        assertThat(advertisementBlockedReason(approvedPrefix.copy(approved = "pending")))
            .contains("hasn't approved")
        assertThat(advertisementBlockedReason(approvedPrefix.copy(onDemandLocked = true)))
            .contains("locked")
        assertThat(advertisementBlockedReason(approvedPrefix.copy(onDemandEnabled = false)))
            .contains("isn't enabled")
    }

    @Test
    fun `prefixSummary reports approval state and ASN`() {
        assertThat(prefixSummary(approvedPrefix.copy(asn = 64512))).isEqualTo("AS64512 · approved")
        assertThat(prefixSummary(approvedPrefix.copy(approved = "pending"))).isEqualTo("approval pending")
        assertThat(prefixSummary(AddressingPrefix(id = "p1", cidr = "203.0.113.0/24")))
            .isEqualTo("No approval status reported")
    }

    @Test
    fun `addressMapSummary counts without listing every address`() {
        val map = AddressMap(
            id = "m1",
            enabled = true,
            ips = listOf(AddressMapIp("192.0.2.1"), AddressMapIp("192.0.2.2")),
            memberships = listOf(AddressMapMembership(identifier = "z1", kind = "zone"))
        )

        val summary = addressMapSummary(map)

        assertThat(summary).isEqualTo("2 IPs · 1 binding · enabled")
        assertThat(summary).doesNotContain("192.0.2.1")
        assertThat(addressMapSummary(AddressMap(id = "m1"))).isEqualTo("disabled")
    }

    // ---- Loading ----

    @Test
    fun `both collections load from the addressing endpoints`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"p1","cidr":"203.0.113.0/24"}]}""")
        )
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"m1","description":"EU"}]}""")
        )

        val state = viewModel().awaitLoaded()

        assertThat((state.prefixes as UiState.Data).value.single().cidr).isEqualTo("203.0.113.0/24")
        assertThat((state.addressMaps as UiState.Data).value.single().description).isEqualTo("EU")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/addressing/prefixes")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/addressing/address_maps")
    }

    // ---- Advertisement ----

    @Test
    fun `toggling advertisement patches the bgp status sub-resource`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"p1","cidr":"203.0.113.0/24","approved":"approved","on_demand_enabled":true,"advertised":false}]}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        val prefix = (vm.awaitLoaded().prefixes as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"advertised":true,"advertised_modified_at":"2026-01-02"}}""")
        )

        vm.setAdvertised(prefix, true)
        val state = vm.uiState.first { (it.prefixes as? UiState.Data)?.value?.single()?.advertised == true }

        assertThat(state.busyId).isNull()
        assertThat((state.prefixes as UiState.Data).value.single().advertisedModifiedAt).isEqualTo("2026-01-02")
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val patch = requests.single { it.method == "PATCH" }
        assertThat(patch.path).isEqualTo("/accounts/acct1/addressing/prefixes/p1/bgp/status")
        assertThat(patch.body.readUtf8()).isEqualTo("""{"advertised":true}""")
        // Only the one PATCH past the two loads: the row is patched in place.
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `a rejected advertisement change reports the reason and leaves the row alone`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"p1","cidr":"203.0.113.0/24","advertised":false}]}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        val prefix = (vm.awaitLoaded().prefixes as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":1004,"message":"Prefix is not approved"}],"result":null}""")
        )

        vm.setAdvertised(prefix, true)
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("not approved")
        assertThat((state.prefixes as UiState.Data).value.single().advertised).isFalse()
        assertThat(state.busyId).isNull()
    }

    // ---- Address maps ----

    @Test
    fun `enabling a map keeps the ips the PATCH response omits`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"m1","description":"EU","enabled":false,"ips":[{"ip":"192.0.2.1"}]}]}"""
            )
        )
        val vm = viewModel()
        val map = (vm.awaitLoaded().addressMaps as UiState.Data).value.single()
        // Cloudflare answers the PATCH with the map's scalar fields only.
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"m1","enabled":true}}"""))

        vm.setAddressMapEnabled(map, true)
        val state = vm.uiState.first { (it.addressMaps as? UiState.Data)?.value?.single()?.enabled == true }

        val updated = (state.addressMaps as UiState.Data).value.single()
        assertThat(updated.ips?.single()?.ip).isEqualTo("192.0.2.1")
        assertThat(updated.description).isEqualTo("EU")
    }

    @Test
    fun `opening a map fetches the ips and memberships the list omits`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"m1","description":"EU"}]}"""))
        val vm = viewModel()
        val map = (vm.awaitLoaded().addressMaps as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"m1","description":"EU","ips":[{"ip":"192.0.2.1"}],"memberships":[{"identifier":"z1","kind":"zone"}]}}"""
            )
        )

        vm.openMap(map)
        val state = vm.uiState.first { it.selectedMap?.ips != null }

        assertThat(state.isLoadingMap).isFalse()
        assertThat(state.selectedMap?.memberships?.single()?.identifier).isEqualTo("z1")
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.last().path).isEqualTo("/accounts/acct1/addressing/address_maps/m1")
    }

    @Test
    fun `a map needs a description, since that is all that identifies it`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        val before = server.requestCount

        vm.openForm()
        vm.save()

        assertThat(server.requestCount).isEqualTo(before)
        assertThat(vm.uiState.value.form?.error).contains("description is required")
    }

    @Test
    fun `creating a map posts its description and enabled flag`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"m1","description":"EU"}}"""))
        enqueueEmptyLists()

        vm.openForm()
        vm.updateForm { it.copy(description = "EU", enabled = true) }
        vm.save()
        vm.uiState.first { it.form == null }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val post = requests.single { it.method == "POST" }
        assertThat(post.path).isEqualTo("/accounts/acct1/addressing/address_maps")
        assertThat(post.body.readUtf8()).isEqualTo("""{"description":"EU","enabled":true}""")
    }

    @Test
    fun `deleting a map closes its detail sheet as well as removing the row`() = runTest {
        enqueueEmptyLists()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        enqueueEmptyLists()

        vm.deleteAddressMap(AddressMap(id = "m1", description = "EU"))
        vm.uiState.first { it.deletingId == null && !it.isRefreshing }

        assertThat(vm.uiState.value.selectedMap).isNull()
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "DELETE" }.path)
            .isEqualTo("/accounts/acct1/addressing/address_maps/m1")
    }
}
