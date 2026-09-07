package dev.cfmobile.app.ui

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.UploadPayload
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.ImagesRepository
import dev.cfmobile.app.data.repository.StreamRepository
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.images.ImagesViewModel
import dev.cfmobile.app.ui.stream.STREAM_BASIC_UPLOAD_LIMIT_BYTES
import dev.cfmobile.app.ui.stream.StreamViewModel
import dev.cfmobile.app.ui.stream.streamUploadSizeError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.BufferedSink
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Uploading a file the user picked on the device, for Images and Stream. */
class MediaUploadTest {

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

    private fun payload(name: String, content: String = "bytes") =
        UploadPayload(fileName = name, body = content.toRequestBody("image/jpeg".toMediaType()))

    /** The four calls one Images load makes, in order: list, stats, variants, keys. */
    private fun enqueueImagesLoad(images: String = "") {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"images":[$images]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"count":{"current":0}}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"variants":{}}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"keys":[]}}"""))
    }

    /** The four calls one Stream load makes: videos, live inputs, watermarks, keys. */
    private fun enqueueStreamLoad(videos: String = "") {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[$videos]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"liveInputs":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
    }

    /** Stands in for a file too large to actually allocate in a test. */
    private fun oversizedPayload(name: String): UploadPayload {
        val body = object : RequestBody() {
            override fun contentType() = "video/mp4".toMediaType()
            override fun contentLength() = STREAM_BASIC_UPLOAD_LIMIT_BYTES + 1
            override fun writeTo(sink: BufferedSink) = error("must never be sent")
        }
        return UploadPayload(fileName = name, body = body)
    }

    @Test
    fun `toPart sends the file under the name Cloudflare expects`() {
        val part = payload("cat.jpg").toPart()

        assertThat(part.headers?.get("Content-Disposition")).contains("name=\"file\"")
        assertThat(part.headers?.get("Content-Disposition")).contains("cat.jpg")
    }

    @Test
    fun `uploading an image posts multipart and refreshes the list`() = runTest {
        enqueueImagesLoad()
        val vm = ImagesViewModel("acct1", ImagesRepository(testApi(server)))
        // Await the whole init chain, not just the list: one load now makes four calls, and
        // starting an upload while any is still in flight makes the request order a race.
        vm.uiState.first { it.images !is UiState.Loading && it.signingKeys !is UiState.Loading }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"i1","filename":"cat.jpg"}}"""))
        enqueueImagesLoad(images = """{"id":"i1","filename":"cat.jpg"}""")

        vm.upload(payload("cat.jpg"))
        val state = vm.uiState.first { !it.isUploading && (it.images as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat(state.uploadedName).isEqualTo("cat.jpg")
        assertThat(state.uploadError).isNull()
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val upload = requests.single { it.method == "POST" }
        assertThat(upload.path).isEqualTo("/accounts/acct1/images/v1")
        assertThat(upload.headers["Content-Type"]).contains("multipart/form-data")
        assertThat(upload.body.readUtf8()).contains("cat.jpg")
    }

    @Test
    fun `a file that couldn't be opened is reported rather than uploaded as empty`() = runTest {
        enqueueImagesLoad()
        val vm = ImagesViewModel("acct1", ImagesRepository(testApi(server)))
        vm.uiState.first { it.images !is UiState.Loading && it.signingKeys !is UiState.Loading }
        val requestsBefore = server.requestCount

        vm.upload(null)

        assertThat(vm.uiState.value.uploadError).contains("Couldn't read")
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `a rejected upload surfaces Cloudflare's message`() = runTest {
        enqueueImagesLoad()
        val vm = ImagesViewModel("acct1", ImagesRepository(testApi(server)))
        vm.uiState.first { it.images !is UiState.Loading && it.signingKeys !is UiState.Loading }
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":5455,"message":"Image too large"}],"result":null}""")
        )

        vm.upload(payload("huge.jpg"))
        val state = vm.uiState.first { it.uploadError != null }

        assertThat(state.uploadError).contains("Image too large")
        assertThat(state.isUploading).isFalse()
    }

    @Test
    fun `streamUploadSizeError only refuses files past Cloudflare's basic-upload limit`() {
        assertThat(streamUploadSizeError(STREAM_BASIC_UPLOAD_LIMIT_BYTES)).isNull()
        assertThat(streamUploadSizeError(STREAM_BASIC_UPLOAD_LIMIT_BYTES + 1)).contains("200 MB")
    }

    @Test
    fun `an oversized video is refused before anything is sent`() = runTest {
        enqueueStreamLoad()
        val vm = StreamViewModel("acct1", StreamRepository(testApi(server)))
        vm.uiState.first { it.videos !is UiState.Loading && it.signingKeys !is UiState.Loading }
        val requestsBefore = server.requestCount

        vm.upload(oversizedPayload("huge.mp4"))

        assertThat(vm.uiState.value.uploadError).contains("200 MB")
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `uploading a video posts to the stream endpoint and reloads`() = runTest {
        enqueueStreamLoad()
        val vm = StreamViewModel("acct1", StreamRepository(testApi(server)))
        // Await the whole init chain: one load makes four calls now.
        vm.uiState.first { it.videos !is UiState.Loading && it.signingKeys !is UiState.Loading }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"uid":"v1"}}"""))
        enqueueStreamLoad(videos = """{"uid":"v1"}""")

        vm.upload(UploadPayload("clip.mp4", "bytes".toRequestBody("video/mp4".toMediaType())))
        val state = vm.uiState.first { !it.isUploading && (it.videos as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat(state.uploadedName).isEqualTo("clip.mp4")
        // Found by method rather than by position: a load fires four GETs now.
        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val upload = requests.single { it.method == "POST" }
        assertThat(upload.path).isEqualTo("/accounts/acct1/stream")
        assertThat(upload.body.readUtf8()).contains("clip.mp4")
    }
}
