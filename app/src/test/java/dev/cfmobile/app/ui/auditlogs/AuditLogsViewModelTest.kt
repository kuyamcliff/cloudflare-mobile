package dev.cfmobile.app.ui.auditlogs

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.AuditLogAction
import dev.cfmobile.app.data.remote.dto.AuditLogActor
import dev.cfmobile.app.data.remote.dto.AuditLogEntry
import dev.cfmobile.app.data.remote.dto.AuditLogResource
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.AuditLogsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AuditLogsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private suspend fun AuditLogsViewModel.awaitLoaded() = uiState.first { it.entries !is UiState.Loading }

    @Test
    fun `loads entries on init`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"log1","action":{"type":"update"},"actor":{"email":"a@example.com"}}]}"""
            )
        )
        val viewModel = AuditLogsViewModel("acct1", AuditLogsRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat((state.entries as UiState.Data).value).hasSize(1)
    }

    @Test
    fun `failure surfaces the Cloudflare error message`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":9109,"message":"Invalid API token"}],"result":null}""")
        )
        val viewModel = AuditLogsViewModel("acct1", AuditLogsRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat(state.entries).isInstanceOf(UiState.Error::class.java)
        assertThat((state.entries as UiState.Error).message).contains("Invalid API token")
    }

    @Test
    fun `actor label falls back from email to id to Unknown`() {
        assertThat(auditActorLabel(AuditLogEntry(actor = AuditLogActor(email = "a@example.com", id = "u1")))).isEqualTo("a@example.com")
        assertThat(auditActorLabel(AuditLogEntry(actor = AuditLogActor(id = "u1")))).isEqualTo("u1")
        assertThat(auditActorLabel(AuditLogEntry(actor = null))).isEqualTo("Unknown")
    }

    @Test
    fun `action label flags a failed result`() {
        assertThat(auditActionLabel(AuditLogEntry(action = AuditLogAction(type = "update", result = true)))).isEqualTo("update")
        assertThat(auditActionLabel(AuditLogEntry(action = AuditLogAction(type = "update", result = false)))).isEqualTo("update (failed)")
    }

    @Test
    fun `resource label combines product and type, or is null when absent`() {
        assertThat(auditResourceLabel(AuditLogEntry(resource = AuditLogResource(product = "dns", type = "record")))).isEqualTo("dns · record")
        assertThat(auditResourceLabel(AuditLogEntry(resource = null))).isNull()
    }
}
