package dev.cfmobile.app.ui.ssl

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.StringSetting
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SslViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun settingResponse(id: String, value: String) =
        MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"$id","value":"$value","editable":true}}""")

    // The five settings load in parallel over a real (if local) network round trip, so tests
    // await the first non-Loading state instead of racing ahead of it.
    private suspend fun SslViewModel.awaitLoaded() = state.first { it !is UiState.Loading }

    @Test
    fun `loads all five settings and combines them into one state`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.endsWith("/settings/ssl") -> settingResponse("ssl", "full")
                request.path!!.endsWith("/settings/always_use_https") -> settingResponse("always_use_https", "on")
                request.path!!.endsWith("/settings/min_tls_version") -> settingResponse("min_tls_version", "1.2")
                request.path!!.endsWith("/settings/automatic_https_rewrites") -> settingResponse("automatic_https_rewrites", "off")
                request.path!!.endsWith("/settings/security_level") -> settingResponse("security_level", "medium")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()

        val viewModel = SslViewModel("zone1", ZoneSettingsRepository(testApi(server)))
        val state = viewModel.awaitLoaded()

        assertThat(state).isInstanceOf(UiState.Data::class.java)
        val settings = (state as UiState.Data).value
        assertThat(settings.ssl).isEqualTo("full")
        assertThat(settings.alwaysUseHttps).isEqualTo("on")
        assertThat(settings.minTlsVersion).isEqualTo("1.2")
        assertThat(settings.securityLevel).isEqualTo("medium")
    }

    @Test
    fun `a single failing setting fails the whole load`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.endsWith("/settings/ssl") ->
                    MockResponse().setResponseCode(403).setBody("""{"success":false,"errors":[{"code":9109,"message":"Missing permission"}]}""")
                else -> settingResponse("x", "on")
            }
        }
        server.start()

        val viewModel = SslViewModel("zone1", ZoneSettingsRepository(testApi(server)))

        assertThat(viewModel.awaitLoaded()).isInstanceOf(UiState.Error::class.java)
    }

    @Test
    fun `updating a setting patches only that setting and keeps the rest unchanged`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "PATCH" -> settingResponse("ssl", "strict")
                request.path!!.endsWith("/settings/ssl") -> settingResponse("ssl", "full")
                else -> settingResponse("x", "off")
            }
        }
        server.start()
        val viewModel = SslViewModel("zone1", ZoneSettingsRepository(testApi(server)))
        viewModel.awaitLoaded()

        viewModel.update(StringSetting.SSL, "strict")

        val updated = viewModel.state
            .filterIsInstance<UiState.Data<SslSettings>>()
            .first { it.value.ssl == "strict" }
        assertThat(updated.value.ssl).isEqualTo("strict")
    }
}
