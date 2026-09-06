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

    @Test
    fun `listIdentityProviders hits the account identity providers endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"p1","name":"PIN","type":"onetimepin"}]}"""))

        val result = AccessRepository(testApi(server)).listIdentityProviders("acct1")

        assertThat((result as ApiResult.Success).data.single().type).isEqualTo("onetimepin")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/access/identity_providers")
    }

    @Test
    fun `createOneTimePinProvider sends an empty config, since that type has none`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"p1","name":"PIN","type":"onetimepin"}}"""))

        AccessRepository(testApi(server)).createOneTimePinProvider("acct1", "PIN")

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"type\":\"onetimepin\"")
        assertThat(body).contains("\"config\":{}")
    }

    @Test
    fun `createServiceToken returns the client secret Cloudflare only sends once`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"t1","name":"ci","client_id":"abc.access","client_secret":"s3cret"}}"""
            )
        )

        val result = AccessRepository(testApi(server)).createServiceToken("acct1", "ci")

        assertThat((result as ApiResult.Success).data.clientSecret).isEqualTo("s3cret")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/accounts/acct1/access/service_tokens")
    }

    @Test
    fun `deleteServiceToken targets the token id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))

        AccessRepository(testApi(server)).deleteServiceToken("acct1", "t1")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/accounts/acct1/access/service_tokens/t1")
    }
}
