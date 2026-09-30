package dev.cfmobile.app.ui.diagnosticsapp

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.BuildConfig
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.api.RawRequest
import dev.cfmobile.app.core.net.ConnectivityMonitor
import dev.cfmobile.app.data.local.db.CfDatabase
import dev.cfmobile.app.data.remote.NetworkModule
import dev.cfmobile.app.data.remote.NetworkStatus
import dev.cfmobile.app.data.remote.RedactingLogInterceptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

enum class CheckState { PENDING, RUNNING, PASS, FAIL }

data class Check(val name: String, val state: CheckState = CheckState.PENDING, val detail: String = "")

data class AppDiagnosticsUiState(
    val checks: List<Check> = listOf(Check("Cloudflare API"), Check("Token verification"), Check("GraphQL Analytics API"), Check("Local storage")),
    val running: Boolean = false
)

/**
 * Diagnostics (spec 112) and the sanitized export (spec 277). The export is assembled from
 * metadata only and passed through the same path templating as the debug logs, so no token,
 * secret, cookie, header or body can appear in it.
 */
class AppDiagnosticsViewModel(
    private val raw: RawApiClient,
    private val status: NetworkStatus,
    private val connectivity: ConnectivityMonitor,
    private val database: CfDatabase,
    private val profileId: () -> String?,
    private val registryProvider: suspend () -> EndpointRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppDiagnosticsUiState())
    val uiState: StateFlow<AppDiagnosticsUiState> = _uiState.asStateFlow()

    fun run() {
        _uiState.update { AppDiagnosticsUiState(running = true) }
        viewModelScope.launch {
            set(0, CheckState.RUNNING, "")
            try {
                val r = raw.execute(RawRequest("GET", "ips"))
                set(0, if (r.isSuccess) CheckState.PASS else CheckState.FAIL, "HTTP ${r.statusCode} in ${r.durationMillis} ms")
            } catch (e: IOException) { set(0, CheckState.FAIL, e.message ?: "unreachable") }

            set(1, CheckState.RUNNING, "")
            try {
                val r = raw.execute(RawRequest("GET", "user/tokens/verify"))
                set(1, if (r.isSuccess) CheckState.PASS else CheckState.FAIL,
                    if (r.isSuccess) "Active (${r.durationMillis} ms)" else "HTTP ${r.statusCode}. Account-owned tokens are verified per account instead.")
            } catch (e: IOException) { set(1, CheckState.FAIL, e.message ?: "unreachable") }

            set(2, CheckState.RUNNING, "")
            try {
                val r = raw.execute(RawRequest("POST", NetworkModule.GRAPHQL_PATH, body = """{"query":"{ viewer { __typename } }"}"""))
                val ok = r.isSuccess && !r.body.contains("\"errors\":[{")
                set(2, if (ok) CheckState.PASS else CheckState.FAIL, "HTTP ${r.statusCode} in ${r.durationMillis} ms")
            } catch (e: IOException) { set(2, CheckState.FAIL, e.message ?: "unreachable") }

            set(3, CheckState.RUNNING, "")
            val history = profileId()?.let { database.requestHistoryDao().observe(it, 5000).first().size } ?: 0
            val transfers = database.transferDao().observeAll().first().size
            val registry = registryProvider()
            set(3, CheckState.PASS, "$history history rows, $transfers transfers, ${registry.endpoints.size} API operations")
            _uiState.update { it.copy(running = false) }
        }
    }

    private fun set(i: Int, s: CheckState, d: String) = _uiState.update { st ->
        st.copy(checks = st.checks.mapIndexed { idx, c -> if (idx == i) c.copy(state = s, detail = d) else c })
    }

    suspend fun exportJson(): String {
        val snap = status.snapshot.value
        val conn = connectivity.current()
        val registry = registryProvider()
        val recent = profileId()?.let { database.requestHistoryDao().observe(it, 50).first() }.orEmpty()
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val rows = recent.joinToString(",\n    ") { h ->
            val path = RedactingLogInterceptor.templatePath((NetworkModule.BASE_URL + h.path).toHttpUrl())
            "{\"method\":\"${h.method}\",\"path\":\"${esc(path)}\",\"status\":${h.statusCode ?: "null"},\"durationMs\":${h.durationMillis},\"error\":${h.errorClass?.let { "\"${esc(it)}\"" } ?: "null"}}"
        }
        val checks = _uiState.value.checks.joinToString(",\n    ") { "{\"name\":\"${esc(it.name)}\",\"state\":\"${it.state}\",\"detail\":\"${esc(it.detail)}\"}" }
        return """
{
  "app": {"version": "${BuildConfig.VERSION_NAME}", "code": ${BuildConfig.VERSION_CODE}, "buildType": "${BuildConfig.BUILD_TYPE}"},
  "device": {"android": "${Build.VERSION.RELEASE}", "sdk": ${Build.VERSION.SDK_INT}, "model": "${esc(Build.MODEL)}"},
  "schema": {"revision": "${registry.schemaRevision}", "generatedAt": "${registry.generatedAt}"},
  "network": {"online": ${conn.online}, "metered": ${conn.metered}, "wifi": ${conn.wifi}},
  "api": {"lastStatus": ${snap.lastStatusCode ?: "null"}, "lastLatencyMs": ${snap.lastLatencyMillis ?: "null"}, "rateLimitRemaining": ${snap.rateLimitRemaining ?: "null"}, "requestsThisSession": ${snap.requestsThisSession}},
  "checks": [
    $checks
  ],
  "recentRequests": [
    $rows
  ],
  "note": "Sanitized: contains no tokens, secrets, headers, bodies or query strings."
}
""".trim()
    }
}
