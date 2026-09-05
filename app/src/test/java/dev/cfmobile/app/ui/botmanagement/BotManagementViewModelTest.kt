package dev.cfmobile.app.ui.botmanagement

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
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

    private fun viewModel() = BotManagementViewModel("zone1", ZoneSettingsRepository(testApi(server)))

    private suspend fun BotManagementViewModel.awaitLoaded() = uiState.first { it.botFightMode !is UiState.Loading }

    @Test
    fun `loads the current bot fight mode value as a boolean`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"bot_fight_mode","value":"on"}}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.botFightMode as UiState.Data).value).isTrue()
    }

    @Test
    fun `hits the bot_fight_mode setting endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"bot_fight_mode","value":"off"}}"""))

        viewModel().awaitLoaded()

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/settings/bot_fight_mode")
    }

    @Test
    fun `setBotFightMode toggles the value and reflects the server response`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"bot_fight_mode","value":"off"}}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"bot_fight_mode","value":"on"}}"""))
        vm.setBotFightMode(true)
        val state = vm.uiState.first { (it.botFightMode as? UiState.Data)?.value == true }

        assertThat((state.botFightMode as UiState.Data).value).isTrue()
        assertThat(state.isSaving).isFalse()
    }

    @Test
    fun `failure surfaces as an error state`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Invalid API token"}],"result":null}""")
        )

        val state = viewModel().awaitLoaded()

        assertThat(state.botFightMode).isInstanceOf(UiState.Error::class.java)
    }
}
