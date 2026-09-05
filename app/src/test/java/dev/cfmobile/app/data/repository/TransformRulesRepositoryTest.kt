package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.dto.TransformActionParameters
import dev.cfmobile.app.data.remote.dto.UriRewrite
import dev.cfmobile.app.data.remote.dto.UriRewritePart
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class TransformRulesRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TransformRulesRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = TransformRulesRepository(testApi(server))
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
            actionParameters = TransformActionParameters(uri = UriRewrite(path = UriRewritePart(value = "/new")))
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
}
