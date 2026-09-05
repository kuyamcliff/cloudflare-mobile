package dev.cfmobile.app.ui.access

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.AccessRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccessViewModelTest {

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

    private fun viewModel() = AccessViewModel("acct1", AccessRepository(testApi(server)))

    private suspend fun AccessViewModel.awaitLoaded() = uiState.first { it.applications !is UiState.Loading }

    @Test
    fun `validateAccessForm requires name, domain, and a non-blank rule value`() {
        assertThat(validateAccessForm(AccessFormState())).isEqualTo("Application name is required")
        assertThat(validateAccessForm(AccessFormState(name = "App"))).isEqualTo("Domain is required")
        assertThat(validateAccessForm(AccessFormState(name = "App", domain = "app.example.com"))).contains("email domain")
    }

    @Test
    fun `validateAccessForm rejects a malformed domain`() {
        val form = AccessFormState(name = "App", domain = "app.example.com", ruleType = AccessRuleType.EMAIL_DOMAIN, ruleValue = "not a domain")
        assertThat(validateAccessForm(form)).contains("valid domain")
    }

    @Test
    fun `validateAccessForm rejects malformed email addresses`() {
        val form = AccessFormState(name = "App", domain = "app.example.com", ruleType = AccessRuleType.EMAIL_LIST, ruleValue = "a@example.com, not-an-email")
        assertThat(validateAccessForm(form)).contains("valid")
    }

    @Test
    fun `validateAccessForm accepts a valid domain form`() {
        val form = AccessFormState(name = "App", domain = "app.example.com", ruleType = AccessRuleType.EMAIL_DOMAIN, ruleValue = "example.com")
        assertThat(validateAccessForm(form)).isNull()
    }

    @Test
    fun `buildIncludeRules splits a comma-separated email list into one rule per address`() {
        val form = AccessFormState(ruleType = AccessRuleType.EMAIL_LIST, ruleValue = "a@example.com, b@example.com")
        val rules = buildIncludeRules(form)
        assertThat(rules.map { it.email?.email }).containsExactly("a@example.com", "b@example.com")
    }

    @Test
    fun `loads applications on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"app1","name":"Staging","domain":"staging.example.com"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.applications as UiState.Data).value.map { it.name }).containsExactly("Staging")
    }

    @Test
    fun `save rejects an invalid form before calling the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.openForm()
        vm.save()

        assertThat(vm.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `a valid save creates the application and its policy, then closes the form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openForm()
        vm.updateForm { it.copy(name = "Staging", domain = "staging.example.com", ruleValue = "example.com") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"app1","name":"Staging","domain":"staging.example.com"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"app1","name":"Staging","domain":"staging.example.com"}]}"""))

        vm.save()
        val state = vm.uiState.first { it.form == null && (it.applications as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.applications as UiState.Data).value.map { it.name }).containsExactly("Staging")
    }
}
