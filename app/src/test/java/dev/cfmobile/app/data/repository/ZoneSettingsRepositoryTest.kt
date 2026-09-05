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

class ZoneSettingsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ZoneSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = ZoneSettingsRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getSetting reads the string value`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"ssl","value":"full","editable":true}}"""))

        val result = repository.getSetting("zone1", StringSetting.SSL)

        assertThat(result).isEqualTo(ApiResult.Success("full"))
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/settings/ssl")
    }

    @Test
    fun `setSetting PATCHes the setting id with the new value`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"always_use_https","value":"on","editable":true}}"""))

        val result = repository.setSetting("zone1", StringSetting.ALWAYS_USE_HTTPS, "on")

        assertThat(result).isEqualTo(ApiResult.Success("on"))
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.path).isEqualTo("/zones/zone1/settings/always_use_https")
        assertThat(request.body.readUtf8()).isEqualTo("""{"value":"on"}""")
    }

    @Test
    fun `purgeEverything sends purge_everything true`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"zone1"}}"""))

        val result = repository.purgeEverything("zone1")

        assertThat(result).isEqualTo(ApiResult.Success(Unit))
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/purge_cache")
        assertThat(request.body.readUtf8()).contains("\"purge_everything\":true")
    }

    @Test
    fun `getBrowserCacheTtl parses an integer value`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"browser_cache_ttl","value":14400,"editable":true}}"""))

        val result = repository.getBrowserCacheTtl("zone1")

        assertThat(result).isEqualTo(ApiResult.Success(14400))
    }
}
