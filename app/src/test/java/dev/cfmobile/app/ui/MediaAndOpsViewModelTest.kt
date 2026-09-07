package dev.cfmobile.app.ui

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.AiModel
import dev.cfmobile.app.data.remote.dto.EnrolledDevice
import dev.cfmobile.app.data.remote.dto.DeviceUser
import dev.cfmobile.app.data.remote.dto.ImagesCount
import dev.cfmobile.app.data.remote.dto.ImagesStats
import dev.cfmobile.app.data.remote.dto.StreamVideo
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.DevicePostureRepository
import dev.cfmobile.app.data.repository.LogpushRepository
import dev.cfmobile.app.data.repository.StreamRepository
import dev.cfmobile.app.data.repository.TurnstileRepository
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.deviceposture.DevicePostureTab
import dev.cfmobile.app.ui.deviceposture.DevicePostureViewModel
import dev.cfmobile.app.ui.deviceposture.deviceLabel
import dev.cfmobile.app.ui.images.imagesUsageLabel
import dev.cfmobile.app.ui.logpush.LogpushViewModel
import dev.cfmobile.app.ui.logpush.redactDestination
import dev.cfmobile.app.ui.stream.StreamViewModel
import dev.cfmobile.app.ui.stream.formatDuration
import dev.cfmobile.app.ui.stream.streamVideoTitle
import dev.cfmobile.app.ui.turnstile.TurnstileFormState
import dev.cfmobile.app.ui.turnstile.TurnstileViewModel
import dev.cfmobile.app.ui.turnstile.parseDomains
import dev.cfmobile.app.ui.turnstile.validateTurnstileForm
import dev.cfmobile.app.ui.workersai.aiModelShortName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Pure formatting/validation helpers plus the load paths for the Stream, Images, Turnstile,
 *  Logpush, Workers AI and device-posture screens. */
class MediaAndOpsViewModelTest {

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

    @Test
    fun `streamVideoTitle falls back to the uid when metadata has no name`() {
        assertThat(streamVideoTitle(StreamVideo(uid = "v1", meta = mapOf("name" to "Launch clip")))).isEqualTo("Launch clip")
        assertThat(streamVideoTitle(StreamVideo(uid = "v1", meta = mapOf("name" to "  ")))).isEqualTo("v1")
        assertThat(streamVideoTitle(StreamVideo(uid = "v1"))).isEqualTo("v1")
        // Metadata is free-form, so a non-string "name" must not crash the row.
        assertThat(streamVideoTitle(StreamVideo(uid = "v1", meta = mapOf("name" to 42)))).isEqualTo("v1")
    }

    @Test
    fun `formatDuration renders m ss and h mm ss, and rejects unprocessed videos`() {
        assertThat(formatDuration(95.5)).isEqualTo("1:36")
        assertThat(formatDuration(3661.0)).isEqualTo("1:01:01")
        assertThat(formatDuration(0.0)).isEqualTo("0:00")
        // Cloudflare reports -1 while a video is still processing.
        assertThat(formatDuration(-1.0)).isNull()
        assertThat(formatDuration(null)).isNull()
    }

    @Test
    fun `imagesUsageLabel handles a missing quota`() {
        assertThat(imagesUsageLabel(ImagesStats(ImagesCount(allowed = 100000, current = 1204))))
            .isEqualTo("1,204 of 100,000 images stored")
        assertThat(imagesUsageLabel(ImagesStats(ImagesCount(current = 5)))).isEqualTo("5 images stored")
        assertThat(imagesUsageLabel(null)).isNull()
        assertThat(imagesUsageLabel(ImagesStats())).isNull()
    }

    @Test
    fun `redactDestination drops the credential-bearing query string`() {
        assertThat(redactDestination("s3://bucket/logs?access-key-id=AKIA123&secret-access-key=shh"))
            .isEqualTo("s3://bucket/logs?…")
        assertThat(redactDestination("r2://bucket/path")).isEqualTo("r2://bucket/path")
        assertThat(redactDestination(null)).isNull()
        assertThat(redactDestination("")).isNull()
    }

    @Test
    fun `redactDestination never leaks a secret that was in the query string`() {
        val redacted = redactDestination("s3://bucket/logs?secret-access-key=SUPERSECRET")
        assertThat(redacted).doesNotContain("SUPERSECRET")
    }

    @Test
    fun `aiModelShortName prefers the recognisable trailing segment`() {
        assertThat(aiModelShortName(AiModel(id = "m1", name = "@cf/meta/llama-3-8b-instruct")))
            .isEqualTo("llama-3-8b-instruct")
        assertThat(aiModelShortName(AiModel(id = "m2", name = "bare-name"))).isEqualTo("bare-name")
    }

    @Test
    fun `deviceLabel falls back from device name to user email to id`() {
        assertThat(deviceLabel(EnrolledDevice(id = "d1", name = "Work laptop"))).isEqualTo("Work laptop")
        assertThat(deviceLabel(EnrolledDevice(id = "d1", user = DeviceUser(email = "a@example.com")))).isEqualTo("a@example.com")
        assertThat(deviceLabel(EnrolledDevice(id = "d1"))).isEqualTo("d1")
    }

    @Test
    fun `parseDomains trims and drops empties`() {
        assertThat(parseDomains("example.com, app.example.com ,, ")).containsExactly("example.com", "app.example.com")
    }

    @Test
    fun `validateTurnstileForm requires a name and valid domains`() {
        assertThat(validateTurnstileForm(TurnstileFormState())).isEqualTo("Widget name is required")
        assertThat(validateTurnstileForm(TurnstileFormState(name = "Signup"))).contains("domain")
        assertThat(validateTurnstileForm(TurnstileFormState(name = "Signup", domains = "not a domain"))).contains("valid")
        assertThat(validateTurnstileForm(TurnstileFormState(name = "Signup", domains = "example.com"))).isNull()
    }

    @Test
    fun `stream loads videos on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"uid":"v1","meta":{"name":"Clip"}}]}"""))

        val vm = StreamViewModel("acct1", StreamRepository(testApi(server)))
        val state = vm.uiState.first { it.videos !is UiState.Loading }

        assertThat((state.videos as UiState.Data).value.map { it.uid }).containsExactly("v1")
    }

    @Test
    fun `logpush toggling a job persists and refreshes`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":42,"name":"http","enabled":true}]}"""))
        val vm = LogpushViewModel("acct1", LogpushRepository(testApi(server)))
        val loaded = vm.uiState.first { it.jobs !is UiState.Loading }
        val job = (loaded.jobs as UiState.Data).value.single()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":42,"enabled":false}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":42,"name":"http","enabled":false}]}"""))

        vm.setEnabled(job, false)
        val state = vm.uiState.first { it.togglingId == null && (it.jobs as? UiState.Data)?.value?.firstOrNull()?.enabled == false }

        assertThat((state.jobs as UiState.Data).value.single().enabled).isFalse()
    }

    @Test
    fun `turnstile save rejects an invalid form before calling the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = TurnstileViewModel("acct1", TurnstileRepository(testApi(server)))
        vm.uiState.first { it.widgets !is UiState.Loading }
        val requestsBefore = server.requestCount

        vm.openForm()
        vm.save()

        assertThat(vm.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `device posture loads both tabs from one sequential pass`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"d1","name":"Work laptop"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"p1","name":"Disk encryption"}]}"""))

        val vm = DevicePostureViewModel("acct1", DevicePostureRepository(testApi(server)))
        val state = vm.uiState.first { it.devices !is UiState.Loading && it.postureRules !is UiState.Loading }

        assertThat((state.devices as UiState.Data).value).hasSize(1)
        assertThat((state.postureRules as UiState.Data).value).hasSize(1)
        assertThat(state.tab).isEqualTo(DevicePostureTab.DEVICES)
        assertThat(state.isRefreshing).isFalse()
    }
}
