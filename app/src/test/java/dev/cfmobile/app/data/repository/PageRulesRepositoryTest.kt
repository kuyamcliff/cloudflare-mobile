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

class PageRulesRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: PageRulesRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = PageRulesRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `createRule builds a single-target single-action page rule`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"pr1","targets":[{"target":"url","constraint":{"operator":"matches","value":"example.com/admin/*"}}],"actions":[{"id":"always_use_https"}],"priority":1,"status":"active"}}"""
            )
        )

        val result = repository.createRule("zone1", "example.com/admin/*", "always_use_https", "on", 1)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("example.com/admin/*")
        assertThat(body).contains("always_use_https")
    }

    @Test
    fun `setStatus toggles a rule between active and disabled while keeping its actions`() = runBlocking {
        val existing = dev.cfmobile.app.data.remote.dto.PageRule(
            id = "pr1",
            targets = listOf(dev.cfmobile.app.data.remote.dto.PageRuleTarget()),
            actions = listOf(dev.cfmobile.app.data.remote.dto.PageRuleAction(id = "cache_level", value = "bypass")),
            priority = 1,
            status = "active"
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"pr1","status":"disabled"}}"""))

        val result = repository.setStatus("zone1", existing, active = false)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.body.readUtf8()).contains("\"status\":\"disabled\"")
    }
}
