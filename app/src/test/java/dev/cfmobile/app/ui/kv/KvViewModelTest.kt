package dev.cfmobile.app.ui.kv

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.KvRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class KvViewModelTest {

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

    private fun viewModel() = KvViewModel("acct1", KvRepository(testApi(server)))

    private suspend fun KvViewModel.awaitLoaded() = uiState.first { it.namespaces !is UiState.Loading }

    @Test
    fun `validateNamespaceTitle requires a non-blank title`() {
        assertThat(validateNamespaceTitle("")).isEqualTo("Namespace title is required")
        assertThat(validateNamespaceTitle("my-namespace")).isNull()
    }

    @Test
    fun `loads namespaces on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"ns1","title":"a"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.namespaces as UiState.Data).value.map { it.title }).containsExactly("a")
    }

    @Test
    fun `save rejects a blank title before calling the network`() = runTest {
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
    fun `a valid save creates the namespace and closes the form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openForm()
        vm.updateForm { it.copy(title = "new-namespace") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"ns2","title":"new-namespace"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"ns2","title":"new-namespace"}]}"""))

        vm.save()
        val state = vm.uiState.first { it.form == null && (it.namespaces as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.namespaces as UiState.Data).value.map { it.title }).containsExactly("new-namespace")
    }
}
