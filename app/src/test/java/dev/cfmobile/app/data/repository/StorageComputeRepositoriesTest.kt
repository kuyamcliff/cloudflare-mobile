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

/** Endpoint coverage for the R2, Workers, Pages, and Queues surfaces added together. */
class StorageComputeRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `R2 config endpoints are all under the bucket`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"rules":[]}}"""))
        R2Repository(testApi(server)).getCors("acct1", "bucket")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/r2/buckets/bucket/cors")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"rules":[]}}"""))
        R2Repository(testApi(server)).getLifecycle("acct1", "bucket")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/r2/buckets/bucket/lifecycle")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"domains":[]}}"""))
        R2Repository(testApi(server)).listCustomDomains("acct1", "bucket")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/r2/buckets/bucket/domains/custom")
    }

    @Test
    fun `enabling the managed domain PUTs the enabled flag`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"enabled":true,"domain":"pub-x.r2.dev"}}"""))

        val result = R2Repository(testApi(server)).setManagedDomain("acct1", "bucket", true)

        assertThat((result as ApiResult.Success).data.domain).isEqualTo("pub-x.r2.dev")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.body.readUtf8()).isEqualTo("""{"enabled":true}""")
    }

    @Test
    fun `listSecrets returns names and types, never a value`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"API_TOKEN","type":"secret_text"}]}"""))

        val result = WorkersRepository(testApi(server)).listSecrets("acct1", "my-worker")

        val secret = (result as ApiResult.Success).data.single()
        assertThat(secret.name).isEqualTo("API_TOKEN")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/workers/scripts/my-worker/secrets")
    }

    @Test
    fun `putSecret sends the value once as secret_text`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"name":"API_TOKEN"}}"""))

        WorkersRepository(testApi(server)).putSecret("acct1", "my-worker", "API_TOKEN", "s3cret")

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"name\":\"API_TOKEN\"")
        assertThat(body).contains("\"text\":\"s3cret\"")
        assertThat(body).contains("\"type\":\"secret_text\"")
    }

    @Test
    fun `attachDomain sends the zone, hostname, and worker`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"d1","hostname":"api.a.com"}}"""))

        WorkersRepository(testApi(server)).attachDomain("acct1", "zone1", "api.a.com", "my-worker")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/accounts/acct1/workers/domains")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"zone_id\":\"zone1\"")
        assertThat(body).contains("\"service\":\"my-worker\"")
        assertThat(body).contains("\"environment\":\"production\"")
    }

    @Test
    fun `listDeployments unwraps Cloudflare's deployments object`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"deployments":[{"id":"d1","source":"wrangler"}]}}""")
        )

        val result = WorkersRepository(testApi(server)).listDeployments("acct1", "my-worker")

        assertThat((result as ApiResult.Success).data.single().source).isEqualTo("wrangler")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/workers/scripts/my-worker/deployments")
    }

    @Test
    fun `pages domains are listed and added under the project`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"d1","name":"www.a.com","status":"active"}]}"""))

        val result = PagesRepository(testApi(server)).listDomains("acct1", "site")

        assertThat((result as ApiResult.Success).data.single().name).isEqualTo("www.a.com")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/pages/projects/site/domains")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"d2","name":"a.com"}}"""))
        PagesRepository(testApi(server)).addDomain("acct1", "site", "a.com")
        val post = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.body.readUtf8()).contains("\"name\":\"a.com\"")
    }

    @Test
    fun `queue consumers are listed and detached under the queue`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"consumer_id":"c1","script_name":"worker","settings":{"batch_size":10}}]}"""
            )
        )

        val result = QueuesRepository(testApi(server)).listConsumers("acct1", "q1")

        val consumer = (result as ApiResult.Success).data.single()
        assertThat(consumer.settings?.batchSize).isEqualTo(10)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/queues/q1/consumers")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        QueuesRepository(testApi(server)).deleteConsumer("acct1", "q1", "c1")
        val delete = server.takeRequest()
        assertThat(delete.method).isEqualTo("DELETE")
        assertThat(delete.path).isEqualTo("/accounts/acct1/queues/q1/consumers/c1")
    }
}
