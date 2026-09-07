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

class AuditLogsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AuditLogsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = AuditLogsRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listEntries parses actor, action, and resource fields`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[
                    {
                        "id":"log1",
                        "action":{"type":"update","result":true},
                        "actor":{"id":"u1","email":"admin@example.com","ip":"203.0.113.5"},
                        "resource":{"id":"zone1","type":"zone","product":"dns"},
                        "when":"2024-01-01T00:00:00Z"
                    }
                ]}"""
            )
        )

        val result = repository.listEntries("acct1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val entries = (result as ApiResult.Success).data
        assertThat(entries).hasSize(1)
        assertThat(entries[0].actor?.email).isEqualTo("admin@example.com")
        assertThat(entries[0].action?.type).isEqualTo("update")
        assertThat(entries[0].resource?.product).isEqualTo("dns")
    }

    @Test
    fun `listEntries hits the account audit_logs endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        repository.listEntries("acct1")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/accounts/acct1/audit_logs?page=1&per_page=50")
    }

    @Test
    fun `listEntries maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Invalid API token"}],"result":null}""")
        )

        val result = repository.listEntries("acct1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Invalid API token")
    }
}
