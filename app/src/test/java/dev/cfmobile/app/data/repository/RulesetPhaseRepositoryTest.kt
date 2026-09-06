package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.dto.RuleActionParameters
import dev.cfmobile.app.data.remote.dto.UriRewrite
import dev.cfmobile.app.data.remote.dto.UriRewritePart
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class RulesetPhaseRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: RulesetPhaseRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = RulesetPhaseRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getRuleset uses the phase passed in, not a fixed one`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[]}}"""))

        repository.getRuleset("zone1", "http_request_late_transform")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/rulesets/phases/http_request_late_transform/entrypoint")
    }

    @Test
    fun `getRuleset treats a 404 as no ruleset yet`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))

        val result = repository.getRuleset("zone1", "http_request_transform")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isNull()
    }

    @Test
    fun `addRule with no existing ruleset PUTs the entrypoint for that phase`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1"}]}}"""))
        val rule = RulesetRuleWrite(
            action = "rewrite", expression = "true",
            actionParameters = RuleActionParameters(uri = UriRewrite(path = UriRewritePart(value = "/new")))
        )

        val result = repository.addRule("zone1", "http_request_transform", existingRulesetId = null, rule = rule)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/zones/zone1/rulesets/phases/http_request_transform/entrypoint")
        assertThat(request.body.readUtf8()).contains("\"path\":{\"value\":\"/new\"}")
    }

    @Test
    fun `addRule with an existing ruleset POSTs to append a rule regardless of phase`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1"},{"id":"r2"}]}}"""))

        val result = repository.addRule(
            "zone1", "http_response_headers_transform", existingRulesetId = "rs1",
            rule = RulesetRuleWrite(action = "rewrite", expression = "true")
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/zones/zone1/rulesets/rs1/rules")
    }

    @Test
    fun `listRulesets returns the zone's rulesets with their kind and phase`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[
                    {"id":"ms1","name":"Cloudflare Managed Ruleset","kind":"managed","phase":"http_request_firewall_managed"},
                    {"id":"z1","name":"zone entrypoint","kind":"zone","phase":"http_request_firewall_custom"}
                ]}"""
            )
        )

        val result = RulesetPhaseRepository(testApi(server)).listRulesets("zone1")

        val rulesets = (result as ApiResult.Success).data
        assertThat(rulesets.map { it.kind }).containsExactly("managed", "zone").inOrder()
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/rulesets")
    }
}
