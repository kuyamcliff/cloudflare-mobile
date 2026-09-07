package dev.cfmobile.app.ui.d1

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.D1Repository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class D1ViewModelTest {

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

    private fun viewModel() = D1ViewModel("acct1", D1Repository(testApi(server)))

    private suspend fun D1ViewModel.awaitLoaded() = uiState.first { it.databases !is UiState.Loading }

    @Test
    fun `validateDatabaseName requires a non-blank name matching the allowed character set`() {
        assertThat(validateDatabaseName("")).isEqualTo("Database name is required")
        assertThat(validateDatabaseName("my database")).isNotNull()
        assertThat(validateDatabaseName("my-database_1")).isNull()
    }

    @Test
    fun `loads databases on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"uuid":"db1","name":"a"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.databases as UiState.Data).value.map { it.name }).containsExactly("a")
    }

    @Test
    fun `save rejects a blank name before calling the network`() = runTest {
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
    fun `a valid save creates the database and closes the form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openForm()
        vm.updateForm { it.copy(name = "new-database") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"uuid":"db2","name":"new-database"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"uuid":"db2","name":"new-database"}]}"""))

        vm.save()
        val state = vm.uiState.first { it.form == null && (it.databases as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.databases as UiState.Data).value.map { it.name }).containsExactly("new-database")
    }
}
