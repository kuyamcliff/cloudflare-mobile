package dev.cfmobile.app.ui.ratelimit

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.RateLimit
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.RateLimitRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RateLimitViewModelTest {

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

    private suspend fun RateLimitViewModel.awaitLoaded() = uiState.first { it.rules !is UiState.Loading }

    @Test
    fun `an empty ruleset (404) loads as zero rules, not an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = RateLimitViewModel("zone1", RateLimitRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat(state.rules).isInstanceOf(UiState.Data::class.java)
        assertThat((state.rules as UiState.Data).value).isEmpty()
    }

    @Test
    fun `save rejects a blank expression before calling the network`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = RateLimitViewModel("zone1", RateLimitRepository(testApi(server)))
        viewModel.awaitLoaded()
        val requestsBefore = server.requestCount

        viewModel.openAddForm()
        viewModel.save()

        assertThat(viewModel.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `save rejects a non-positive requests-per-period`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = RateLimitViewModel("zone1", RateLimitRepository(testApi(server)))
        viewModel.awaitLoaded()

        viewModel.openAddForm()
        viewModel.updateForm { it.copy(expression = "true", requestsPerPeriod = "0") }
        viewModel.save()

        assertThat(viewModel.uiState.value.form?.error).contains("positive")
    }

    @Test
    fun `saving a valid rule creates it and closes the form`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = RateLimitViewModel("zone1", RateLimitRepository(testApi(server)))
        viewModel.awaitLoaded()

        viewModel.openAddForm()
        viewModel.updateForm { it.copy(expression = "http.request.uri.path eq \"/login\"", requestsPerPeriod = "5") }
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1","action":"block","expression":"http.request.uri.path eq \"/login\"","ratelimit":{"period":60,"requests_per_period":5}}]}}"""
            )
        )

        viewModel.save()
        val state = viewModel.uiState.first { it.form == null }

        assertThat((state.rules as UiState.Data).value).hasSize(1)
    }

    @Test
    fun `editing an existing rule pre-fills its rate limit fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = RateLimitViewModel("zone1", RateLimitRepository(testApi(server)))
        viewModel.awaitLoaded()
        val rule = RulesetRule(
            id = "r1", action = "log", expression = "true",
            ratelimit = RateLimit(period = 3600, requestsPerPeriod = 200, mitigationTimeout = 120)
        )

        viewModel.openEditForm(rule)

        val form = viewModel.uiState.value.form!!
        assertThat(form.period).isEqualTo(3600)
        assertThat(form.requestsPerPeriod).isEqualTo("200")
        assertThat(form.mitigationTimeout).isEqualTo("120")
    }
}
