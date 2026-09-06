package dev.cfmobile.app.ui.hyperdrive

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.HyperdriveConfig
import dev.cfmobile.app.data.remote.dto.HyperdriveOrigin
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.HyperdriveRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HyperdriveViewModelTest {

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

    private fun viewModel() = HyperdriveViewModel("acct1", HyperdriveRepository(testApi(server)))

    private suspend fun HyperdriveViewModel.awaitLoaded() = uiState.first { it.configs !is UiState.Loading }

    @Test
    fun `hyperdriveOriginLabel builds a connection summary from whatever fields came back`() {
        val full = HyperdriveConfig(
            id = "h1",
            name = "prod",
            origin = HyperdriveOrigin(host = "db.example.com", port = 5432, database = "app", user = "reader")
        )
        assertThat(hyperdriveOriginLabel(full)).isEqualTo("reader@db.example.com:5432/app")

        val hostOnly = HyperdriveConfig(id = "h2", name = "staging", origin = HyperdriveOrigin(host = "db.example.com"))
        assertThat(hyperdriveOriginLabel(hostOnly)).isEqualTo("db.example.com")

        // No origin at all, or an origin with no host, has nothing meaningful to show.
        assertThat(hyperdriveOriginLabel(HyperdriveConfig(id = "h3", name = "x"))).isNull()
        assertThat(hyperdriveOriginLabel(HyperdriveConfig(id = "h4", name = "x", origin = HyperdriveOrigin(port = 5432)))).isNull()
    }

    @Test
    fun `loads configs on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"h1","name":"prod"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.configs as UiState.Data).value.map { it.name }).containsExactly("prod")
    }

    @Test
    fun `delete removes the config and refreshes`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"h1","name":"prod"}]}"""))
        val vm = viewModel()
        val loaded = vm.awaitLoaded()
        val config = (loaded.configs as UiState.Data).value.single()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.delete(config)
        val state = vm.uiState.first { it.deletingId == null && (it.configs as? UiState.Data)?.value?.isEmpty() == true }

        assertThat((state.configs as UiState.Data).value).isEmpty()
    }
}
