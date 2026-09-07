package dev.cfmobile.app.ui.stream

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.StreamCaption
import dev.cfmobile.app.data.remote.dto.StreamLiveInput
import dev.cfmobile.app.data.remote.dto.StreamRecording
import dev.cfmobile.app.data.remote.dto.StreamVideo
import dev.cfmobile.app.data.remote.dto.StreamWatermark
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.StreamRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The pieces around a video: live inputs, watermarks, captions, and signing keys. */
class StreamViewModelTest {

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

    private fun viewModel() = StreamViewModel("acct1", StreamRepository(testApi(server)))

    /** The four calls one load makes, in order: videos, live inputs, watermarks, keys. */
    private fun enqueueLoad(videos: String = "", liveInputs: String = "") {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[$videos]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"liveInputs":[$liveInputs]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
    }

    private suspend fun StreamViewModel.awaitLoaded() =
        uiState.first { it.videos !is UiState.Loading && it.signingKeys !is UiState.Loading }

    // ---- Formatting ----

    @Test
    fun `a live input falls back to its uid when it has no name`() {
        assertThat(liveInputName(StreamLiveInput(uid = "i1", meta = mapOf("name" to "Main stage"))))
            .isEqualTo("Main stage")
        assertThat(liveInputName(StreamLiveInput(uid = "i1"))).isEqualTo("i1")
        assertThat(liveInputName(StreamLiveInput(uid = "i1", meta = mapOf("name" to " ")))).isEqualTo("i1")
    }

    @Test
    fun `liveInputSummary says whether it is broadcasting and being recorded`() {
        val recording = StreamLiveInput(uid = "i1", recording = StreamRecording(mode = "automatic"))

        assertThat(liveInputSummary(recording)).isEqualTo("idle · recording on")
        assertThat(liveInputSummary(StreamLiveInput(uid = "i1"))).isEqualTo("idle · recording off")
    }

    @Test
    fun `watermarkSummary turns Cloudflare's fractions into percentages`() {
        val watermark = StreamWatermark(uid = "w1", position = "upperRight", opacity = 0.2, scale = 0.1)

        assertThat(watermarkSummary(watermark)).isEqualTo("upperRight · 20% opacity · 10% scale")
    }

    @Test
    fun `captionSummary distinguishes generated tracks from uploaded ones`() {
        assertThat(captionSummary(StreamCaption(language = "en", status = "ready", generated = true)))
            .isEqualTo("ready · auto-generated")
        assertThat(captionSummary(StreamCaption(language = "en", status = "ready")))
            .isEqualTo("ready · uploaded")
    }

    // ---- Loading ----

    @Test
    fun `every tab loads from its own Stream endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"uid":"v1"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"liveInputs":[{"uid":"i1"}]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"uid":"w1","name":"logo"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"k1"}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.videos as UiState.Data).value.single().uid).isEqualTo("v1")
        assertThat((state.liveInputs as UiState.Data).value.single().uid).isEqualTo("i1")
        assertThat((state.watermarks as UiState.Data).value.single().name).isEqualTo("logo")
        assertThat((state.signingKeys as UiState.Data).value.single().id).isEqualTo("k1")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/stream")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/stream/live_inputs")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/stream/watermarks")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/stream/keys")
    }

    // ---- Live inputs ----

    @Test
    fun `creating a live input opens straight onto the stream key it returns`() = runTest {
        enqueueLoad()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"uid":"i1","meta":{"name":"Main stage"},"rtmps":{"url":"rtmps://live.cloudflare.com:443/live/","streamKey":"push-me"}}}"""
            )
        )
        enqueueLoad()

        vm.openLiveInputForm()
        vm.updateLiveInputForm { it.copy(name = "Main stage", record = true) }
        vm.saveLiveInput()
        val state = vm.uiState.first { it.liveInputDetail?.isLoading == false }

        // The create response already carries the key, so no second read is made for it.
        assertThat(state.liveInputDetail?.input?.rtmps?.streamKey).isEqualTo("push-me")
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val post = requests.single { it.method == "POST" }
        assertThat(post.path).isEqualTo("/accounts/acct1/stream/live_inputs")
        assertThat(post.body.readUtf8()).contains("\"mode\":\"automatic\"")
    }

    @Test
    fun `opening an existing live input fetches the key the list omits`() = runTest {
        enqueueLoad(liveInputs = """{"uid":"i1","meta":{"name":"Main stage"}}""")
        val vm = viewModel()
        val input = (vm.awaitLoaded().liveInputs as UiState.Data).value.single()
        assertThat(input.rtmps).isNull()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"uid":"i1","srt":{"url":"srt://x","streamKey":"push-me"}}}""")
        )

        vm.openLiveInput(input)
        val state = vm.uiState.first { it.liveInputDetail?.isLoading == false }

        assertThat(state.liveInputDetail?.input?.srt?.streamKey).isEqualTo("push-me")
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.last().path).isEqualTo("/accounts/acct1/stream/live_inputs/i1")
    }

    @Test
    fun `closing the sheet drops the stream key`() = runTest {
        enqueueLoad(liveInputs = """{"uid":"i1"}""")
        val vm = viewModel()
        val input = (vm.awaitLoaded().liveInputs as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"uid":"i1","rtmps":{"url":"rtmps://x","streamKey":"push-me"}}}""")
        )
        vm.openLiveInput(input)
        vm.uiState.first { it.liveInputDetail?.isLoading == false }

        vm.closeLiveInput()

        // Nothing holds the credential once the sheet is gone.
        assertThat(vm.uiState.value.liveInputDetail).isNull()
    }

    @Test
    fun `a live input needs a name`() = runTest {
        enqueueLoad()
        val vm = viewModel()
        vm.awaitLoaded()
        val before = server.requestCount

        vm.openLiveInputForm()
        vm.saveLiveInput()

        assertThat(server.requestCount).isEqualTo(before)
        assertThat(vm.uiState.value.liveInputForm?.error).contains("name is required")
    }

    // ---- Captions ----

    @Test
    fun `captions load for the video that was opened`() = runTest {
        enqueueLoad(videos = """{"uid":"v1"}""")
        val vm = viewModel()
        val video = (vm.awaitLoaded().videos as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"language":"en","label":"English","status":"ready"}]}""")
        )

        vm.openCaptions(video)
        val state = vm.uiState.first { it.captions?.isLoading == false }

        assertThat(state.captions?.captions?.single()?.label).isEqualTo("English")
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.last().path).isEqualTo("/accounts/acct1/stream/v1/captions")
    }

    @Test
    fun `deleting a caption removes it from the sheet without a reload`() = runTest {
        enqueueLoad(videos = """{"uid":"v1"}""")
        val vm = viewModel()
        val video = (vm.awaitLoaded().videos as UiState.Data).value.single()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"language":"en"},{"language":"fr"}]}""")
        )
        vm.openCaptions(video)
        vm.uiState.first { it.captions?.isLoading == false }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        vm.deleteCaption(StreamCaption(language = "en"))
        val state = vm.uiState.first { it.captions?.captions?.size == 1 }

        assertThat(state.captions?.captions?.single()?.language).isEqualTo("fr")
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "DELETE" }.path)
            .isEqualTo("/accounts/acct1/stream/v1/captions/en")
    }

    // ---- Deletes ----

    @Test
    fun `deleting a signing key reports a refusal instead of failing silently`() = runTest {
        enqueueLoad()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Key in use"}],"result":null}""")
        )

        vm.deleteSigningKey(dev.cfmobile.app.data.remote.dto.StreamSigningKey(id = "k1"))
        val state = vm.uiState.first { it.error != null }

        assertThat(state.error).contains("Key in use")
        assertThat(state.deletingId).isNull()
    }

    @Test
    fun `an oversized video is refused before the upload starts`() {
        assertThat(streamUploadSizeError(STREAM_BASIC_UPLOAD_LIMIT_BYTES)).isNull()
        assertThat(streamUploadSizeError(STREAM_BASIC_UPLOAD_LIMIT_BYTES + 1)).contains("200 MB")
    }

    @Test
    fun `deleting a video still works through the shared delete path`() = runTest {
        enqueueLoad(videos = """{"uid":"v1"}""")
        val vm = viewModel()
        val video = (vm.awaitLoaded().videos as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        enqueueLoad()

        vm.delete(video)
        vm.uiState.first { it.deletingId == null && !it.isRefreshing }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "DELETE" }.path).isEqualTo("/accounts/acct1/stream/v1")
    }

    @Test
    fun `streamVideoTitle prefers the metadata name`() {
        assertThat(streamVideoTitle(StreamVideo(uid = "v1", meta = mapOf("name" to "Keynote"))))
            .isEqualTo("Keynote")
        assertThat(streamVideoTitle(StreamVideo(uid = "v1"))).isEqualTo("v1")
    }
}
