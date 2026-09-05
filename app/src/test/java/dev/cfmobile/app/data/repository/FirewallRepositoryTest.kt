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

class FirewallRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: FirewallRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = FirewallRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `createRule sends an inline filter expression alongside the action`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"r1","action":"block","filter":{"id":"f1","expression":"ip.src eq 203.0.113.5"}}]}"""
            )
        )

        val result = repository.createRule("zone1", "ip.src eq 203.0.113.5", "block", "Block bad IP")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/firewall/rules")
        val body = request.body.readUtf8()
        assertThat(body).contains("ip.src eq 203.0.113.5")
        assertThat(body).contains("\"action\":\"block\"")
    }

    @Test
    fun `createAccessRule targets an IP with the given mode`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"a1","mode":"challenge","configuration":{"target":"ip","value":"203.0.113.5"}}}"""
            )
        )

        val result = repository.createAccessRule("zone1", "challenge", "203.0.113.5", null)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"mode\":\"challenge\"")
        assertThat(body).contains("\"value\":\"203.0.113.5\"")
    }

    @Test
    fun `deleteAccessRule hits the expected path`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"a1"}}"""))

        repository.deleteAccessRule("zone1", "a1")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/zones/zone1/firewall/access_rules/rules/a1")
    }
}
