package dev.cfmobile.app.ui.diagnostics

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.TraceResult
import dev.cfmobile.app.data.remote.dto.TraceStep
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.DiagnosticsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TracerViewModelTest {

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

    private fun viewModel() = TracerViewModel("acct1", DiagnosticsRepository(testApi(server)))

    @Test
    fun `a trace needs a full URL, scheme included`() {
        assertThat(validateTraceUrl("")).contains("required")
        assertThat(validateTraceUrl("example.com/path")).contains("scheme")
        assertThat(validateTraceUrl("https://example.com/path")).isNull()
        assertThat(validateTraceUrl("http://example.com")).isNull()
    }

    @Test
    fun `step labels are readable without knowing every step type`() {
        assertThat(traceStepLabel(TraceStep(stepName = "rate_limit"))).isEqualTo("Rate Limit")
        assertThat(traceStepLabel(TraceStep(trace = "waf_managed_rules"))).isEqualTo("Waf Managed Rules")
        assertThat(traceStepLabel(TraceStep(step = 3))).isEqualTo("Step 3")
    }

    @Test
    fun `traceStepSummary says whether the step fired`() {
        assertThat(traceStepSummary(TraceStep(matched = true, action = "block"))).isEqualTo("matched · block")
        assertThat(traceStepSummary(TraceStep(matched = false))).isEqualTo("not matched")
    }

    @Test
    fun `matchedSteps picks out only the rules that fired`() {
        val result = TraceResult(
            trace = listOf(
                TraceStep(stepName = "waf", matched = true),
                TraceStep(stepName = "cache", matched = false),
                TraceStep(stepName = "worker", matched = true)
            )
        )

        assertThat(matchedSteps(result).map { it.stepName }).containsExactly("waf", "worker").inOrder()
        assertThat(matchedSteps(null)).isEmpty()
    }

    @Test
    fun `a trace posts the url and method to the tracer endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"status_code":403,"trace":[{"step":1,"step_name":"waf","matched":true,"action":"block"}]}}"""
            )
        )
        val vm = viewModel()

        vm.updateUrl("https://example.com/checkout")
        vm.selectMethod(TraceMethod.POST)
        vm.trace()
        val state = vm.uiState.first { it.result != null }

        assertThat(state.result?.statusCode).isEqualTo(403)
        assertThat(state.tracedUrl).isEqualTo("https://example.com/checkout")
        assertThat(state.isTracing).isFalse()
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/request-tracer/trace")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"url\":\"https://example.com/checkout\"")
        assertThat(body).contains("\"method\":\"POST\"")
    }

    @Test
    fun `an invalid url never reaches the network`() = runTest {
        val vm = viewModel()

        vm.updateUrl("example.com")
        vm.trace()

        assertThat(server.requestCount).isEqualTo(0)
        assertThat(vm.uiState.value.error).contains("scheme")
    }

    @Test
    fun `a failed trace clears the previous result rather than showing a stale one`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"status_code":200,"trace":[]}}""")
        )
        val vm = viewModel()
        vm.updateUrl("https://example.com")
        vm.trace()
        vm.uiState.first { it.result != null }

        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":1000,"message":"Zone not on this account"}],"result":null}""")
        )
        vm.updateUrl("https://other.example")
        vm.trace()
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("Zone not on this account")
        assertThat(state.result).isNull()
        assertThat(state.tracedUrl).isNull()
    }
}
