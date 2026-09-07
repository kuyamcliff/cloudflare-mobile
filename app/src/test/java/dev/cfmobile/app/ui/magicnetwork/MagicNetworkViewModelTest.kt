package dev.cfmobile.app.ui.magicnetwork

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.MagicRoute
import dev.cfmobile.app.data.remote.dto.MagicSite
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.MagicNetworkRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The writeable half of Magic WAN: static routes, plus the sites tab. */
class MagicNetworkViewModelTest {

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

    private fun viewModel() = MagicNetworkViewModel("acct1", MagicNetworkRepository(testApi(server)))

    /** The four calls one load makes, in order. */
    private fun enqueueLoad(routes: String = "", sites: String = "") {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"gre_tunnels":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"ipsec_tunnels":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"routes":[$routes]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[$sites]}"""))
    }

    private suspend fun MagicNetworkViewModel.awaitLoaded() =
        uiState.first { it.routes !is UiState.Loading && it.sites !is UiState.Loading && !it.isRefreshing }

    // ---- Validation and formatting ----

    @Test
    fun `a route prefix has to carry its mask`() {
        val valid = MagicRouteFormState(prefix = "10.0.0.0/8", nexthop = "10.0.0.1")

        assertThat(validateRouteForm(valid)).isNull()
        // A bare host address routes nothing on its own.
        assertThat(validateRouteForm(valid.copy(prefix = "10.0.0.0"))).contains("mask")
        assertThat(validateRouteForm(valid.copy(prefix = ""))).contains("prefix is required")
        assertThat(validateRouteForm(valid.copy(nexthop = " "))).contains("next hop")
        assertThat(validateRouteForm(valid.copy(priority = "soon"))).contains("Priority")
        assertThat(validateRouteForm(valid.copy(weight = "heavy"))).contains("Weight")
        // Weight is optional, so blank stays valid.
        assertThat(validateRouteForm(valid.copy(weight = ""))).isNull()
    }

    @Test
    fun `an IPv6 prefix is accepted too`() {
        assertThat(
            validateRouteForm(MagicRouteFormState(prefix = "2001:db8::/32", nexthop = "2001:db8::1"))
        ).isNull()
    }

    @Test
    fun `buildRouteWrite drops the optional fields rather than sending blanks`() {
        val write = buildRouteWrite(
            MagicRouteFormState(prefix = " 10.0.0.0/8 ", nexthop = " 10.0.0.1 ", priority = "50")
        )

        assertThat(write.prefix).isEqualTo("10.0.0.0/8")
        assertThat(write.nexthop).isEqualTo("10.0.0.1")
        assertThat(write.priority).isEqualTo(50)
        assertThat(write.description).isNull()
        assertThat(write.weight).isNull()
    }

    @Test
    fun `routeSummary reads as the route it installs`() {
        assertThat(routeSummary(MagicRoute(id = "r1", prefix = "10.0.0.0/8", nexthop = "10.0.0.1", priority = 100)))
            .isEqualTo("via 10.0.0.1 · priority 100")
        assertThat(routeSummary(MagicRoute(id = "r1", prefix = "10.0.0.0/8"))).isEmpty()
    }

    @Test
    fun `siteSummary counts connectors and says when there are none`() {
        assertThat(siteSummary(MagicSite(id = "s1", name = "Berlin", connectorId = "c1")))
            .isEqualTo("1 connector")
        assertThat(
            siteSummary(MagicSite(id = "s1", connectorId = "c1", secondaryConnectorId = "c2", haMode = true))
        ).isEqualTo("2 connectors · high availability")
        assertThat(siteSummary(MagicSite(id = "s1"))).isEqualTo("No connector bound")
    }

    // ---- Routes ----

    @Test
    fun `adding a route posts it and reloads`() = runTest {
        enqueueLoad()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"routes":[{"id":"r1","prefix":"10.0.0.0/8"}]}}""")
        )
        enqueueLoad(routes = """{"id":"r1","prefix":"10.0.0.0/8"}""")

        vm.openRouteForm()
        vm.updateRouteForm { it.copy(prefix = "10.0.0.0/8", nexthop = "10.0.0.1", priority = "100") }
        vm.saveRoute()
        vm.uiState.first { it.routeForm == null && (it.routes as? UiState.Data)?.value?.isNotEmpty() == true }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val post = requests.single { it.method == "POST" }
        assertThat(post.path).isEqualTo("/accounts/acct1/magic/routes")
        assertThat(post.body.readUtf8()).contains("\"prefix\":\"10.0.0.0/8\"")
    }

    @Test
    fun `an invalid route never reaches the network`() = runTest {
        enqueueLoad()
        val vm = viewModel()
        vm.awaitLoaded()
        val before = server.requestCount

        vm.openRouteForm()
        vm.updateRouteForm { it.copy(prefix = "10.0.0.0", nexthop = "10.0.0.1") }
        vm.saveRoute()

        assertThat(server.requestCount).isEqualTo(before)
        assertThat(vm.uiState.value.routeForm?.error).contains("mask")
    }

    @Test
    fun `deleting a route addresses it by id`() = runTest {
        enqueueLoad(routes = """{"id":"r1","prefix":"10.0.0.0/8"}""")
        val vm = viewModel()
        val route = (vm.awaitLoaded().routes as UiState.Data).value.single()
        // Cloudflare answers a route delete with the route it removed.
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"deleted":true,"deleted_route":{"id":"r1","prefix":"10.0.0.0/8"}}}""")
        )
        enqueueLoad()

        vm.deleteRoute(route)
        vm.uiState.first { it.deletingId == null && (it.routes as? UiState.Data)?.value?.isEmpty() == true }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "DELETE" }.path).isEqualTo("/accounts/acct1/magic/routes/r1")
    }

    @Test
    fun `a rejected route delete reports why and keeps the route`() = runTest {
        enqueueLoad(routes = """{"id":"r1","prefix":"10.0.0.0/8"}""")
        val vm = viewModel()
        val route = (vm.awaitLoaded().routes as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":1004,"message":"Route is in use"}],"result":null}""")
        )

        vm.deleteRoute(route)
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("Route is in use")
        assertThat((state.routes as UiState.Data).value).hasSize(1)
    }

    // ---- Sites ----

    @Test
    fun `opening a site fetches its LAN and WAN interfaces`() = runTest {
        enqueueLoad(sites = """{"id":"s1","name":"Berlin"}""")
        val vm = viewModel()
        val site = (vm.awaitLoaded().sites as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"lans":[{"id":"l1","name":"office","physport":1}]}}""")
        )
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"wans":[{"id":"w1","name":"uplink","priority":10}]}}""")
        )

        vm.openSite(site)
        val state = vm.uiState.first { it.selectedSite?.isLoading == false }

        assertThat(state.selectedSite?.lans?.single()?.name).isEqualTo("office")
        assertThat(state.selectedSite?.wans?.single()?.name).isEqualTo("uplink")
        assertThat(state.selectedSite?.error).isNull()
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.map { it.path }).containsAtLeast(
            "/accounts/acct1/magic/sites/s1/lans",
            "/accounts/acct1/magic/sites/s1/wans"
        )
    }

    @Test
    fun `a site whose interfaces can't be read still opens, with the reason`() = runTest {
        enqueueLoad(sites = """{"id":"s1","name":"Berlin"}""")
        val vm = viewModel()
        val site = (vm.awaitLoaded().sites as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Unauthorized"}],"result":null}""")
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"wans":[]}}"""))

        vm.openSite(site)
        val state = vm.uiState.first { it.selectedSite?.isLoading == false }

        assertThat(state.selectedSite?.error).contains("Unauthorized")
        assertThat(state.selectedSite?.lans).isEmpty()
    }

    @Test
    fun `closing a site before its interfaces arrive doesn't reopen it`() = runTest {
        enqueueLoad(sites = """{"id":"s1","name":"Berlin"}""")
        val vm = viewModel()
        val site = (vm.awaitLoaded().sites as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"lans":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"wans":[]}}"""))

        vm.openSite(site)
        vm.closeSite()
        // Let the in-flight pair land against a closed sheet.
        vm.uiState.first { it.selectedSite == null }

        assertThat(vm.uiState.value.selectedSite).isNull()
    }
}
