package dev.cfmobile.app.ui.transformrules

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.TransformActionParameters
import dev.cfmobile.app.data.remote.dto.UriRewrite
import dev.cfmobile.app.data.remote.dto.UriRewritePart
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.TransformRulesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TransformRulesViewModelTest {

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

    private suspend fun TransformRulesViewModel.awaitLoaded() = uiState.first { it.activeState.rules !is UiState.Loading }

    @Test
    fun `loads the URL Rewrite tab by default`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = TransformRulesViewModel("zone1", TransformRulesRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat(state.selectedKind).isEqualTo(TransformRuleKind.URL_REWRITE)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/rulesets/phases/http_request_transform/entrypoint")
    }

    @Test
    fun `switching tabs loads that phase's ruleset only once`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = TransformRulesViewModel("zone1", TransformRulesRepository(testApi(server)))
        viewModel.awaitLoaded()

        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        viewModel.selectKind(TransformRuleKind.REQUEST_HEADERS)
        val state = viewModel.uiState.first { it.selectedKind == TransformRuleKind.REQUEST_HEADERS && it.activeState.loaded }

        assertThat(state.states.getValue(TransformRuleKind.REQUEST_HEADERS).rules).isInstanceOf(UiState.Data::class.java)
        assertThat(server.requestCount).isEqualTo(2) // initial load + one tab switch

        // Switching back to an already-loaded tab makes no new request.
        viewModel.selectKind(TransformRuleKind.URL_REWRITE)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `save rejects an incomplete URL rewrite before calling the network`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = TransformRulesViewModel("zone1", TransformRulesRepository(testApi(server)))
        viewModel.awaitLoaded()
        val requestsBefore = server.requestCount

        viewModel.openAddForm()
        viewModel.save()

        assertThat(viewModel.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `saving a valid URL rewrite creates it and closes the form`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = TransformRulesViewModel("zone1", TransformRulesRepository(testApi(server)))
        viewModel.awaitLoaded()

        viewModel.openAddForm()
        viewModel.updateForm { it.copy(pathValue = "/new-path") }
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1","action":"rewrite","expression":"true"}]}}"""
            )
        )

        viewModel.save()
        val state = viewModel.uiState.first { it.form == null }

        assertThat((state.activeState.rules as UiState.Data).value).hasSize(1)
    }

    @Test
    fun `editing a header rule pre-fills its name, operation, and value`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = TransformRulesViewModel("zone1", TransformRulesRepository(testApi(server)))
        viewModel.awaitLoaded()
        viewModel.selectKind(TransformRuleKind.REQUEST_HEADERS)
        val rule = RulesetRule(
            id = "r1", action = "rewrite", expression = "true",
            actionParameters = TransformActionParameters(headers = mapOf("X-Foo" to dev.cfmobile.app.data.remote.dto.HeaderModification(operation = "set", value = "bar")))
        )

        viewModel.openEditForm(rule)

        val form = viewModel.uiState.value.form!!
        assertThat(form.headerName).isEqualTo("X-Foo")
        assertThat(form.headerOperation).isEqualTo("set")
        assertThat(form.headerValue).isEqualTo("bar")
        assertThat(form.headerIsExpression).isFalse()
    }

    @Test
    fun `editing a URL rewrite rule pre-fills path and query`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = TransformRulesViewModel("zone1", TransformRulesRepository(testApi(server)))
        viewModel.awaitLoaded()
        val rule = RulesetRule(
            id = "r1", action = "rewrite", expression = "true",
            actionParameters = TransformActionParameters(uri = UriRewrite(path = UriRewritePart(expression = "concat(\"/x\", http.request.uri.path)")))
        )

        viewModel.openEditForm(rule)

        val form = viewModel.uiState.value.form!!
        assertThat(form.pathValue).isEqualTo("concat(\"/x\", http.request.uri.path)")
        assertThat(form.pathIsExpression).isTrue()
    }
}
