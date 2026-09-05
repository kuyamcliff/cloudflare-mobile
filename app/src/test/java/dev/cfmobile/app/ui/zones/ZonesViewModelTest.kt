package dev.cfmobile.app.ui.zones

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ZonesViewModelTest {

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

    private val zonesJson = """
        {"success":true,"errors":[],"result":[
            {"id":"1","name":"example.com","status":"active"},
            {"id":"2","name":"test.dev","status":"pending"},
            {"id":"3","name":"another-example.net","status":"active"}
        ]}
    """.trimIndent()

    // The initial load happens via a real (if local) network round trip, so it completes
    // asynchronously relative to the test - waiting for the first non-Loading state is what
    // actually observes that, instead of racing ahead of it by reading .value immediately.
    private suspend fun ZonesViewModel.awaitLoaded(): UiState<List<dev.cfmobile.app.data.remote.dto.CfZone>> =
        state.first { it !is UiState.Loading }

    @Test
    fun `loads zones on init`() = runTest {
        server.enqueue(MockResponse().setBody(zonesJson))
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat(state).isInstanceOf(UiState.Data::class.java)
        assertThat((state as UiState.Data).value).hasSize(3)
    }

    @Test
    fun `query filters by domain name substring, case-insensitively`() = runTest {
        server.enqueue(MockResponse().setBody(zonesJson))
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)))
        viewModel.awaitLoaded()

        viewModel.onQueryChange("EXAMPLE")

        val state = viewModel.state.value as UiState.Data
        assertThat(state.value.map { it.name }).containsExactly("example.com", "another-example.net")
    }

    @Test
    fun `failure surfaces the Cloudflare error message`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Invalid API token"}],"result":null}""")
        )
        val viewModel = ZonesViewModel(ZonesRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat(state).isInstanceOf(UiState.Error::class.java)
        assertThat((state as UiState.Error).message).contains("Invalid API token")
    }
}
