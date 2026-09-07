package dev.cfmobile.app.ui.zonesecurity

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.ClientCertificate
import dev.cfmobile.app.data.remote.dto.CustomNameserver
import dev.cfmobile.app.data.remote.dto.LockdownConfiguration
import dev.cfmobile.app.data.remote.dto.UserAgentRule
import dev.cfmobile.app.data.remote.dto.UserAgentRuleConfiguration
import dev.cfmobile.app.data.remote.dto.ZoneHold
import dev.cfmobile.app.data.remote.dto.ZoneLockdown
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.MutualTlsRepository
import dev.cfmobile.app.data.repository.ZoneFirewallLegacyRepository
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

/** Zone Lockdown, User Agent Blocking, mTLS certificates, and zone ownership. */
class ZoneSecurityViewModelsTest {

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

    // ---- Lockdown ----

    @Test
    fun `a lockdown source is read as an IP, a range, or a country from its shape`() {
        assertThat(lockdownConfigurationOf("203.0.113.10"))
            .isEqualTo(LockdownConfiguration(target = "ip", value = "203.0.113.10"))
        assertThat(lockdownConfigurationOf("198.51.100.0/24"))
            .isEqualTo(LockdownConfiguration(target = "ip_range", value = "198.51.100.0/24"))
        // A two-letter code is a country, and Cloudflare wants it upper-cased.
        assertThat(lockdownConfigurationOf("gb"))
            .isEqualTo(LockdownConfiguration(target = "country", value = "GB"))
    }

    @Test
    fun `parseLines drops blanks and duplicates`() {
        assertThat(parseLines("a\n\nb\na\n")).containsExactly("a", "b").inOrder()
        assertThat(parseLines("   ")).isEmpty()
    }

    @Test
    fun `a lockdown needs both a URL and a source`() {
        assertThat(validateLockdownForm(LockdownFormState(sources = "1.1.1.1"))).contains("URL pattern")
        assertThat(validateLockdownForm(LockdownFormState(urls = "a.com/*"))).contains("address, range, or country")
        assertThat(validateLockdownForm(LockdownFormState(urls = "a.com/*", sources = "1.1.1.1"))).isNull()
    }

    @Test
    fun `buildLockdownWrite splits the pasted blocks into entries`() {
        val write = buildLockdownWrite(
            LockdownFormState(urls = "a.com/admin*\nb.com/*", sources = "1.1.1.1\nGB", description = "admin only")
        )

        assertThat(write.urls).containsExactly("a.com/admin*", "b.com/*").inOrder()
        assertThat(write.configurations.map { it.target }).containsExactly("ip", "country").inOrder()
        assertThat(write.description).isEqualTo("admin only")
    }

    @Test
    fun `editing a lockdown starts from what Cloudflare stored`() {
        val lockdown = ZoneLockdown(
            id = "l1",
            description = "admin",
            paused = true,
            urls = listOf("a.com/admin"),
            configurations = listOf(LockdownConfiguration(target = "ip", value = "1.1.1.1"))
        )

        val form = lockdownFormOf(lockdown)

        assertThat(form.editingId).isEqualTo("l1")
        assertThat(form.urls).isEqualTo("a.com/admin")
        assertThat(form.sources).isEqualTo("1.1.1.1")
        assertThat(form.paused).isTrue()
    }

    @Test
    fun `lockdownSummary counts what the row can't show in full`() {
        val lockdown = ZoneLockdown(
            urls = listOf("a.com/1", "a.com/2"),
            configurations = listOf(LockdownConfiguration(value = "1.1.1.1"))
        )

        assertThat(lockdownSummary(lockdown)).isEqualTo("2 URLs · 1 source")
    }

    // ---- User Agent Blocking ----

    @Test
    fun `a user agent rule refuses a wildcard, which would match nothing`() {
        assertThat(validateUserAgentForm(UserAgentFormState(userAgent = "Bad*"))).contains("exactly")
        assertThat(validateUserAgentForm(UserAgentFormState(userAgent = ""))).contains("required")
        assertThat(validateUserAgentForm(UserAgentFormState(userAgent = "BadBot/1.0"))).isNull()
    }

    @Test
    fun `buildUserAgentWrite targets the ua header`() {
        val write = buildUserAgentWrite(UserAgentFormState(userAgent = "BadBot/1.0", mode = "managed_challenge"))

        assertThat(write.configuration.target).isEqualTo("ua")
        assertThat(write.configuration.value).isEqualTo("BadBot/1.0")
        assertThat(write.mode).isEqualTo("managed_challenge")
    }

    @Test
    fun `editing a user agent rule starts from what Cloudflare stored`() {
        val rule = UserAgentRule(
            id = "u1",
            mode = "js_challenge",
            paused = true,
            configuration = UserAgentRuleConfiguration(value = "BadBot/1.0")
        )

        val form = userAgentFormOf(rule)

        assertThat(form.editingId).isEqualTo("u1")
        assertThat(form.userAgent).isEqualTo("BadBot/1.0")
        assertThat(form.mode).isEqualTo("js_challenge")
        assertThat(uaModeLabel("js_challenge")).isEqualTo("JS Challenge")
    }

    private fun serveLegacyFirewall(lockdowns: String = "[]", uaRules: String = "[]") {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method != "GET" -> MockResponse().setBody("""{"success":true,"errors":[],"result":{}}""")
                    path.contains("/lockdowns") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":$lockdowns}""")
                    else -> MockResponse().setBody("""{"success":true,"errors":[],"result":$uaRules}""")
                }
            }
        }
    }

    @Test
    fun `both legacy firewall lists load together`() = runTest {
        serveLegacyFirewall(
            lockdowns = """[{"id":"l1","urls":["a.com/*"],"configurations":[{"target":"ip","value":"1.1.1.1"}]}]""",
            uaRules = """[{"id":"u1","mode":"block","configuration":{"target":"ua","value":"BadBot"}}]"""
        )
        val vm = LegacyFirewallViewModel("zone1", ZoneFirewallLegacyRepository(testApi(server)))

        val state = vm.uiState.first { it.lockdowns !is UiState.Loading && it.userAgentRules !is UiState.Loading }

        assertThat((state.lockdowns as UiState.Data).value.single().id).isEqualTo("l1")
        assertThat((state.userAgentRules as UiState.Data).value.single().id).isEqualTo("u1")
    }

    @Test
    fun `an invalid lockdown never reaches the network`() = runTest {
        serveLegacyFirewall()
        val vm = LegacyFirewallViewModel("zone1", ZoneFirewallLegacyRepository(testApi(server)))
        vm.uiState.first { it.lockdowns !is UiState.Loading && it.userAgentRules !is UiState.Loading }
        val requestsBefore = server.requestCount

        vm.openLockdownForm()
        vm.saveLockdown()

        assertThat(vm.uiState.value.lockdownForm?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    // ---- mTLS ----

    @Test
    fun `a revoked certificate is distinguishable from an active one`() {
        assertThat(isRevoked(ClientCertificate(status = "revoked"))).isTrue()
        assertThat(isRevoked(ClientCertificate(status = "active"))).isFalse()
        assertThat(certificateStatusLabel(ClientCertificate(status = "active", expiresOn = "2027-01-01")))
            .isEqualTo("Active · expires 2027-01-01")
        assertThat(certificateStatusLabel(ClientCertificate())).isEqualTo("Active")
    }

    @Test
    fun `the certificate list loads with origin pulls and Total TLS beside it`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("client_certificates") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"c1","common_name":"client.example.com","status":"active"}]}""")
                    path.contains("origin_tls_client_auth") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":true}}""")
                    else ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":true,"certificate_authority":"google"}}""")
                }
            }
        }
        val vm = MutualTlsViewModel("zone1", MutualTlsRepository(testApi(server)))

        val state = vm.uiState.first { it.certificates !is UiState.Loading && it.totalTls != null }

        assertThat((state.certificates as UiState.Data).value.single().commonName).isEqualTo("client.example.com")
        assertThat(state.originPullsEnabled).isTrue()
        assertThat(state.totalTls?.certificateAuthority).isEqualTo("google")
    }

    @Test
    fun `turning Total TLS off drops the certificate authority from the request`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("client_certificates") -> MockResponse().setBody("""{"success":true,"errors":[],"result":[]}""")
                    path.contains("origin_tls_client_auth") -> MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":false}}""")
                    else -> MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":false}}""")
                }
            }
        }
        val vm = MutualTlsViewModel("zone1", MutualTlsRepository(testApi(server)))
        vm.uiState.first { it.certificates !is UiState.Loading && it.totalTls != null }

        vm.setTotalTls(false)
        vm.uiState.first { !it.isSaving }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val post = requests.single { it.method == "POST" }
        val body = post.body.readUtf8()
        assertThat(body).contains("\"enabled\":false")
        assertThat(body).doesNotContain("certificate_authority")
    }

    // ---- Zone ownership ----

    @Test
    fun `nameserver sets are grouped and labelled by their set number`() {
        val nameservers = listOf(
            CustomNameserver(nsName = "ns1.example.com", nsSet = 1),
            CustomNameserver(nsName = "ns2.example.com", nsSet = 1),
            CustomNameserver(nsName = "ns3.example.com", nsSet = 2)
        )

        val sets = nameserverSets(nameservers)

        assertThat(sets.map { it.first }).containsExactly("1", "2").inOrder()
        assertThat(sets.first().second).contains("ns1.example.com, ns2.example.com")
    }

    @Test
    fun `holdSummary says whether the domain can be claimed elsewhere`() {
        assertThat(holdSummary(null)).contains("can be added to another")
        assertThat(holdSummary(ZoneHold(hold = false))).contains("can be added to another")
        assertThat(holdSummary(ZoneHold(hold = true))).isEqualTo("Held")
        assertThat(holdSummary(ZoneHold(hold = true, includeSubdomains = true))).contains("including subdomains")
    }
}
