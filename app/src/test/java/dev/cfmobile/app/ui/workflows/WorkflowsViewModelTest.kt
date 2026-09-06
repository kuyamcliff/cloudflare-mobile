package dev.cfmobile.app.ui.workflows

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.WorkflowsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WorkflowsViewModelTest {

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

    private fun viewModel() = WorkflowsViewModel("acct1", WorkflowsRepository(testApi(server)))

    private suspend fun WorkflowsViewModel.awaitLoaded() = uiState.first { it.workflows !is UiState.Loading }

    @Test
    fun `workflowStatusTone groups Cloudflare's status strings and tolerates unknown ones`() {
        assertThat(workflowStatusTone("complete")).isEqualTo("success")
        assertThat(workflowStatusTone("errored")).isEqualTo("error")
        assertThat(workflowStatusTone("running")).isEqualTo("pending")
        // A status Cloudflare adds later must render as neutral rather than crash or be
        // mislabelled as healthy.
        assertThat(workflowStatusTone("something-new")).isEqualTo("neutral")
        assertThat(workflowStatusTone(null)).isEqualTo("neutral")
    }

    @Test
    fun `loads workflows on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"w1","name":"orders"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.workflows as UiState.Data).value.map { it.name }).containsExactly("orders")
    }

    @Test
    fun `selectWorkflow loads that workflow's instances`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"w1","name":"orders"}]}"""))
        val vm = viewModel()
        val loaded = vm.awaitLoaded()
        val workflow = (loaded.workflows as UiState.Data).value.single()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"i1","status":"complete"}]}"""))
        vm.selectWorkflow(workflow)
        val state = vm.uiState.first { it.instances is UiState.Data }

        assertThat(vm.uiState.value.selectedWorkflowName).isEqualTo("orders")
        assertThat((state.instances as UiState.Data).value.map { it.id }).containsExactly("i1")
    }

    @Test
    fun `closeInstances clears the selection`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"w1","name":"orders"}]}"""))
        val vm = viewModel()
        val loaded = vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        vm.selectWorkflow((loaded.workflows as UiState.Data).value.single())
        vm.uiState.first { it.instances is UiState.Data }

        vm.closeInstances()

        assertThat(vm.uiState.value.selectedWorkflowName).isNull()
        assertThat(vm.uiState.value.instances).isNull()
    }
}
