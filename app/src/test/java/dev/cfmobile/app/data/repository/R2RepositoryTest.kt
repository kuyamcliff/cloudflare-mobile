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

class R2RepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: R2Repository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = R2Repository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listBuckets unwraps the nested buckets array`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"buckets":[{"name":"my-bucket","creation_date":"2024-01-01T00:00:00Z"}]}}"""
            )
        )

        val result = repository.listBuckets("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val buckets = (result as ApiResult.Success).data
        assertThat(buckets).hasSize(1)
        assertThat(buckets[0].name).isEqualTo("my-bucket")
    }

    @Test
    fun `listBuckets hits the account-level r2 buckets endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"buckets":[]}}"""))

        repository.listBuckets("acct1")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/r2/buckets")
    }

    @Test
    fun `createBucket sends the bucket name`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"name":"new-bucket"}}"""))

        val result = repository.createBucket("acct1", "new-bucket")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body.readUtf8()).contains("\"name\":\"new-bucket\"")
    }

    @Test
    fun `deleteBucket maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"success":false,"errors":[{"code":10004,"message":"Bucket not empty"}],"result":null}""")
        )

        val result = repository.deleteBucket("acct1", "my-bucket")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Bucket not empty")
    }
}
