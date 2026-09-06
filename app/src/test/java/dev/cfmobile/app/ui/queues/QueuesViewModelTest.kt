package dev.cfmobile.app.ui.queues

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.QueuesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QueuesViewModelTest {

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

    private fun viewModel() = QueuesViewModel("acct1", QueuesRepository(testApi(server)))

    private suspend fun QueuesViewModel.awaitLoaded() = uiState.first { it.queues !is UiState.Loading }

    @Test
    fun `validateQueueName requires a name in Cloudflare's allowed character set`() {
        assertThat(validateQueueName("")).isEqualTo("Queue name is required")
        assertThat(validateQueueName("has spaces")).isNotNull()
        assertThat(validateQueueName("my-queue_1")).isNull()
    }

    @Test
    fun `loads queues on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"queue_id":"q1","queue_name":"jobs"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.queues as UiState.Data).value.map { it.queueName }).containsExactly("jobs")
    }

    @Test
    fun `save rejects an invalid name before calling the network`() = runTest {
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
    fun `a valid save creates the queue and closes the form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openForm()
        vm.updateForm { it.copy(name = "jobs") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"queue_id":"q1","queue_name":"jobs"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"queue_id":"q1","queue_name":"jobs"}]}"""))

        vm.save()
        val state = vm.uiState.first { it.form == null && (it.queues as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.queues as UiState.Data).value.map { it.queueName }).containsExactly("jobs")
    }

    @Test
    fun `refresh keeps existing content on screen instead of flashing the loading state`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"queue_id":"q1","queue_name":"jobs"}]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"queue_id":"q1","queue_name":"jobs"},{"queue_id":"q2","queue_name":"emails"}]}"""))
        vm.refresh()

        // The list stays Data throughout a pull-to-refresh - only isRefreshing toggles.
        val state = vm.uiState.first { (it.queues as? UiState.Data)?.value?.size == 2 }
        assertThat(state.isRefreshing).isFalse()
    }
}
