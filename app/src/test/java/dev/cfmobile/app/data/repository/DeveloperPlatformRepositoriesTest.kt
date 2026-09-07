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

/** Endpoint/shape coverage for the developer-platform repositories added together: Queues,
 *  Durable Objects, Workflows, Hyperdrive, and Vectorize. */
class DeveloperPlatformRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listQueues hits the account queues endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"queue_id":"q1","queue_name":"jobs"}]}"""))

        val result = QueuesRepository(testApi(server)).listQueues("acct1")

        assertThat((result as ApiResult.Success).data.single().queueName).isEqualTo("jobs")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/queues")
    }

    @Test
    fun `createQueue sends the queue name in Cloudflare's snake_case field`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"queue_id":"q2","queue_name":"jobs"}}"""))

        val result = QueuesRepository(testApi(server)).createQueue("acct1", "jobs")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().body.readUtf8()).contains("\"queue_name\":\"jobs\"")
    }

    @Test
    fun `deleteQueue maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":11000,"message":"Queue not found"}],"result":null}""")
        )

        val result = QueuesRepository(testApi(server)).deleteQueue("acct1", "missing")

        assertThat((result as ApiResult.Failure).message).contains("Queue not found")
    }

    @Test
    fun `listNamespaces hits the durable objects namespaces endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"ns1","name":"Rooms","script":"chat","class":"Room"}]}"""))

        val result = DurableObjectsRepository(testApi(server)).listNamespaces("acct1")

        val namespace = (result as ApiResult.Success).data.single()
        assertThat(namespace.name).isEqualTo("Rooms")
        assertThat(namespace.className).isEqualTo("Room")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/workers/durable_objects/namespaces")
    }

    @Test
    fun `listWorkflows and listInstances hit their endpoints`() = runBlocking {
        val repository = WorkflowsRepository(testApi(server))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"w1","name":"orders"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"i1","status":"complete"}]}"""))

        val workflows = repository.listWorkflows("acct1")
        val instances = repository.listInstances("acct1", "orders")

        assertThat((workflows as ApiResult.Success).data.single().name).isEqualTo("orders")
        assertThat((instances as ApiResult.Success).data.single().status).isEqualTo("complete")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/workflows")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/workflows/orders/instances")
    }

    @Test
    fun `listHyperdriveConfigs parses the nested origin`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"h1","name":"prod","origin":{"host":"db.example.com","port":5432,"database":"app","user":"reader"}}]}"""
            )
        )

        val result = HyperdriveRepository(testApi(server)).listConfigs("acct1")

        val config = (result as ApiResult.Success).data.single()
        assertThat(config.origin?.host).isEqualTo("db.example.com")
        assertThat(config.origin?.port).isEqualTo(5432)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/hyperdrive/configs")
    }

    @Test
    fun `createVectorizeIndex sends the nested config and omits a blank description`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"name":"docs"}}"""))

        val result = VectorizeRepository(testApi(server)).createIndex("acct1", "docs", 768, "cosine", "  ")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"dimensions\":768")
        assertThat(body).contains("\"metric\":\"cosine\"")
        assertThat(body).doesNotContain("description")
    }

    @Test
    fun `deleteVectorizeIndex addresses the index by name`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        val result = VectorizeRepository(testApi(server)).deleteIndex("acct1", "docs")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/vectorize/v2/indexes/docs")
    }
}
