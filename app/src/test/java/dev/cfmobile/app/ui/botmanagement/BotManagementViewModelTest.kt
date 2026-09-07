package dev.cfmobile.app.ui.botmanagement

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.BotManagementRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BotManagementViewModelTest {

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

    private fun viewModel() = BotManagementViewModel("zone1", BotManagementRepository(testApi(server)))

    private suspend fun BotManagementViewModel.awaitLoaded() = uiState.first { it.config !is UiState.Loading }

    @Test
    fun `loads the zone's bot configuration on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"fight_mode":true}}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.config as UiState.Data).value.fightMode).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/bot_management")
    }

    @Test
    fun `toggling Bot Fight Mode writes the new value and shows what came back`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"fight_mode":false}}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"fight_mode":true}}"""))

        vm.setFightMode(true)
        val state = vm.uiState.first { !it.isSaving && (it.config as? UiState.Data)?.value?.fightMode == true }

        assertThat(state.error).isNull()
        server.takeRequest()
        val put = server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.body.readUtf8()).contains("\"fight_mode\":true")
    }

    @Test
    fun `a failure loading the configuration is an error state, not an empty screen`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":1004,"message":"Not entitled"}],"result":null}""")
        )

        val state = viewModel().awaitLoaded()

        assertThat(state.config).isInstanceOf(UiState.Error::class.java)
    }

    @Test
    fun `nothing is written before the configuration has loaded`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"success":false,"errors":[],"result":null}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.setFightMode(true)

        // Without a loaded config there is no document to send one changed field of.
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }
}
