package dev.cfmobile.app.ui.zerotrust

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.AccessEmailDomainRule
import dev.cfmobile.app.data.remote.dto.AccessEmailRule
import dev.cfmobile.app.data.remote.dto.AccessGroup
import dev.cfmobile.app.data.remote.dto.AccessMtlsCertificate
import dev.cfmobile.app.data.remote.dto.AccessPolicyIncludeRule
import dev.cfmobile.app.data.remote.dto.DeviceServiceMode
import dev.cfmobile.app.data.remote.dto.DeviceSettingsPolicy
import dev.cfmobile.app.data.remote.dto.GatewayLocation
import dev.cfmobile.app.data.remote.dto.GatewayLocationNetwork
import dev.cfmobile.app.data.remote.dto.TunnelRoute
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.AccessRepository
import dev.cfmobile.app.ui.common.UiState
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

/** Access groups, bookmarks, tags and mTLS roots, plus Zero Trust's private-network side. */
class ZeroTrustBreadthTest {

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

    // ---- Access directory ----

    @Test
    fun `a group with no rules is refused, since it would match nobody`() {
        assertThat(validateAccessGroupForm(AccessGroupFormState(name = "Team"))).contains("matches nobody")
        assertThat(validateAccessGroupForm(AccessGroupFormState(emailDomain = "a.com"))).contains("name is required")
        assertThat(validateAccessGroupForm(AccessGroupFormState(name = "Team", emailDomain = "a.com"))).isNull()
        assertThat(validateAccessGroupForm(AccessGroupFormState(name = "Team", emails = "a@b.com"))).isNull()
    }

    @Test
    fun `a group form rejects a malformed domain or address before sending`() {
        assertThat(validateAccessGroupForm(AccessGroupFormState(name = "T", emailDomain = "not a domain")))
            .contains("e.g. example.com")
        assertThat(validateAccessGroupForm(AccessGroupFormState(name = "T", emails = "nope")))
            .contains("isn't a valid email")
    }

    @Test
    fun `buildGroupIncludes turns the form into Cloudflare's include rules`() {
        val includes = buildGroupIncludes(
            AccessGroupFormState(name = "Team", emailDomain = "example.com", emails = "a@b.com\nc@d.com")
        )

        assertThat(includes).hasSize(3)
        assertThat(includes.first().emailDomain?.domain).isEqualTo("example.com")
        assertThat(includes.drop(1).mapNotNull { it.email?.email }).containsExactly("a@b.com", "c@d.com")
    }

    @Test
    fun `groupSummary counts rule kinds this app doesn't model rather than hiding them`() {
        val group = AccessGroup(
            id = "g1",
            name = "Team",
            include = listOf(
                AccessPolicyIncludeRule(emailDomain = AccessEmailDomainRule("example.com")),
                AccessPolicyIncludeRule(email = AccessEmailRule("a@b.com")),
                // An include rule with neither field set is one of the kinds this app doesn't
                // model - it still has to be counted.
                AccessPolicyIncludeRule()
            )
        )

        assertThat(groupSummary(group)).isEqualTo("@example.com · 1 address · 1 other rule")
        assertThat(groupSummary(AccessGroup(id = "g2", name = "Empty"))).contains("matches nobody")
    }

    @Test
    fun `a bookmark takes a hostname, not a URL`() {
        val form = AccessBookmarkFormState(name = "Wiki", domain = "https://wiki.a.com")
        assertThat(validateBookmarkForm(form)).contains("Leave off the scheme")
        assertThat(validateBookmarkForm(form.copy(domain = "wiki.a.com"))).isNull()
        assertThat(validateBookmarkForm(form.copy(name = "", domain = "wiki.a.com"))).contains("Name")
    }

    @Test
    fun `certificateSummary says when a root isn't associated with anything`() {
        assertThat(certificateSummary(AccessMtlsCertificate(id = "c1")))
            .isEqualTo("Not associated with any hostname")
        assertThat(
            certificateSummary(
                AccessMtlsCertificate(id = "c1", associatedHostnames = listOf("a.com"), expiresOn = "2027-01-01")
            )
        ).isEqualTo("1 hostname · expires 2027-01-01")
    }

    @Test
    fun `the directory loads all four lists in one pass`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/groups") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"g1","name":"Team"}]}""")
                    path.endsWith("/bookmarks") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"b1","name":"Wiki"}]}""")
                    path.endsWith("/tags") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"internal","app_count":2}]}""")
                    else ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"c1","name":"Root"}]}""")
                }
            }
        }
        val vm = AccessDirectoryViewModel("acct1", AccessRepository(testApi(server)))

        val state = vm.uiState.first {
            it.groups !is UiState.Loading && it.bookmarks !is UiState.Loading &&
                it.tags !is UiState.Loading && it.certificates !is UiState.Loading
        }

        assertThat((state.groups as UiState.Data).value.single().name).isEqualTo("Team")
        assertThat((state.tags as UiState.Data).value.single().appCount).isEqualTo(2)
        assertThat((state.certificates as UiState.Data).value.single().name).isEqualTo("Root")
    }

    @Test
    fun `the certificates tab offers no create action`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setBody("""{"success":true,"errors":[],"result":[]}""")
        }
        val vm = AccessDirectoryViewModel("acct1", AccessRepository(testApi(server)))
        vm.uiState.first { it.certificates !is UiState.Loading }

        vm.selectTab(AccessDirectoryTab.CERTIFICATES)
        vm.openForm()

        // Uploading a root certificate means handling a chain, which this app doesn't do.
        assertThat(vm.uiState.value.groupForm).isNull()
        assertThat(vm.uiState.value.bookmarkForm).isNull()
        assertThat(vm.uiState.value.tagForm).isNull()
    }

    // ---- Zero Trust network ----

    @Test
    fun `isCidr accepts a range and rejects a bare address`() {
        assertThat(isCidr("10.0.0.0/8")).isTrue()
        assertThat(isCidr("10.0.0.1")).isFalse()
        assertThat(isCidr("10.0.0.0/33")).isFalse()
        assertThat(isCidr("not a range")).isFalse()
    }

    @Test
    fun `a route needs a CIDR range and a tunnel`() {
        val form = TunnelRouteFormState(network = "10.0.0.0/8", tunnelId = "t1")
        assertThat(validateRouteForm(form)).isNull()
        assertThat(validateRouteForm(form.copy(network = "10.0.0.1"))).contains("CIDR")
        assertThat(validateRouteForm(form.copy(network = ""))).contains("required")
        assertThat(validateRouteForm(form.copy(tunnelId = null))).contains("tunnel")
    }

    @Test
    fun `a location's networks all have to be CIDR ranges`() {
        assertThat(validateLocationForm(GatewayLocationFormState(name = "", networks = ""))).contains("name")
        assertThat(validateLocationForm(GatewayLocationFormState(name = "Office", networks = "203.0.113.5")))
            .contains("isn't a CIDR")
        assertThat(validateLocationForm(GatewayLocationFormState(name = "Office", networks = "203.0.113.0/24"))).isNull()
        // A DoH-only location has no source networks at all, which is allowed.
        assertThat(validateLocationForm(GatewayLocationFormState(name = "Remote"))).isNull()
    }

    @Test
    fun `locationNetworks splits the pasted block into entries`() {
        val networks = locationNetworks(GatewayLocationFormState(networks = "203.0.113.0/24\n198.51.100.0/24"))

        assertThat(networks).containsExactly(
            GatewayLocationNetwork(network = "203.0.113.0/24"),
            GatewayLocationNetwork(network = "198.51.100.0/24")
        ).inOrder()
    }

    @Test
    fun `routeSummary names the tunnel a route goes through`() {
        assertThat(routeSummary(TunnelRoute(id = "r1", network = "10.0.0.0/8", tunnelName = "office", comment = "HQ")))
            .isEqualTo("through office · HQ")
        assertThat(routeSummary(TunnelRoute(id = "r1", network = "10.0.0.0/8", tunnelId = "t1")))
            .isEqualTo("t1")
    }

    @Test
    fun `locationSummary reports the DoH hostname and network count`() {
        val location = GatewayLocation(
            id = "l1",
            name = "Office",
            dohSubdomain = "abc123",
            clientDefault = true,
            networks = listOf(GatewayLocationNetwork(network = "203.0.113.0/24"))
        )

        assertThat(locationSummary(location))
            .isEqualTo("abc123.cloudflare-gateway.com · 1 network · default")
    }

    @Test
    fun `warpProfileSummary reports the service mode that decides what WARP takes over`() {
        val profile = DeviceSettingsPolicy(
            policyId = "p1",
            name = "Default",
            default = true,
            precedence = 1,
            serviceMode = DeviceServiceMode(mode = "warp")
        )

        assertThat(warpProfileSummary(profile)).isEqualTo("mode warp · precedence 1 · default profile")
        assertThat(warpProfileSummary(DeviceSettingsPolicy(match = "identity.email == \"a@b.com\"")))
            .isEqualTo("identity.email == \"a@b.com\"")
    }
}
