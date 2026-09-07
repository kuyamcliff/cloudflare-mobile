package dev.cfmobile.app.ui.magicfirewall

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.MagicFirewallRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MagicFirewallViewModelTest {

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

    private fun viewModel() = MagicFirewallViewModel("acct1", MagicFirewallRepository(testApi(server)))

    private suspend fun MagicFirewallViewModel.awaitLoaded() = uiState.first { it.rules !is UiState.Loading }

    private fun ruleset(vararg rules: String) =
        """{"success":true,"errors":[],"result":{"id":"rs1","phase":"magic_transit","rules":[${rules.joinToString(",")}]}}"""

    private val blockRule =
        """{"id":"r1","action":"block","expression":"tcp.dstport == 22","description":"Drop SSH","enabled":true}"""

    @Test
    fun `an unknown action falls back to block rather than crashing`() {
        assertThat(actionFromValue("log")).isEqualTo(MagicFirewallAction.LOG)
        assertThat(actionFromValue("skip")).isEqualTo(MagicFirewallAction.SKIP)
        // Cloudflare could add an action this build doesn't know.
        assertThat(actionFromValue("managed_challenge")).isEqualTo(MagicFirewallAction.BLOCK)
    }

    @Test
    fun `a rule needs both an expression and a description`() {
        assertThat(validateMagicFirewallForm(MagicFirewallFormState())).contains("expression")
        assertThat(validateMagicFirewallForm(MagicFirewallFormState(expression = "ip.src == 1.2.3.4")))
            .contains("description")
        assertThat(
            validateMagicFirewallForm(
                MagicFirewallFormState(expression = "ip.src == 1.2.3.4", description = "Block one host")
            )
        ).isNull()
    }

    @Test
    fun `ruleSummary says what the rule does and whether it is doing it`() {
        val rule = RulesetRule(id = "r1", action = "block", expression = "x", enabled = true)

        assertThat(ruleSummary(rule)).isEqualTo("Block · enabled")
        assertThat(ruleSummary(rule.copy(action = "log", enabled = false))).isEqualTo("Log · disabled")
    }

    @Test
    fun `rules load from the account's magic_transit phase entrypoint`() = runTest {
        server.enqueue(MockResponse().setBody(ruleset(blockRule)))

        val state = viewModel().awaitLoaded()

        assertThat((state.rules as UiState.Data).value.single().description).isEqualTo("Drop SSH")
        assertThat(state.rulesetId).isEqualTo("rs1")
        assertThat(server.takeRequest().path)
            .isEqualTo("/accounts/acct1/rulesets/phases/magic_transit/entrypoint")
    }

    @Test
    fun `an account with no rules yet is an empty list, not an error`() = runTest {
        // Cloudflare has no entrypoint ruleset until the first rule exists.
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":10005,"message":"could not find ruleset"}],"result":null}""")
        )

        val state = viewModel().awaitLoaded()

        assertThat((state.rules as UiState.Data).value).isEmpty()
        assertThat(state.rulesetId).isNull()
    }

    @Test
    fun `the first rule creates the entrypoint with a PUT`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":10005,"message":"could not find ruleset"}],"result":null}""")
        )
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody(ruleset(blockRule)))

        vm.openForm()
        vm.updateForm { it.copy(expression = "tcp.dstport == 22", description = "Drop SSH") }
        vm.save()
        vm.uiState.first { it.form == null }

        server.takeRequest()
        val put = server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.path).isEqualTo("/accounts/acct1/rulesets/phases/magic_transit/entrypoint")
        assertThat(put.body.readUtf8()).contains("\"action\":\"block\"")
        assertThat(vm.uiState.value.rulesetId).isEqualTo("rs1")
    }

    @Test
    fun `a later rule is appended with a POST, not a whole-ruleset replace`() = runTest {
        server.enqueue(MockResponse().setBody(ruleset(blockRule)))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody(ruleset(blockRule)))

        vm.openForm()
        vm.updateForm { it.copy(expression = "udp.dstport == 53", description = "Log DNS", action = MagicFirewallAction.LOG) }
        vm.save()
        vm.uiState.first { it.form == null }

        server.takeRequest()
        val post = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.path).isEqualTo("/accounts/acct1/rulesets/rs1/rules")
        assertThat(post.body.readUtf8()).contains("\"action\":\"log\"")
    }

    @Test
    fun `editing a rule patches it in place`() = runTest {
        server.enqueue(MockResponse().setBody(ruleset(blockRule)))
        val vm = viewModel()
        val rule = (vm.awaitLoaded().rules as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody(ruleset(blockRule)))

        vm.editRule(rule)
        assertThat(vm.uiState.value.form?.expression).isEqualTo("tcp.dstport == 22")
        vm.updateForm { it.copy(description = "Drop SSH from anywhere") }
        vm.save()
        vm.uiState.first { it.form == null }

        server.takeRequest()
        val patch = server.takeRequest()
        assertThat(patch.method).isEqualTo("PATCH")
        assertThat(patch.path).isEqualTo("/accounts/acct1/rulesets/rs1/rules/r1")
    }

    @Test
    fun `the enable switch keeps the rule's expression and action`() = runTest {
        server.enqueue(MockResponse().setBody(ruleset(blockRule)))
        val vm = viewModel()
        val rule = (vm.awaitLoaded().rules as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setBody(
                ruleset("""{"id":"r1","action":"block","expression":"tcp.dstport == 22","description":"Drop SSH","enabled":false}""")
            )
        )

        vm.setEnabled(rule, false)
        val state = vm.uiState.first { (it.rules as? UiState.Data)?.value?.single()?.enabled == false }

        assertThat(state.busyId).isNull()
        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"expression\":\"tcp.dstport == 22\"")
        assertThat(body).contains("\"action\":\"block\"")
        assertThat(body).contains("\"enabled\":false")
    }

    @Test
    fun `deleting a rule uses the list Cloudflare returns rather than refetching`() = runTest {
        server.enqueue(MockResponse().setBody(ruleset(blockRule)))
        val vm = viewModel()
        val rule = (vm.awaitLoaded().rules as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody(ruleset()))

        vm.delete(rule)
        val state = vm.uiState.first { (it.rules as? UiState.Data)?.value?.isEmpty() == true }

        assertThat(state.deletingId).isNull()
        server.takeRequest()
        val delete = server.takeRequest()
        assertThat(delete.method).isEqualTo("DELETE")
        assertThat(delete.path).isEqualTo("/accounts/acct1/rulesets/rs1/rules/r1")
        // Two requests total: the delete response carries the remaining rules.
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `a rejected save keeps the form open with the reason`() = runTest {
        server.enqueue(MockResponse().setBody(ruleset(blockRule)))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":20000,"message":"Invalid expression"}],"result":null}""")
        )

        vm.openForm()
        vm.updateForm { it.copy(expression = "tcp.dstport = 22", description = "Typo") }
        vm.save()
        val state = vm.uiState.first { it.form?.error != null }

        assertThat(state.form?.error).contains("Invalid expression")
        assertThat(state.form?.isSaving).isFalse()
        assertThat(state.form?.expression).isEqualTo("tcp.dstport = 22")
    }
}
