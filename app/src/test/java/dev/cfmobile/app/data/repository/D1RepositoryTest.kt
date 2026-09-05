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

class D1RepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: D1Repository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = D1Repository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listDatabases hits the account-level d1 database endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"uuid":"db1","name":"my-database"}]}"""))

        val result = repository.listDatabases("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().name).isEqualTo("my-database")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/d1/database")
    }

    @Test
    fun `createDatabase sends the name`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"uuid":"db2","name":"new-database"}}"""))

        val result = repository.createDatabase("acct1", "new-database")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body.readUtf8()).contains("\"name\":\"new-database\"")
    }

    @Test
    fun `deleteDatabase maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":7404,"message":"Database not found"}],"result":null}""")
        )

        val result = repository.deleteDatabase("acct1", "missing")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Database not found")
    }
}
