package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Endpoint/shape coverage for Stream, Images, Turnstile, Logpush, Workers AI, and the Zero
 *  Trust device/posture repositories. */
class MediaAndOpsRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listVideos parses free-form metadata and the nested status`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"uid":"v1","status":{"state":"ready"},"meta":{"name":"Launch clip"},"duration":95.5}]}"""
            )
        )

        val result = StreamRepository(testApi(server)).listVideos("acct1")

        val video = (result as ApiResult.Success).data.single()
        assertThat(video.meta?.get("name")).isEqualTo("Launch clip")
        assertThat(video.status?.state).isEqualTo("ready")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/stream")
    }

    @Test
    fun `listImages unwraps the nested images array`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"images":[{"id":"img1","filename":"hero.png"}]}}""")
        )

        val result = ImagesRepository(testApi(server)).listImages("acct1")

        assertThat((result as ApiResult.Success).data.single().filename).isEqualTo("hero.png")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/images/v1")
    }

    @Test
    fun `getImagesStats reads the nested count`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"count":{"allowed":100000,"current":1204}}}"""))

        val result = ImagesRepository(testApi(server)).getStats("acct1")

        assertThat((result as ApiResult.Success).data.count?.current).isEqualTo(1204)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/images/v1/stats")
    }

    @Test
    fun `createTurnstileWidget sends the domain list and mode`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"sitekey":"k1","name":"Signup","domains":["example.com"]}}"""))

        val result = TurnstileRepository(testApi(server))
            .createWidget("acct1", "Signup", listOf("example.com"), "managed")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"domains\":[\"example.com\"]")
        assertThat(body).contains("\"mode\":\"managed\"")
    }

    @Test
    fun `deleteTurnstileWidget addresses the widget by sitekey`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"sitekey":"k1","name":"Signup"}}"""))

        val result = TurnstileRepository(testApi(server)).deleteWidget("acct1", "k1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/challenges/widgets/k1")
    }

    @Test
    fun `setEnabled PUTs the enabled flag for a numeric job id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":42,"enabled":false}}"""))

        val result = LogpushRepository(testApi(server)).setEnabled("acct1", 42, false)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/accounts/acct1/logpush/jobs/42")
        assertThat(request.body.readUtf8()).contains("\"enabled\":false")
    }

    @Test
    fun `searchAiModels hits the model catalogue endpoint`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"m1","name":"@cf/meta/llama-3-8b-instruct","task":{"name":"Text Generation"}}]}"""
            )
        )

        val result = WorkersAiRepository(testApi(server)).listModels("acct1")

        assertThat((result as ApiResult.Success).data.single().task?.name).isEqualTo("Text Generation")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/ai/models/search")
    }

    @Test
    fun `device and posture lists hit their own endpoints`() = runBlocking {
        val repository = DevicePostureRepository(testApi(server))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"d1","name":"Work laptop","user":{"email":"a@example.com"}}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"p1","name":"Disk encryption","type":"disk_encryption"}]}"""))

        val devices = repository.listDevices("acct1")
        val rules = repository.listPostureRules("acct1")

        assertThat((devices as ApiResult.Success).data.single().user?.email).isEqualTo("a@example.com")
        assertThat((rules as ApiResult.Success).data.single().name).isEqualTo("Disk encryption")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/devices")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/devices/posture")
    }
}
