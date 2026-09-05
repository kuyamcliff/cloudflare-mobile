package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AccessApplicationCreate
import dev.cfmobile.app.data.remote.dto.AccessEmailDomainRule
import dev.cfmobile.app.data.remote.dto.AccessPolicyCreate
import dev.cfmobile.app.data.remote.dto.AccessPolicyIncludeRule
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AccessRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AccessRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = AccessRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listApplications hits the account-level access apps endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"app1","name":"Staging","domain":"staging.example.com"}]}"""))

        val result = repository.listApplications("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().name).isEqualTo("Staging")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/access/apps")
    }

    @Test
    fun `createApplication sends name and domain`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"app2","name":"Staging","domain":"staging.example.com"}}"""))

        val result = repository.createApplication("acct1", AccessApplicationCreate(name = "Staging", domain = "staging.example.com"))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body.readUtf8()).contains("\"domain\":\"staging.example.com\"")
    }

    @Test
    fun `createPolicy posts to the app-scoped policies endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        val result = repository.createPolicy(
            "acct1",
            "app2",
            AccessPolicyCreate(name = "Staging policy", decision = "allow", include = listOf(AccessPolicyIncludeRule(emailDomain = AccessEmailDomainRule("example.com"))))
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/access/apps/app2/policies")
        assertThat(request.body.readUtf8()).contains("\"email_domain\":{\"domain\":\"example.com\"}")
    }

    @Test
    fun `deleteApplication maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":12112,"message":"Application not found"}],"result":null}""")
        )

        val result = repository.deleteApplication("acct1", "missing")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Application not found")
    }
}
