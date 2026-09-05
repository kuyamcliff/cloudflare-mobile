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
}
