package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Endpoint and shape coverage for the account-level products added together. */
class AccountProductsRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listTokens hits the user-scoped tokens endpoint and never receives a value`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"t1","name":"ci","status":"active","last_used_on":"2026-01-02"}]}"""
            )
        )

        val result = ApiTokensRepository(testApi(server)).listTokens()

        val token = (result as ApiResult.Success).data.single()
        assertThat(token.lastUsedOn).isEqualTo("2026-01-02")
        assertThat(server.takeRequest().path).isEqualTo("/user/tokens")
    }

    @Test
    fun `listNotificationPolicies parses the mechanisms block`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"p1","name":"SSL","enabled":true,"alert_type":"universal_ssl_event_type",
                    "mechanisms":{"email":[{"id":"ops@example.com"}]}}]}"""
            )
        )

        val result = NotificationsRepository(testApi(server)).listPolicies("acct1")

        val policy = (result as ApiResult.Success).data.single()
        assertThat(policy.alertType).isEqualTo("universal_ssl_event_type")
        assertThat(policy.mechanisms?.email).hasSize(1)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/alerting/v3/policies")
    }

    @Test
    fun `setEnabled patches the policy with just that field`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"p1","name":"SSL","enabled":false}}"""))

        NotificationsRepository(testApi(server)).setEnabled("acct1", "p1", false)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.path).isEqualTo("/accounts/acct1/alerting/v3/policies/p1")
        assertThat(request.body.readUtf8()).isEqualTo("""{"enabled":false}""")
    }

    @Test
    fun `listRulesLists parses a redirect list's item count`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"l1","name":"redirects","kind":"redirect","num_items":7}]}""")
        )

        val result = BulkRedirectsRepository(testApi(server)).listLists("acct1")

        assertThat((result as ApiResult.Success).data.single().numItems).isEqualTo(7)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/rules/lists")
    }

    @Test
    fun `listRulesListItems parses a redirect entry`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"i1","redirect":{"source_url":"a.com/x","target_url":"https://b.com/y","status_code":302}}]}"""
            )
        )

        val result = BulkRedirectsRepository(testApi(server)).listItems("acct1", "l1")

        val redirect = (result as ApiResult.Success).data.single().redirect
        assertThat(redirect?.targetUrl).isEqualTo("https://b.com/y")
        assertThat(redirect?.statusCode).isEqualTo(302)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/rules/lists/l1/items")
    }

    @Test
    fun `listRegistrarDomains parses the registration details`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"d1","name":"example.com","auto_renew":true,"locked":true,"expires_at":"2027-03-01"}]}"""
            )
        )

        val result = RegistrarRepository(testApi(server)).listDomains("acct1")

        val domain = (result as ApiResult.Success).data.single()
        assertThat(domain.autoRenew).isTrue()
        assertThat(domain.expiresAt).isEqualTo("2027-03-01")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/registrar/domains")
    }

    @Test
    fun `listRumSites parses the site tag, snippet, and zone`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"site_tag":"abc","snippet":"<script></script>","auto_install":false,
                    "ruleset":{"id":"r1","zone_name":"example.com","enabled":true}}]}"""
            )
        )

        val result = WebAnalyticsRepository(testApi(server)).listSites("acct1")

        val site = (result as ApiResult.Success).data.single()
        assertThat(site.siteTag).isEqualTo("abc")
        assertThat(site.ruleset?.zoneName).isEqualTo("example.com")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/rum/site_info/list")
    }

    @Test
    fun `createSite sends the host and install mode`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"site_tag":"abc"}}"""))

        WebAnalyticsRepository(testApi(server)).createSite("acct1", "example.com", autoInstall = true)

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/rum/site_info")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"host\":\"example.com\"")
        assertThat(body).contains("\"auto_install\":true")
    }

    @Test
    fun `deleteSite targets the site tag`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        WebAnalyticsRepository(testApi(server)).deleteSite("acct1", "abc")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/accounts/acct1/rum/site_info/abc")
    }
}
