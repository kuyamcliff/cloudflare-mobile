package dev.cfmobile.app.ui.diagnosticsapp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.NetworkStatus
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.KeyValueRow
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.theme.StatusColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDiagnosticsScreen(viewModel: AppDiagnosticsViewModel, status: NetworkStatus, onBack: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snap by status.snapshot.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val json = viewModel.exportJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Diagnostics") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Checks")
            ui.checks.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium)
                        if (c.detail.isNotBlank()) Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (c.state) {
                        CheckState.PASS -> Badge("Pass", StatusColors.success)
                        CheckState.FAIL -> Badge("Fail", StatusColors.error)
                        CheckState.RUNNING -> Badge("Running", StatusColors.info)
                        CheckState.PENDING -> Badge("Not run", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::run, enabled = !ui.running, modifier = Modifier.fillMaxWidth()) { Text(if (ui.running) "Running" else "Run checks") }
                OutlinedButton(onClick = { export.launch("cloudflare-control-diagnostics.json") }, modifier = Modifier.fillMaxWidth()) { Text("Export sanitized report") }
            }
            SectionHeader("This session")
            KeyValueRow("Requests", "${snap.requestsThisSession}")
            KeyValueRow("Last status", snap.lastStatusCode?.toString() ?: "None yet")
            KeyValueRow("Last latency", snap.lastLatencyMillis?.let { "$it ms" } ?: "None yet")
            KeyValueRow("Rate limit left", snap.rateLimitRemaining?.toString() ?: "Not reported by Cloudflare")
            Text(
                "Cloudflare allows 1,200 API requests per 5 minutes per token and 300 GraphQL queries per 5 minutes by default. The remaining count is shown only when Cloudflare sends it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)
            )
        }
    }
}
