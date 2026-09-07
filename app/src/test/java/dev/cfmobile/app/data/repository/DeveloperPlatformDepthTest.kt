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

/** Endpoint and shape coverage for the second developer-platform pass: KV keys and values,
 *  the D1 query console, Worker source/schedules/routes, and Pages deployments. */
class DeveloperPlatformDepthTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    // ---- KV keys and values ----

    @Test
    fun `listKeys hits the namespace keys endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"session:1","expiration":1735689600}]}"""))

        val result = KvRepository(testApi(server)).listKeys("acct1", "ns1")

        val key = (result as ApiResult.Success).data.single()
        assertThat(key.name).isEqualTo("session:1")
        assertThat(key.expiration).isEqualTo(1735689600L)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/storage/kv/namespaces/ns1/keys")
    }

    @Test
    fun `getValue returns the raw body rather than an envelope`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"not":"an envelope"}"""))

        val result = KvRepository(testApi(server)).getValue("acct1", "ns1", "k")

        assertThat((result as ApiResult.Success).data).isEqualTo("""{"not":"an envelope"}""")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/storage/kv/namespaces/ns1/values/k")
    }

    @Test
    fun `getValue reports a value that isn't UTF-8 text instead of showing mojibake`() = runBlocking {
        val binary = okio.Buffer().write(byteArrayOf(0xC3.toByte(), 0x28, 0xA0.toByte(), 0xA1.toByte()))
        server.enqueue(MockResponse().setBody(binary))

        val result = KvRepository(testApi(server)).getValue("acct1", "ns1", "blob")

        assertThat((result as ApiResult.Failure).message).contains("binary")
    }

    @Test
    fun `putValue sends the value as the request body`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        val result = KvRepository(testApi(server)).putValue("acct1", "ns1", "k", "hello")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.body.readUtf8()).isEqualTo("hello")
    }

    @Test
    fun `deleteValue targets the key`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        KvRepository(testApi(server)).deleteValue("acct1", "ns1", "k")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/accounts/acct1/storage/kv/namespaces/ns1/values/k")
    }

    // ---- D1 query ----

    @Test
    fun `query posts the SQL and parses one result per statement`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[
                    {"success":true,"results":[{"id":1,"name":"ada"}],"meta":{"duration":1.5,"rows_read":1}},
                    {"success":true,"results":[],"meta":{"rows_written":2}}
                ]}"""
            )
        )

        val result = D1Repository(testApi(server)).query("acct1", "db1", "SELECT 1; SELECT 2")

        val results = (result as ApiResult.Success).data
        assertThat(results).hasSize(2)
        assertThat(results[0].results?.single()?.get("name")).isEqualTo("ada")
        assertThat(results[1].meta?.rowsWritten).isEqualTo(2)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/d1/database/db1/query")
        assertThat(request.body.readUtf8()).contains("\"sql\":\"SELECT 1; SELECT 2\"")
    }

    @Test
    fun `a SQL error comes back as Failure with Cloudflare's message`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":7500,"message":"no such table: users"}],"result":null}""")
        )

        val result = D1Repository(testApi(server)).query("acct1", "db1", "SELECT * FROM users")

        assertThat((result as ApiResult.Failure).message).contains("no such table")
    }

    // ---- Workers source, schedules, routes ----

    @Test
    fun `getScriptSource returns the script body verbatim`() = runBlocking {
        server.enqueue(MockResponse().setBody("export default { fetch() {} }"))

        val result = WorkersRepository(testApi(server)).getScriptSource("acct1", "hello")

        assertThat((result as ApiResult.Success).data).isEqualTo("export default { fetch() {} }")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/workers/scripts/hello")
    }

    @Test
    fun `getSchedules unwraps Cloudflare's schedules object into a list`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"schedules":[{"cron":"*/5 * * * *"}]}}"""))

        val result = WorkersRepository(testApi(server)).getSchedules("acct1", "hello")

        assertThat((result as ApiResult.Success).data.single().cron).isEqualTo("*/5 * * * *")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/workers/scripts/hello/schedules")
    }

    @Test
    fun `listRoutes is zone-scoped, not account-scoped`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","pattern":"example.com/*","script":"hello"}]}"""))

        val result = WorkersRepository(testApi(server)).listRoutes("zone1")

        assertThat((result as ApiResult.Success).data.single().pattern).isEqualTo("example.com/*")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/workers/routes")
    }

    @Test
    fun `createRoute sends the pattern and script`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"r1","pattern":"example.com/*","script":"hello"}}"""))

        WorkersRepository(testApi(server)).createRoute("zone1", "example.com/*", "hello")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"pattern\":\"example.com/*\"")
        assertThat(body).contains("\"script\":\"hello\"")
    }

    @Test
    fun `updateRoute PUTs to the route id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"r1","pattern":"a.com/*","script":""}}"""))

        WorkersRepository(testApi(server)).updateRoute("zone1", "r1", "a.com/*", "")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/zones/zone1/workers/routes/r1")
    }

    @Test
    fun `deleteRoute targets the route id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        WorkersRepository(testApi(server)).deleteRoute("zone1", "r1")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/zones/zone1/workers/routes/r1")
    }

    // ---- Pages deployments ----

    @Test
    fun `createDeployment posts to the project's deployments collection`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"d1","environment":"production"}}"""))

        val result = PagesRepository(testApi(server)).createDeployment("acct1", "site")

        assertThat((result as ApiResult.Success).data.id).isEqualTo("d1")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/accounts/acct1/pages/projects/site/deployments")
    }

    @Test
    fun `retryDeployment posts to the retry sub-resource`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"d2"}}"""))

        PagesRepository(testApi(server)).retryDeployment("acct1", "site", "d1")

        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/pages/projects/site/deployments/d1/retry")
    }
}
