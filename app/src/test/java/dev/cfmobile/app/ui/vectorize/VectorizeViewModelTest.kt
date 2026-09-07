package dev.cfmobile.app.ui.vectorize

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.VectorizeRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VectorizeViewModelTest {

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

    private fun viewModel() = VectorizeViewModel("acct1", VectorizeRepository(testApi(server)))

    private suspend fun VectorizeViewModel.awaitLoaded() = uiState.first { it.indexes !is UiState.Loading }

    @Test
    fun `validateVectorizeForm checks the name and the dimension bounds`() {
        assertThat(validateVectorizeForm(VectorizeFormState())).isEqualTo("Index name is required")
        assertThat(validateVectorizeForm(VectorizeFormState(name = "Docs"))).contains("lowercase")
        assertThat(validateVectorizeForm(VectorizeFormState(name = "docs", dimensions = "abc"))).contains("whole number")
        assertThat(validateVectorizeForm(VectorizeFormState(name = "docs", dimensions = "0"))).contains("between 1 and 1536")
        assertThat(validateVectorizeForm(VectorizeFormState(name = "docs", dimensions = "9000"))).contains("between 1 and 1536")
        assertThat(validateVectorizeForm(VectorizeFormState(name = "docs", dimensions = "768"))).isNull()
    }

    @Test
    fun `loads indexes on init`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"docs","config":{"dimensions":768,"metric":"cosine"}}]}""")
        )

        val state = viewModel().awaitLoaded()

        val index = (state.indexes as UiState.Data).value.single()
        assertThat(index.name).isEqualTo("docs")
        assertThat(index.config?.dimensions).isEqualTo(768)
    }

    @Test
    fun `save rejects an out-of-range dimension before calling the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.openForm()
        vm.updateForm { it.copy(name = "docs", dimensions = "99999") }
        vm.save()

        assertThat(vm.uiState.value.form?.error).contains("between 1 and 1536")
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `a valid save creates the index and closes the form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openForm()
        vm.updateForm { it.copy(name = "docs", dimensions = "768") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"name":"docs"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"docs"}]}"""))

        vm.save()
        val state = vm.uiState.first { it.form == null && (it.indexes as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.indexes as UiState.Data).value.map { it.name }).containsExactly("docs")
    }
}
