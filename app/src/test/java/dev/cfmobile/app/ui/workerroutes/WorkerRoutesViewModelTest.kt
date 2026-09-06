package dev.cfmobile.app.ui.workerroutes

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.WorkerRoute
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

class WorkerRoutesViewModelTest {

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

    private fun viewModel() = WorkerRoutesViewModel("zone1", WorkersRepository(testApi(server)))

    private suspend fun WorkerRoutesViewModel.awaitLoaded() = uiState.first { it.routes !is UiState.Loading }

    @Test
    fun `validateRoutePattern insists on a hostname and a path`() {
        assertThat(validateRoutePattern("")).isEqualTo("Route pattern is required")
        assertThat(validateRoutePattern("example.com")).contains("path")
        assertThat(validateRoutePattern("https://example.com/*")).contains("scheme")
        assertThat(validateRoutePattern("example.com/*")).isNull()
        assertThat(validateRoutePattern("*.example.com/api/*")).isNull()
    }

    @Test
    fun `routeScriptLabel spells out that an empty script disables the route`() {
        assertThat(routeScriptLabel(WorkerRoute(id = "r", pattern = "a/*", script = "hello"))).isEqualTo("hello")
        assertThat(routeScriptLabel(WorkerRoute(id = "r", pattern = "a/*", script = ""))).contains("No Worker")
        assertThat(routeScriptLabel(WorkerRoute(id = "r", pattern = "a/*", script = null))).contains("No Worker")
    }

    @Test
    fun `loads the zone's routes on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","pattern":"a.com/*","script":"w"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.routes as UiState.Data).value.single().pattern).isEqualTo("a.com/*")
    }

    @Test
    fun `save rejects an invalid pattern before calling the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.openCreateForm()
        vm.updateForm { it.copy(pattern = "example.com") }
        vm.save()

        assertThat(vm.uiState.value.form?.error).contains("path")
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `a valid save creates the route and reloads the list`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"r1","pattern":"a.com/*","script":"w"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","pattern":"a.com/*","script":"w"}]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openCreateForm()
        vm.updateForm { it.copy(pattern = "a.com/*", script = "w") }
        vm.save()
        val state = vm.uiState.first { it.form == null && (it.routes as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.routes as UiState.Data).value.single().id).isEqualTo("r1")
    }

    @Test
    fun `editing an existing route updates it in place instead of creating another`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","pattern":"a.com/*","script":"w"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"r1","pattern":"b.com/*","script":"w"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","pattern":"b.com/*","script":"w"}]}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openEditForm(WorkerRoute(id = "r1", pattern = "a.com/*", script = "w"))
        vm.updateForm { it.copy(pattern = "b.com/*") }
        vm.save()
        vm.uiState.first { (it.routes as? UiState.Data)?.value?.single()?.pattern == "b.com/*" }

        server.takeRequest()
        val write = server.takeRequest()
        assertThat(write.method).isEqualTo("PUT")
        assertThat(write.path).isEqualTo("/zones/zone1/workers/routes/r1")
    }

    @Test
    fun `a failed save keeps the form open with the error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":10020,"message":"Route pattern conflicts"}],"result":null}""")
        )
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openCreateForm()
        vm.updateForm { it.copy(pattern = "a.com/*", script = "w") }
        vm.save()
        val form = vm.uiState.first { state ->
            state.form.let { it != null && !it.isSaving && it.error != null }
        }.form

        assertThat(form).isNotNull()
        assertThat(form!!.error).contains("conflicts")
        assertThat(form.pattern).isEqualTo("a.com/*")
    }
}
