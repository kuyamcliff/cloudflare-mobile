package dev.cfmobile.app.ui.pages

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.PagesDeployment
import dev.cfmobile.app.data.remote.dto.PagesDeploymentStage
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

    @Test
    fun `isRetryableDeployment only offers a retry on a failed deployment`() {
        fun deployment(status: String?) = PagesDeployment(
            id = "d1",
            latestStage = PagesDeploymentStage(name = "deploy", status = status)
        )

        assertThat(isRetryableDeployment(deployment("failure"))).isTrue()
        assertThat(isRetryableDeployment(deployment("canceled"))).isTrue()
        assertThat(isRetryableDeployment(deployment("success"))).isFalse()
        assertThat(isRetryableDeployment(deployment(null))).isFalse()
        assertThat(isRetryableDeployment(PagesDeployment(id = "d1"))).isFalse()
    }

    @Test
    fun `deploy posts a new deployment and reloads the history`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-site"}]}"""))
        val vm = viewModel()
        val project = (vm.awaitLoaded().projects as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        vm.selectProject(project)
        vm.uiState.first { it.deployments is UiState.Data }

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"new"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"new","environment":"production"}]}"""))

        vm.deploy()
        val state = vm.uiState.first { (it.deployments as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.deployments as UiState.Data).value.single().id).isEqualTo("new")
        assertThat(state.deployingProject).isNull()
        assertThat(state.deployError).isNull()
        assertThat(state.deployMessage).isNotNull()
    }

    @Test
    fun `a rejected deploy reports the error and leaves the history alone`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-site"}]}"""))
        val vm = viewModel()
        val project = (vm.awaitLoaded().projects as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"old"}]}"""))
        vm.selectProject(project)
        vm.uiState.first { it.deployments is UiState.Data }

        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":8000007,"message":"No source connected"}],"result":null}""")
        )

        vm.deploy()
        val state = vm.uiState.first { it.deployError != null }

        assertThat(state.deployError).contains("No source connected")
        assertThat(state.deployingProject).isNull()
        assertThat((state.deployments as UiState.Data).value.single().id).isEqualTo("old")
    }

    @Test
    fun `retry targets the deployment's retry endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-site"}]}"""))
        val vm = viewModel()
        val project = (vm.awaitLoaded().projects as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"dep1"}]}"""))
        vm.selectProject(project)
        val loaded = vm.uiState.first { it.deployments is UiState.Data }
        val deployment = (loaded.deployments as UiState.Data).value.single()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"dep2"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"dep2"}]}"""))

        vm.retry(deployment)
        vm.uiState.first { (it.deployments as? UiState.Data)?.value?.single()?.id == "dep2" }

        server.takeRequest()
        server.takeRequest()
        assertThat(server.takeRequest().path)
            .isEqualTo("/accounts/acct1/pages/projects/my-site/deployments/dep1/retry")
    }

    @Test
    fun `deploy does nothing when no project sheet is open`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-site"}]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.deploy()

        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }
}
