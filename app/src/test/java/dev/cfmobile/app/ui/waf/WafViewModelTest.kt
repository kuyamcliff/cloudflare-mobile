package dev.cfmobile.app.ui.waf

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.WafRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WafViewModelTest {

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

    private suspend fun WafViewModel.awaitLoaded() = uiState.first { it.rules !is UiState.Loading }

    @Test
    fun `an empty ruleset (404) loads as zero rules, not an error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Ruleset not found"}],"result":null}""")
        )
        val viewModel = WafViewModel("zone1", WafRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat(state.rules).isInstanceOf(UiState.Data::class.java)
        assertThat((state.rules as UiState.Data).value).isEmpty()
        assertThat(state.rulesetId).isNull()
    }

    @Test
    fun `loads existing rules and remembers the ruleset id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1","action":"block","expression":"ip.src eq 1.1.1.1","enabled":true}]}}"""))
        val viewModel = WafViewModel("zone1", WafRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat((state.rules as UiState.Data).value).hasSize(1)
        assertThat(state.rulesetId).isEqualTo("rs1")
    }

    @Test
    fun `save rejects a blank expression before calling the network`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = WafViewModel("zone1", WafRepository(testApi(server)))
        viewModel.awaitLoaded()
        val requestsBefore = server.requestCount

        viewModel.openAddForm()
        viewModel.save()

        assertThat(viewModel.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `saving the first rule on a zone with no ruleset creates one and closes the form`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = WafViewModel("zone1", WafRepository(testApi(server)))
        viewModel.awaitLoaded()

        viewModel.openAddForm()
        viewModel.updateForm { it.copy(expression = "ip.src eq 203.0.113.5") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1","action":"block","expression":"ip.src eq 203.0.113.5"}]}}"""))

        viewModel.save()
        val state = viewModel.uiState.first { it.form == null }

        assertThat(state.rulesetId).isEqualTo("rs1")
        assertThat((state.rules as UiState.Data).value).hasSize(1)
    }

    @Test
    fun `editing an existing rule pre-fills the form`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val viewModel = WafViewModel("zone1", WafRepository(testApi(server)))
        viewModel.awaitLoaded()
        val rule = RulesetRule(id = "r1", action = "challenge", expression = "ip.src eq 1.1.1.1", description = "Suspicious IP", enabled = false)

        viewModel.openEditForm(rule)

        val form = viewModel.uiState.value.form!!
        assertThat(form.editingId).isEqualTo("r1")
        assertThat(form.action).isEqualTo("challenge")
        assertThat(form.enabled).isFalse()
    }

    @Test
    fun `toggleEnabled flips the rule and updates state from the response`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1","action":"block","expression":"ip.src eq 1.1.1.1","enabled":true}]}}"""))
        val viewModel = WafViewModel("zone1", WafRepository(testApi(server)))
        val loaded = viewModel.awaitLoaded()
        val rule = (loaded.rules as UiState.Data).value.first()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1","action":"block","expression":"ip.src eq 1.1.1.1","enabled":false}]}}"""))
        viewModel.toggleEnabled(rule)
        val state = viewModel.uiState.first { (it.rules as? UiState.Data)?.value?.firstOrNull()?.enabled == false }

        assertThat((state.rules as UiState.Data).value.first().enabled).isFalse()
    }

    @Test
    fun `delete removes the rule via the remembered ruleset id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[{"id":"r1","action":"block","expression":"ip.src eq 1.1.1.1"}]}}"""))
        val viewModel = WafViewModel("zone1", WafRepository(testApi(server)))
        val loaded = viewModel.awaitLoaded()
        val rule = (loaded.rules as UiState.Data).value.first()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"rs1","rules":[]}}"""))
        viewModel.delete(rule)
        val state = viewModel.uiState.first { (it.rules as? UiState.Data)?.value?.isEmpty() == true }

        assertThat((state.rules as UiState.Data).value).isEmpty()
        server.takeRequest() // the initial GET
        val deleteRequest = server.takeRequest()
        assertThat(deleteRequest.method).isEqualTo("DELETE")
        assertThat(deleteRequest.path).isEqualTo("/zones/zone1/rulesets/rs1/rules/r1")
    }
}
