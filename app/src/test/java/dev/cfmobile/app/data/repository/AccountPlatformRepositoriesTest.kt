package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AiGatewayWrite
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Endpoint coverage for the account platform products: AI Gateway, Calls, Pipelines, the
 *  Secrets Store, DNS Firewall, notification destinations, and account-owned API tokens. */
class AccountPlatformRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `an AI gateway create sends every setting Cloudflare requires`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"g1","cache_ttl":60}}"""))

        val result = AiGatewayRepository(testApi(server)).createGateway(
            "acct1",
            AiGatewayWrite(id = "g1", cacheTtl = 60, collectLogs = true, rateLimitingLimit = 10, rateLimitingInterval = 60)
        )

        assertThat((result as ApiResult.Success).data.cacheTtl).isEqualTo(60)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/ai-gateway/gateways")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"collect_logs\":true")
        // Cloudflare rejects a create that omits the technique, so it always goes out.
        assertThat(body).contains("\"rate_limiting_technique\":\"fixed\"")
    }

    @Test
    fun `deleting a gateway succeeds even though Cloudflare echoes the deleted object`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"g1"}}"""))

        val result = AiGatewayRepository(testApi(server)).deleteGateway("acct1", "g1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/ai-gateway/gateways/g1")
    }

    @Test
    fun `a Calls app create returns the secret exactly once`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"uid":"a1","name":"room","secret":"s3cret"}}""")
        )

        val created = CallsRepository(testApi(server)).createApp("acct1", "room")

        assertThat((created as ApiResult.Success).data.secret).isEqualTo("s3cret")
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"name":"room"}""")

        // Reading the app back never carries it again.
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"uid":"a1","name":"room"}]}"""))
        val listed = CallsRepository(testApi(server)).listApps("acct1")
        assertThat((listed as ApiResult.Success).data.single().secret).isNull()
    }

    @Test
    fun `a pipeline is deleted by name`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        val result = PipelinesRepository(testApi(server)).deletePipeline("acct1", "logs")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/pipelines/logs")
    }

    @Test
    fun `a secret store create answers with a list, since Cloudflare accepts several`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"s1","name":"prod"}]}"""))

        val result = SecretsStoreRepository(testApi(server)).createStore("acct1", "prod")

        assertThat((result as ApiResult.Success).data.single().id).isEqualTo("s1")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/secrets_store/stores")
    }

    @Test
    fun `a stored secret is listed by name and status, never by value`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"k1","name":"STRIPE_KEY","status":"active","scopes":["workers"]}]}""")
        )

        val result = SecretsStoreRepository(testApi(server)).listSecrets("acct1", "s1")

        val secret = (result as ApiResult.Success).data.single()
        assertThat(secret.name).isEqualTo("STRIPE_KEY")
        assertThat(secret.scopes).containsExactly("workers")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/secrets_store/stores/s1/secrets")
    }

    @Test
    fun `a DNS Firewall cluster reports the addresses to delegate to`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"c1","name":"eu","dns_firewall_ips":["1.2.3.4"],"upstream_ips":["192.0.2.1"]}}""")
        )

        val result = DnsFirewallRepository(testApi(server)).createCluster("acct1", "eu", listOf("192.0.2.1"))

        assertThat((result as ApiResult.Success).data.dnsFirewallIps).containsExactly("1.2.3.4")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/dns_firewall")
    }

    @Test
    fun `notification destinations and history come from the alerting v3 API`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"w1","name":"ops","url":"https://x.example/h","last_success":"2026-01-02"}]}""")
        )
        val repository = NotificationsRepository(testApi(server))

        val webhooks = repository.listWebhooks("acct1")

        assertThat((webhooks as ApiResult.Success).data.single().lastSuccess).isEqualTo("2026-01-02")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/alerting/v3/destinations/webhooks")

        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"h1","alert_type":"universal_ssl_event_type","sent":"2026-01-02"}]}""")
        )
        val history = repository.listHistory("acct1")
        assertThat((history as ApiResult.Success).data.single().alertType).isEqualTo("universal_ssl_event_type")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/alerting/v3/history")
    }

    @Test
    fun `account-owned tokens are a different collection from the user's own`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"t1","name":"deploy-bot"}]}"""))
        val repository = ApiTokensRepository(testApi(server))

        val result = repository.listAccountTokens("acct1")

        assertThat((result as ApiResult.Success).data.single().name).isEqualTo("deploy-bot")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/tokens")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        repository.deleteAccountToken("acct1", "t1")
        val delete = server.takeRequest()
        assertThat(delete.method).isEqualTo("DELETE")
        assertThat(delete.path).isEqualTo("/accounts/acct1/tokens/t1")
    }

    @Test
    fun `no repository in this batch ever asks Cloudflare for a secret's value`() {
        // A guard against a future "just add a read" - the Secrets Store API has no such
        // endpoint, and neither Calls nor AI Gateway exposes one either.
        val source = java.io.File("src/main/java/dev/cfmobile/app/data/repository")
            .listFiles { file -> file.name.endsWith("Repository.kt") }
            .orEmpty()
            .joinToString("\n") { it.readText() }

        assertThat(source).doesNotContain("/value")
        assertThat(source).doesNotContain("reveal")
    }
}
