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

class PagesRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: PagesRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = PagesRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listProjects hits the account-level pages projects endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"my-site","subdomain":"my-site.pages.dev"}]}"""))

        val result = repository.listProjects("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().name).isEqualTo("my-site")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/pages/projects")
    }

    @Test
    fun `listDeployments hits the project-scoped deployments endpoint`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"dep1","environment":"production","deployment_trigger":{"metadata":{"branch":"main"}}}]}"""
            )
        )

        val result = repository.listDeployments("acct1", "my-site")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().deploymentTrigger?.metadata?.branch).isEqualTo("main")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/pages/projects/my-site/deployments")
    }

    @Test
    fun `listProjects maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Authentication error"}],"result":null}""")
        )

        val result = repository.listProjects("acct1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Authentication error")
    }
}
