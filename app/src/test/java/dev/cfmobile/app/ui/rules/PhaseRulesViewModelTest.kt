package dev.cfmobile.app.ui.rules

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.RulesetPhaseRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Behaviour shared by every single-phase rules screen, exercised through Redirect Rules. */
class PhaseRulesViewModelTest {

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

    private fun viewModel() = RedirectRulesViewModel("zone1", RulesetPhaseRepository(testApi(server)))

    private suspend fun RedirectRulesViewModel.awaitLoaded() = uiState.first { it.rules !is UiState.Loading }

    private fun rulesetBody(vararg ruleIds: String, enabled: Boolean = true) = """
        {"success":true,"errors":[],"result":{"id":"ruleset1","rules":[
        ${ruleIds.joinToString(",") { """{"id":"$it","action":"redirect","expression":"true","enabled":$enabled,
            "action_parameters":{"from_value":{"status_code":301,"target_url":{"value":"https://a.com"}}}}""" }}
        ]}}
    """.trimIndent()

    @Test
    fun `a phase with no ruleset yet is an empty list, not an error`() = runTest {
        // Cloudflare 404s the entrypoint until the zone's first rule in this phase exists.
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[{"code":1000,"message":"not found"}],"result":null}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.rules as UiState.Data).value).isEmpty()
        assertThat(state.rulesetId).isNull()
    }

    @Test
    fun `loads the phase's rules on init`() = runTest {
        server.enqueue(MockResponse().setBody(rulesetBody("r1")))

        val state = viewModel().awaitLoaded()

        assertThat((state.rules as UiState.Data).value.map { it.id }).containsExactly("r1")
        assertThat(state.rulesetId).isEqualTo("ruleset1")
        assertThat(server.takeRequest().path)
            .isEqualTo("/zones/zone1/rulesets/phases/http_request_dynamic_redirect/entrypoint")
    }

    @Test
    fun `the first rule in an empty phase creates the entrypoint with a PUT`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody(rulesetBody("r1")))

        vm.openCreateForm()
        vm.updateForm { it.copy(target = "https://a.com") }
        vm.save()
        val state = vm.uiState.first { it.form == null && (it.rules as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat(state.rulesetId).isEqualTo("ruleset1")
        server.takeRequest()
        val write = server.takeRequest()
        assertThat(write.method).isEqualTo("PUT")
        assertThat(write.path).isEqualTo("/zones/zone1/rulesets/phases/http_request_dynamic_redirect/entrypoint")
    }

    @Test
    fun `a later rule is appended rather than replacing the ruleset`() = runTest {
        server.enqueue(MockResponse().setBody(rulesetBody("r1")))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody(rulesetBody("r1", "r2")))

        vm.openCreateForm()
        vm.updateForm { it.copy(target = "https://b.com") }
        vm.save()
        vm.uiState.first { (it.rules as? UiState.Data)?.value?.size == 2 }

        server.takeRequest()
        val write = server.takeRequest()
        assertThat(write.method).isEqualTo("POST")
        assertThat(write.path).isEqualTo("/zones/zone1/rulesets/ruleset1/rules")
    }

    @Test
    fun `editing a rule patches it in place`() = runTest {
        server.enqueue(MockResponse().setBody(rulesetBody("r1")))
        val vm = viewModel()
        val loaded = vm.awaitLoaded()
        val rule = (loaded.rules as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody(rulesetBody("r1")))

        vm.openEditForm(rule)
        vm.updateForm { it.copy(target = "https://c.com") }
        vm.save()
        vm.uiState.first { it.form == null }

        server.takeRequest()
        val write = server.takeRequest()
        assertThat(write.method).isEqualTo("PATCH")
        assertThat(write.path).isEqualTo("/zones/zone1/rulesets/ruleset1/rules/r1")
    }

    @Test
    fun `an invalid form never reaches the network`() = runTest {
        server.enqueue(MockResponse().setBody(rulesetBody("r1")))
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.openCreateForm()
        vm.save()

        assertThat(vm.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `a rejected save keeps the form open with Cloudflare's message`() = runTest {
        server.enqueue(MockResponse().setBody(rulesetBody("r1")))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":20037,"message":"expression is invalid"}],"result":null}""")
        )

        vm.openCreateForm()
        vm.updateForm { it.copy(target = "https://a.com", expression = "nonsense") }
        vm.save()
        val form = vm.uiState.first { state -> state.form.let { it != null && !it.isSaving && it.error != null } }.form

        assertThat(form?.error).contains("expression is invalid")
    }

    @Test
    fun `toggling a rule preserves everything else about it`() = runTest {
        server.enqueue(MockResponse().setBody(rulesetBody("r1", enabled = true)))
        val vm = viewModel()
        val rule = (vm.awaitLoaded().rules as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody(rulesetBody("r1", enabled = false)))

        vm.setEnabled(rule, false)
        vm.uiState.first { (it.rules as? UiState.Data)?.value?.single()?.enabled == false }

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"enabled\":false")
        assertThat(body).contains("\"target_url\"")
    }

    @Test
    fun `delete uses the ruleset returned by the delete instead of re-fetching`() = runTest {
        server.enqueue(MockResponse().setBody(rulesetBody("r1")))
        val vm = viewModel()
        val rule = (vm.awaitLoaded().rules as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"ruleset1","rules":[]}}"""))

        vm.delete(rule)
        val state = vm.uiState.first { it.deletingId == null && (it.rules as? UiState.Data)?.value?.isEmpty() == true }

        assertThat((state.rules as UiState.Data).value).isEmpty()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `deleting is a no-op when the phase has no ruleset`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.delete(RulesetRule(id = "ghost"))

        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }
}
