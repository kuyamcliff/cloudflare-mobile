package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.ImageVariantOptions
import dev.cfmobile.app.data.remote.dto.TraceRequest
import dev.cfmobile.app.data.remote.dto.WaitingRoomEventWrite
import dev.cfmobile.app.data.remote.dto.ZoneDnsSettingsUpdate
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Endpoint coverage for the media depth and diagnostics surfaces. */
class MediaDiagnosticsRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `live inputs arrive nested and only the single read carries a stream key`(): Unit = runBlocking {
        val repository = StreamRepository(testApi(server))
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"liveInputs":[{"uid":"i1","meta":{"name":"Main"}}]}}""")
        )

        val listed = repository.listLiveInputs("acct1")

        val input = (listed as ApiResult.Success).data.single()
        assertThat(input.uid).isEqualTo("i1")
        assertThat(input.rtmps).isNull()
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/stream/live_inputs")

        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"uid":"i1","rtmps":{"url":"rtmps://x","streamKey":"push-me"}}}""")
        )
        val single = repository.getLiveInput("acct1", "i1")
        assertThat((single as ApiResult.Success).data.rtmps?.streamKey).isEqualTo("push-me")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/stream/live_inputs/i1")
    }

    @Test
    fun `creating a live input sends the name as metadata and the recording mode`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"uid":"i1"}}"""))

        StreamRepository(testApi(server)).createLiveInput("acct1", "Main stage", record = false)

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"name\":\"Main stage\"")
        assertThat(body).contains("\"mode\":\"off\"")
    }

    @Test
    fun `a caption is addressed by its language`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        val result = StreamRepository(testApi(server)).deleteCaption("acct1", "v1", "en")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/stream/v1/captions/en")
    }

    @Test
    fun `image variants are keyed by id and get that id filled in`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"variants":{"thumbnail":{"options":{"fit":"cover","width":200,"height":200,"metadata":"none"}}}}}"""
            )
        )

        val result = ImagesRepository(testApi(server)).listVariants("acct1")

        val variant = (result as ApiResult.Success).data.single()
        assertThat(variant.id).isEqualTo("thumbnail")
        assertThat(variant.options?.fit).isEqualTo("cover")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/images/v1/variants")
    }

    @Test
    fun `creating a variant unwraps the object Cloudflare nests it in`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"variant":{"id":"thumbnail"}}}""")
        )

        val result = ImagesRepository(testApi(server)).createVariant(
            "acct1",
            "thumbnail",
            ImageVariantOptions(fit = "cover", width = 200, height = 200),
            neverRequireSignedUrls = false
        )

        assertThat((result as ApiResult.Success).data?.id).isEqualTo("thumbnail")
    }

    @Test
    fun `an image signing key's value is dropped before it can leave the repository`(): Unit = runBlocking {
        // Cloudflare sends the value; nothing above this layer ever sees it.
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"keys":[{"name":"default","value":"super-secret"}]}}""")
        )

        val result = ImagesRepository(testApi(server)).listSigningKeys("acct1")

        val key = (result as ApiResult.Success).data.single()
        assertThat(key.name).isEqualTo("default")
        assertThat(key.value).isNull()
    }

    @Test
    fun `rotating a Turnstile secret is the one call that returns one`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"sitekey":"0x1","secret":"0xNEW"}}""")
        )

        val result = TurnstileRepository(testApi(server)).rotateSecret("acct1", "0x1")

        assertThat((result as ApiResult.Success).data.secret).isEqualTo("0xNEW")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/accounts/acct1/challenges/widgets/0x1/rotate_secret")
    }

    @Test
    fun `no other Turnstile call asks Cloudflare for a secret`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"sitekey":"0x1","name":"Checkout"}]}"""))

        val result = TurnstileRepository(testApi(server)).listWidgets("acct1")

        // The widget DTO has no secret field at all, so a list can't carry one.
        assertThat((result as ApiResult.Success).data.single().sitekey).isEqualTo("0x1")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/challenges/widgets")
    }

    @Test
    fun `a trace posts to the account tracer and reports the steps`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"status_code":403,"trace":[{"step":1,"step_name":"waf","matched":true,"action":"block"}]}}"""
            )
        )

        val result = DiagnosticsRepository(testApi(server))
            .trace("acct1", TraceRequest(url = "https://example.com", method = "GET"))

        assertThat((result as ApiResult.Success).data.trace.single().action).isEqualTo("block")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/request-tracer/trace")
    }

    @Test
    fun `web3 hostnames live under the zone`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"h1","name":"web3.example.com","target":"ipfs"}}""")
        )

        val result = Web3Repository(testApi(server))
            .createHostname("zone1", "web3.example.com", "ipfs", description = null, dnslink = "/ipfs/bafy")

        assertThat((result as ApiResult.Success).data.id).isEqualTo("h1")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/web3/hostnames")
        assertThat(request.body.readUtf8()).contains("\"dnslink\":\"/ipfs/bafy\"")
    }

    @Test
    fun `a waiting room event delete succeeds even though it echoes the event`(): Unit = runBlocking {
        val repository = WaitingRoomRepository(testApi(server))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"e1","name":"Sale"}}"""))

        val deleted = repository.deleteEvent("zone1", "r1", "e1")

        assertThat(deleted).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/waiting_rooms/r1/events/e1")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"e2","name":"Drop"}}"""))
        repository.createEvent(
            "zone1",
            "r1",
            WaitingRoomEventWrite(
                name = "Drop",
                eventStartTime = "2026-01-02T15:00:00Z",
                eventEndTime = "2026-01-02T18:00:00Z"
            )
        )
        val post = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.path).isEqualTo("/zones/zone1/waiting_rooms/r1/events")
    }

    @Test
    fun `a DNS settings update sends only the field it was given`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"flatten_all_cnames":true}}"""))

        val result = ZoneDnsSettingsRepository(testApi(server))
            .update("zone1", ZoneDnsSettingsUpdate(flattenAllCnames = true))

        assertThat((result as ApiResult.Success).data.flattenAllCnames).isTrue()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        // Moshi drops the nulls, so the untouched settings are never overwritten.
        assertThat(request.body.readUtf8()).isEqualTo("""{"flatten_all_cnames":true}""")
    }
}
