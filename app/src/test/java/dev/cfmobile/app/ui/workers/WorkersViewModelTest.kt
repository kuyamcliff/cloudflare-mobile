package dev.cfmobile.app.ui.workers

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.WorkersRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WorkersViewModelTest {

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

    private fun viewModel() = WorkersViewModel("acct1", WorkersRepository(testApi(server)))

    private suspend fun WorkersViewModel.awaitLoaded() = uiState.first { it.scripts !is UiState.Loading }

    @Test
    fun `loads scripts on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"my-script"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.scripts as UiState.Data).value.map { it.id }).containsExactly("my-script")
    }

    @Test
    fun `delete removes the script and refreshes the list`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"my-script"}]}"""))
        val vm = viewModel()
        val loaded = vm.awaitLoaded()
        val script = (loaded.scripts as UiState.Data).value.single()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":null}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.delete(script)
        val state = vm.uiState.first { it.deletingId == null && (it.scripts as? UiState.Data)?.value?.isEmpty() == true }

        assertThat((state.scripts as UiState.Data).value).isEmpty()
    }

    @Test
    fun `extractWorkerSource returns a plain script unchanged`() {
        val script = "export default { fetch() { return new Response('hi') } }"

        assertThat(extractWorkerSource(script)).isEqualTo(script)
    }

    @Test
    fun `extractWorkerSource pulls the code out of a module worker's multipart body`() {
        val body = listOf(
            "--boundary123",
            "Content-Disposition: form-data; name=\"worker.js\"; filename=\"worker.js\"",
            "Content-Type: application/javascript+module",
            "",
            "export default { fetch() {} }",
            "--boundary123--"
        ).joinToString("\n")

        val source = extractWorkerSource(body)

        assertThat(source).contains("export default { fetch() {} }")
        assertThat(source).doesNotContain("Content-Disposition")
    }

    @Test
    fun `openDetail loads the script source and its cron triggers`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"my-script"}]}"""))
        val vm = viewModel()
        val script = ((vm.awaitLoaded().scripts) as UiState.Data).value.single()

        server.enqueue(MockResponse().setBody("addEventListener('fetch', () => {})"))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"schedules":[{"cron":"0 * * * *"}]}}"""))

        vm.openDetail(script)
        val state = vm.uiState.first { it.detail?.isLoading == false }

        assertThat(state.detail?.source).contains("addEventListener")
        assertThat(state.detail?.schedules?.single()?.cron).isEqualTo("0 * * * *")
        assertThat(state.detail?.sourceError).isNull()
    }

    @Test
    fun `a failure on one half of the detail still shows the other`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"my-script"}]}"""))
        val vm = viewModel()
        val script = ((vm.awaitLoaded().scripts) as UiState.Data).value.single()

        server.enqueue(MockResponse().setBody("the source"))
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Not entitled"}],"result":null}""")
        )

        vm.openDetail(script)
        val state = vm.uiState.first { it.detail?.isLoading == false }

        assertThat(state.detail?.source).isEqualTo("the source")
        assertThat(state.detail?.schedulesError).contains("Not entitled")
        assertThat(state.detail?.schedules).isEmpty()
    }

    @Test
    fun `closeDetail drops the loaded detail`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"my-script"}]}"""))
        val vm = viewModel()
        val script = ((vm.awaitLoaded().scripts) as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("src"))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"schedules":[]}}"""))
        vm.openDetail(script)
        vm.uiState.first { it.detail?.isLoading == false }

        vm.closeDetail()

        assertThat(vm.uiState.value.detail).isNull()
    }
}
