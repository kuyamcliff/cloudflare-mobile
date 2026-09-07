package dev.cfmobile.app.ui.zonesettings

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.ui.common.UiState
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

class ZoneSettingsGroupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    private val specs = listOf(
        ZoneSettingSpec.Toggle("brotli", "Brotli"),
        ZoneSettingSpec.Toggle("early_hints", "Early Hints"),
        ZoneSettingSpec.Options("polish", "Polish", listOf("off" to "Off", "lossless" to "Lossless"))
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun viewModel() = ZoneSettingsGroupViewModel("zone1", ZoneSettingsRepository(testApi(server)), specs)

    /** Answers by setting id rather than by arrival order, so the test doesn't depend on the
     *  sequence requests happen to be issued in. A PATCH echoes back the value it was sent,
     *  which is what Cloudflare does - answering with the old value instead would make a
     *  successful write look like it silently did nothing. */
    private fun dispatchBySetting(values: Map<String, String>, unavailable: Set<String> = emptySet()) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val settingId = request.path.orEmpty().substringAfterLast('/')
                if (settingId in unavailable) {
                    return MockResponse().setResponseCode(403)
                        .setBody("""{"success":false,"errors":[{"code":1015,"message":"Not available on this plan"}],"result":null}""")
                }
                val written = if (request.method == "PATCH") {
                    Regex("\"value\"\\s*:\\s*\"([^\"]*)\"").find(request.body.readUtf8())?.groupValues?.get(1)
                } else {
                    null
                }
                val value = written ?: values[settingId] ?: "off"
                return MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"$settingId","value":"$value"}}""")
            }
        }
    }

    @Test
    fun `isSettingOn maps Cloudflare's on off strings`() {
        assertThat("on".isSettingOn()).isTrue()
        assertThat("off".isSettingOn()).isFalse()
        assertThat(null.isSettingOn()).isFalse()
        assertThat(true.toSettingValue()).isEqualTo("on")
        assertThat(false.toSettingValue()).isEqualTo("off")
    }

    @Test
    fun `loads every setting in the group`() = runTest {
        dispatchBySetting(mapOf("brotli" to "on", "early_hints" to "off", "polish" to "lossless"))

        val state = viewModel().uiState.first { it.values !is UiState.Loading }

        val values = (state.values as UiState.Data).value
        assertThat(values["brotli"]).isEqualTo("on")
        assertThat(values["polish"]).isEqualTo("lossless")
        assertThat(state.unavailableIds).isEmpty()
    }

    @Test
    fun `a setting the plan does not include is marked unavailable, not shown as off`() = runTest {
        dispatchBySetting(mapOf("brotli" to "on", "polish" to "off"), unavailable = setOf("early_hints"))

        val state = viewModel().uiState.first { it.values !is UiState.Loading }

        // The rest of the group still loads and stays usable.
        assertThat((state.values as UiState.Data).value).containsKey("brotli")
        assertThat(state.unavailableIds).containsExactly("early_hints")
    }

    @Test
    fun `only a total failure becomes an error state`() = runTest {
        dispatchBySetting(emptyMap(), unavailable = setOf("brotli", "early_hints", "polish"))

        val state = viewModel().uiState.first { it.values !is UiState.Loading }

        assertThat(state.values).isInstanceOf(UiState.Error::class.java)
    }

    @Test
    fun `a successful write stores the value the server confirmed`() = runTest {
        dispatchBySetting(mapOf("brotli" to "off", "early_hints" to "off", "polish" to "off"))
        val vm = viewModel()
        vm.uiState.first { it.values !is UiState.Loading }

        vm.toggle(specs[0], true)
        val state = vm.uiState.first { it.savingId == null && (it.values as? UiState.Data)?.value?.get("brotli") == "on" }

        assertThat((state.values as UiState.Data).value["brotli"]).isEqualTo("on")
        assertThat(state.error).isNull()
    }

    @Test
    fun `a failed write leaves the stored value alone and reports why`() = runTest {
        dispatchBySetting(mapOf("brotli" to "off", "early_hints" to "off", "polish" to "off"))
        val vm = viewModel()
        vm.uiState.first { it.values !is UiState.Loading }

        // Reject the PATCH, so the toggle must not claim the change took effect.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":1015,"message":"Requires a paid plan"}],"result":null}""")
        }
        vm.toggle(specs[0], true)
        val state = vm.uiState.first { it.savingId == null && it.error != null }

        assertThat((state.values as UiState.Data).value["brotli"]).isEqualTo("off")
        assertThat(state.error).contains("Requires a paid plan")
        assertThat(state.error).contains("Brotli")
    }
}
