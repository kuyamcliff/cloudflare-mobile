package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RateLimit
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class RateLimitRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: RateLimitRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = RateLimitRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getRuleset treats a 404 as no ruleset yet, not a failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Ruleset not found"}],"result":null}""")
        )

        val result = repository.getRuleset("zone1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isNull()
    }

    @Test
    fun `getRuleset uses the http_ratelimit phase, not the WAF custom rules phase`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[]}}"""))

        repository.getRuleset("zone1")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/rulesets/phases/http_ratelimit/entrypoint")
    }

    @Test
    fun `addRule with no existing ruleset PUTs the entrypoint with the ratelimit object`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1"}]}}"""))
        val rule = RulesetRuleWrite(
            action = "block", expression = "http.request.uri.path eq \"/login\"",
            ratelimit = RateLimit(period = 60, requestsPerPeriod = 5)
        )

        val result = repository.addRule("zone1", existingRulesetId = null, rule = rule)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/zones/zone1/rulesets/phases/http_ratelimit/entrypoint")
        assertThat(request.body.readUtf8()).contains("\"requests_per_period\":5")
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
