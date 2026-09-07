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

class WorkersRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: WorkersRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = WorkersRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listScripts hits the account-level workers scripts endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"my-script","usage_model":"bundled"}]}"""))

        val result = repository.listScripts("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().id).isEqualTo("my-script")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/workers/scripts")
    }

    @Test
    fun `deleteScript maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":10007,"message":"Script not found"}],"result":null}""")
        )

        val result = repository.deleteScript("acct1", "missing")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Script not found")
    }
}
