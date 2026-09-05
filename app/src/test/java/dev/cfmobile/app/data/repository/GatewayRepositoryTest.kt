package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.GatewayRuleCreate
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class GatewayRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: GatewayRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = GatewayRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listRules hits the account-level gateway rules endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"rule1","name":"Block malware","action":"block"}]}"""))

        val result = repository.listRules("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().name).isEqualTo("Block malware")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/gateway/rules")
    }

    @Test
    fun `createRule sends the traffic expression and action`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rule2","name":"Block malware","action":"block"}}"""))

        val result = repository.createRule(
            "acct1",
            GatewayRuleCreate(name = "Block malware", action = "block", traffic = "dns.fqdn == \"malware.example.com\"")
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body.readUtf8()).contains("\"traffic\":\"dns.fqdn == \\\"malware.example.com\\\"\"")
    }

    @Test
    fun `deleteRule maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":14003,"message":"Rule not found"}],"result":null}""")
        )

        val result = repository.deleteRule("acct1", "missing")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Rule not found")
    }
}
