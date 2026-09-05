package dev.cfmobile.app.ui.pages

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.PagesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PagesViewModelTest {

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

    private fun viewModel() = PagesViewModel("acct1", PagesRepository(testApi(server)))

    private suspend fun PagesViewModel.awaitLoaded() = uiState.first { it.projects !is UiState.Loading }

    @Test
    fun `loads projects on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-site"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.projects as UiState.Data).value.map { it.name }).containsExactly("my-site")
    }

    @Test
    fun `selectProject loads its deployment history`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-site"}]}"""))
        val vm = viewModel()
        val loaded = vm.awaitLoaded()
        val project = (loaded.projects as UiState.Data).value.single()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"dep1","environment":"production"}]}"""))
        vm.selectProject(project)
        val state = vm.uiState.first { it.deployments is UiState.Data }

        assertThat(vm.uiState.value.selectedProjectName).isEqualTo("my-site")
        assertThat((state.deployments as UiState.Data).value.map { it.id }).containsExactly("dep1")
    }

    @Test
    fun `closeDeployments clears the selection`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-site"}]}"""))
        val vm = viewModel()
        val loaded = vm.awaitLoaded()
        val project = (loaded.projects as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        vm.selectProject(project)
        vm.uiState.first { it.deployments is UiState.Data }

        vm.closeDeployments()

        assertThat(vm.uiState.value.selectedProjectName).isNull()
        assertThat(vm.uiState.value.deployments).isNull()
    }
}
