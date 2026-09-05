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

class KvRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: KvRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = KvRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listNamespaces hits the account-level kv namespaces endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"ns1","title":"my-namespace"}]}"""))

        val result = repository.listNamespaces("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().title).isEqualTo("my-namespace")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/storage/kv/namespaces")
    }

    @Test
    fun `createNamespace sends the title`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"ns2","title":"new-namespace"}}"""))

        val result = repository.createNamespace("acct1", "new-namespace")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body.readUtf8()).contains("\"title\":\"new-namespace\"")
    }

    @Test
    fun `deleteNamespace maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":10009,"message":"Namespace not found"}],"result":null}""")
        )

        val result = repository.deleteNamespace("acct1", "missing")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Namespace not found")
    }
}
