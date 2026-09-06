package dev.cfmobile.app.ui.account

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.ApiToken
import dev.cfmobile.app.data.remote.dto.BulkRedirect
import dev.cfmobile.app.data.remote.dto.NotificationMechanismTarget
import dev.cfmobile.app.data.remote.dto.NotificationMechanisms
import dev.cfmobile.app.data.remote.dto.NotificationPolicy
import dev.cfmobile.app.data.remote.dto.RegistrarDomain
import dev.cfmobile.app.data.remote.dto.RulesList
import dev.cfmobile.app.data.remote.dto.RulesListItem
import dev.cfmobile.app.data.remote.dto.RumRuleset
import dev.cfmobile.app.data.remote.dto.RumSite
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.ApiTokensRepository
import dev.cfmobile.app.data.repository.BulkRedirectsRepository
import dev.cfmobile.app.data.repository.NotificationsRepository
import dev.cfmobile.app.data.repository.WebAnalyticsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The account-level products added together: API tokens, notifications, bulk redirects,
 *  registrar, and Web Analytics. */
class AccountProductsViewModelTest {

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

    // ---- API tokens ----

    @Test
    fun `apiTokenSummary says plainly when a token has never been used`() {
        assertThat(apiTokenSummary(ApiToken(status = "active"))).isEqualTo("Active · never used")
        assertThat(apiTokenSummary(ApiToken(status = "active", lastUsedOn = "2026-01-02", expiresOn = "2026-06-01")))
            .isEqualTo("Active · last used 2026-01-02 · expires 2026-06-01")
    }

    @Test
    fun `api tokens load from the user-scoped endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"t1","name":"ci","status":"active"}]}"""))
        val vm = ApiTokensViewModel(ApiTokensRepository(testApi(server)))

        val state = vm.uiState.first { it.tokens !is UiState.Loading }

        assertThat((state.tokens as UiState.Data).value.single().name).isEqualTo("ci")
        assertThat(server.takeRequest().path).isEqualTo("/user/tokens")
    }

    @Test
    fun `a token list the signed-in token isn't allowed to read is an error, not an empty list`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Unauthorized to access requested resource"}],"result":null}""")
        )
        val vm = ApiTokensViewModel(ApiTokensRepository(testApi(server)))

        val state = vm.uiState.first { it.tokens !is UiState.Loading }

        assertThat(state.tokens).isInstanceOf(UiState.Error::class.java)
    }

    @Test
    fun `revoking a token deletes it by id and reloads`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"t1","name":"ci"}]}"""))
        val vm = ApiTokensViewModel(ApiTokensRepository(testApi(server)))
        vm.uiState.first { it.tokens !is UiState.Loading }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.revoke(ApiToken(id = "t1", name = "ci"))
        vm.uiState.first { it.deletingId == null && (it.tokens as? UiState.Data)?.value?.isEmpty() == true }

        server.takeRequest()
        val delete = server.takeRequest()
        assertThat(delete.method).isEqualTo("DELETE")
        assertThat(delete.path).isEqualTo("/user/tokens/t1")
    }

    // ---- Notifications ----

    @Test
    fun `alertTypeLabel makes Cloudflare's snake_case alert types readable`() {
        assertThat(alertTypeLabel("universal_ssl_event_type")).isEqualTo("Universal Ssl Event Type")
        assertThat(alertTypeLabel(null)).isEqualTo("Alert")
    }

    @Test
    fun `mechanismSummary counts destinations without listing addresses`() {
        val policy = NotificationPolicy(
            mechanisms = NotificationMechanisms(
                email = listOf(NotificationMechanismTarget(id = "a@b.com"), NotificationMechanismTarget(id = "c@d.com")),
                webhooks = listOf(NotificationMechanismTarget(id = "w1"))
            )
        )

        val summary = mechanismSummary(policy)

        assertThat(summary).isEqualTo("2 emails · 1 webhook")
        assertThat(summary).doesNotContain("a@b.com")
        assertThat(mechanismSummary(NotificationPolicy())).isEqualTo("No destinations")
    }

    @Test
    fun `silencing a policy patches only enabled and updates the row in place`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"p1","name":"SSL","enabled":true}]}"""))
        val vm = NotificationsViewModel("acct1", NotificationsRepository(testApi(server)))
        val policy = (vm.uiState.first { it.policies !is UiState.Loading }.policies as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"p1","name":"SSL","enabled":false}}"""))

        vm.setEnabled(policy, false)
        val state = vm.uiState.first { (it.policies as? UiState.Data)?.value?.single()?.enabled == false }

        assertThat(state.busyId).isNull()
        server.takeRequest()
        val patch = server.takeRequest()
        assertThat(patch.method).isEqualTo("PATCH")
        assertThat(patch.body.readUtf8()).isEqualTo("""{"enabled":false}""")
        // Only one extra request: the row is patched in place rather than refetching the list.
        assertThat(server.requestCount).isEqualTo(2)
    }

    // ---- Bulk redirects ----

    @Test
    fun `a list name is restricted to what Cloudflare accepts`() {
        assertThat(validateBulkRedirectForm(BulkRedirectFormState(name = "Marketing Redirects"))).contains("lowercase")
        assertThat(validateBulkRedirectForm(BulkRedirectFormState(name = ""))).contains("required")
        assertThat(validateBulkRedirectForm(BulkRedirectFormState(name = "marketing_redirects_1"))).isNull()
    }

    @Test
    fun `redirectItemSummary reads as the redirect it performs`() {
        val item = RulesListItem(
            id = "i1",
            redirect = BulkRedirect(sourceUrl = "example.com/old", targetUrl = "https://example.com/new", statusCode = 301)
        )

        assertThat(redirectItemSummary(item)).isEqualTo("example.com/old → https://example.com/new (301)")
    }

    @Test
    fun `bulkRedirectSubtitle counts the redirects in a list`() {
        assertThat(bulkRedirectSubtitle(RulesList(numItems = 1))).isEqualTo("1 redirect")
        assertThat(bulkRedirectSubtitle(RulesList())).isEqualTo("0 redirects")
    }

    @Test
    fun `only redirect-kind lists reach the bulk redirects screen`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[
                    {"id":"l1","name":"redirects","kind":"redirect","num_items":2},
                    {"id":"l2","name":"blocked_ips","kind":"ip","num_items":9}
                ]}"""
            )
        )
        val vm = BulkRedirectsViewModel("acct1", BulkRedirectsRepository(testApi(server)))

        val state = vm.uiState.first { it.lists !is UiState.Loading }

        // The same endpoint serves the IP lists WAF rules match against; they aren't redirects.
        assertThat((state.lists as UiState.Data).value.map { it.id }).containsExactly("l1")
    }

    @Test
    fun `creating a redirect list sends the redirect kind`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = BulkRedirectsViewModel("acct1", BulkRedirectsRepository(testApi(server)))
        vm.uiState.first { it.lists !is UiState.Loading }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"l1","name":"redirects","kind":"redirect"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"l1","name":"redirects","kind":"redirect"}]}"""))

        vm.openForm()
        vm.updateForm { it.copy(name = "redirects") }
        vm.save()
        vm.uiState.first { it.form == null && (it.lists as? UiState.Data)?.value?.isNotEmpty() == true }

        server.takeRequest()
        assertThat(server.takeRequest().body.readUtf8()).contains("\"kind\":\"redirect\"")
    }

    // ---- Registrar ----

    @Test
    fun `registrarSummary omits whatever Cloudflare didn't report`() {
        assertThat(registrarSummary(RegistrarDomain(name = "a.com", expiresAt = "2027-03-01", autoRenew = true, locked = false)))
            .isEqualTo("Expires 2027-03-01 · auto-renew on · unlocked")
        assertThat(registrarSummary(RegistrarDomain(name = "a.com")))
            .isEqualTo("No registration details reported")
    }

    @Test
    fun `registrarDetail names the registrar holding a domain that isn't Cloudflare's`() {
        assertThat(registrarDetail(RegistrarDomain(currentRegistrar = "Other Registrar")))
            .isEqualTo("Registrar: Other Registrar")
        assertThat(registrarDetail(RegistrarDomain())).isNull()
    }

    // ---- Web Analytics ----

    @Test
    fun `a RUM site is labelled by its zone, falling back to the site tag`() {
        assertThat(rumSiteLabel(RumSite(siteTag = "abc", ruleset = RumRuleset(zoneName = "example.com"))))
            .isEqualTo("example.com")
        assertThat(rumSiteLabel(RumSite(siteTag = "abc"))).isEqualTo("abc")
    }

    @Test
    fun `rumSiteSubtitle says whether a snippet is needed`() {
        assertThat(rumSiteSubtitle(RumSite(autoInstall = true))).isEqualTo("Auto-installed")
        assertThat(rumSiteSubtitle(RumSite(autoInstall = false, ruleset = RumRuleset(enabled = true))))
            .isEqualTo("Snippet required · enabled")
    }

    @Test
    fun `a site host has to look like a hostname`() {
        assertThat(validateWebAnalyticsForm(WebAnalyticsFormState(host = ""))).contains("required")
        assertThat(validateWebAnalyticsForm(WebAnalyticsFormState(host = "not a host"))).contains("hostname")
        assertThat(validateWebAnalyticsForm(WebAnalyticsFormState(host = "example.com"))).isNull()
    }

    @Test
    fun `creating a manually installed site offers its snippet straight away`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = WebAnalyticsViewModel("acct1", WebAnalyticsRepository(testApi(server)))
        vm.uiState.first { it.sites !is UiState.Loading }
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"site_tag":"abc","snippet":"<script></script>","auto_install":false}}""")
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"site_tag":"abc"}]}"""))

        vm.openForm()
        vm.updateForm { it.copy(host = "example.com", autoInstall = false) }
        vm.save()
        val state = vm.uiState.first { it.snippetSite != null }

        assertThat(state.snippetSite?.snippet).isEqualTo("<script></script>")
        server.takeRequest()
        assertThat(server.takeRequest().body.readUtf8()).contains("\"auto_install\":false")
    }

    @Test
    fun `an auto-installed site with no snippet opens no dialog`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = WebAnalyticsViewModel("acct1", WebAnalyticsRepository(testApi(server)))
        vm.uiState.first { it.sites !is UiState.Loading }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"site_tag":"abc","auto_install":true}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"site_tag":"abc"}]}"""))

        vm.openForm()
        vm.updateForm { it.copy(host = "example.com") }
        vm.save()
        vm.uiState.first { it.form == null && !it.isRefreshing }

        assertThat(vm.uiState.value.snippetSite).isNull()
    }
}
