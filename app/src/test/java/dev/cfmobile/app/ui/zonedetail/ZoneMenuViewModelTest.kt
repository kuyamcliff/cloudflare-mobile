package dev.cfmobile.app.ui.zonedetail

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

class ZoneMenuViewModelTest {

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

    private suspend fun ZoneMenuViewModel.awaitLoaded() =
        state.first { it !is UiState.Loading }

    @Test
    fun `records when the zone was last successfully loaded`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"1","name":"example.com","status":"active"}}"""))
        val viewModel = ZoneMenuViewModel("1", ZonesRepository(testApi(server)))

        viewModel.awaitLoaded()

        assertThat(viewModel.lastUpdatedAt.value).isNotNull()
    }

    @Test
    fun `a failed load does not touch the last-updated timestamp`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Invalid API token"}],"result":null}""")
        )
        val viewModel = ZoneMenuViewModel("1", ZonesRepository(testApi(server)))

        viewModel.awaitLoaded()

        assertThat(viewModel.lastUpdatedAt.value).isNull()
    }
}
