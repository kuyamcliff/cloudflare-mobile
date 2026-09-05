package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class WafRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: WafRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = WafRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getCustomRuleset returns the ruleset when one exists`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"rs1","phase":"http_request_firewall_custom","rules":[
                    {"id":"r1","action":"block","expression":"ip.src eq 203.0.113.5","enabled":true}
                ]}}"""
            )
        )

        val result = repository.getCustomRuleset("zone1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val ruleset = (result as ApiResult.Success).data
        assertThat(ruleset).isNotNull()
        assertThat(ruleset!!.rules).hasSize(1)
    }

    @Test
    fun `getCustomRuleset treats a 404 as no ruleset yet, not a failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Ruleset not found"}],"result":null}""")
        )

        val result = repository.getCustomRuleset("zone1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isNull()
    }

    @Test
    fun `getCustomRuleset still surfaces a genuine Failure for a non-404 error`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Invalid API token"}],"result":null}""")
        )

        val result = repository.getCustomRuleset("zone1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
    }

    @Test
    fun `addRule with no existing ruleset PUTs the entrypoint to create one`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1","action":"block","expression":"ip.src eq 1.1.1.1"}]}}"""))

        val result = repository.addRule("zone1", existingRulesetId = null, rule = RulesetRuleWrite(action = "block", expression = "ip.src eq 1.1.1.1"))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/zones/zone1/rulesets/phases/http_request_firewall_custom/entrypoint")
    }

    @Test
    fun `addRule with an existing ruleset POSTs to append a rule`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1"},{"id":"r2"}]}}"""))

        val result = repository.addRule("zone1", existingRulesetId = "rs1", rule = RulesetRuleWrite(action = "block", expression = "ip.src eq 1.1.1.1"))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/zones/zone1/rulesets/rs1/rules")
    }

    @Test
    fun `updateRule PATCHes the specific rule`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1","enabled":false}]}}"""))

        val result = repository.updateRule("zone1", "rs1", "r1", RulesetRuleWrite(action = "block", expression = "ip.src eq 1.1.1.1", enabled = false))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.path).isEqualTo("/zones/zone1/rulesets/rs1/rules/r1")
    }

    @Test
    fun `deleteRule maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Rule not found"}],"result":null}""")
        )

        val result = repository.deleteRule("zone1", "rs1", "missing")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Rule not found")
    }
}
