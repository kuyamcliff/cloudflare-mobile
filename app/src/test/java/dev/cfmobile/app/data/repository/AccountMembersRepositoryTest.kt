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

class AccountMembersRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AccountMembersRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = AccountMembersRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listMembers parses embedded roles`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[
                    {"id":"m1","user":{"id":"u1","email":"a@example.com"},"status":"accepted","roles":[{"id":"r1","name":"Administrator","description":"Full access"}]}
                ]}"""
            )
        )

        val result = repository.listMembers("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val members = (result as ApiResult.Success).data
        assertThat(members).hasSize(1)
        assertThat(members[0].user.email).isEqualTo("a@example.com")
        assertThat(members[0].roles.single().name).isEqualTo("Administrator")
    }

    @Test
    fun `listRoles hits the account roles endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","name":"Administrator","description":"Full access"}]}"""))

        repository.listRoles("acct1")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/roles")
    }

    @Test
    fun `inviteMember sends email and role ids in the request body`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"m2","user":{"id":"u2","email":"b@example.com"},"status":"pending","roles":[]}}"""))

        val result = repository.inviteMember("acct1", "b@example.com", listOf("r1", "r2"))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/accounts/acct1/members")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"email\":\"b@example.com\"")
        assertThat(body).contains("\"r1\"")
        assertThat(body).contains("\"r2\"")
    }

    @Test
    fun `removeMember maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Cannot remove the last administrator"}],"result":null}""")
        )

        val result = repository.removeMember("acct1", "m1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Cannot remove the last administrator")
    }
}
