package dev.cfmobile.app.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.local.db.RequestHistoryEntity
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.MethodBadge
import dev.cfmobile.app.ui.components.NavRow
import dev.cfmobile.app.ui.components.ThinDivider
import dev.cfmobile.app.ui.theme.StatusColors
import java.text.SimpleDateFormat
import java.util.Date

/** Body of the Activity tab; the shell supplies the app bar. */
@Composable
fun ActivityContent(
    viewModel: ActivityViewModel,
    onRepeat: (RequestHistoryEntity) -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenAuditLogs: (() -> Unit)?
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = ui.tab.ordinal) {
            ActivityTab.entries.forEach { t -> Tab(selected = ui.tab == t, onClick = { viewModel.setTab(t) }, text = { Text(t.label) }) }
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusFilter.entries.forEach { f -> FilterChip(selected = ui.filter == f, onClick = { viewModel.setFilter(f) }, label = { Text(f.label) }) }
            Row(Modifier.weight(1f)) {}
            IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Clear local history") }
        }
        OutlinedTextField(
            value = ui.query, onValueChange = viewModel::setQuery, placeholder = { Text("Filter") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item("links") {
                NavRow("Transfers", "Uploads and downloads", onClick = onOpenTransfers)
                onOpenAuditLogs?.let { NavRow("Cloudflare audit log", "Server-side history recorded by Cloudflare", onClick = it) }
                Text(
                    "Local activity from this device. Headers, bodies and tokens are never recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ThinDivider()
            }
            if (ui.loaded && ui.rows.isEmpty()) {
                item("empty") {
                    EmptyState(if (ui.tab == ActivityTab.CHANGES) "No changes made from this device yet." else "No requests recorded yet.")
                }
            }
            items(ui.rows, key = { it.entry.id }) { row -> HistoryRow(row, onClick = { onRepeat(row.entry) }) }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local history?") },
            text = { Text("Removes this profile's request history from this device. Cloudflare's audit log is not affected.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clear() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HistoryRow(row: ActivityRow, onClick: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { SimpleDateFormat("MMM d, HH:mm:ss", locale) }
    val e = row.entry
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(timeFormat.format(Date(e.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
            Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val code = e.statusCode
            Badge(
                code?.toString() ?: (e.errorClass ?: "failed"),
                when {
                    code == null -> StatusColors.error
                    code in 200..399 -> StatusColors.success
                    code == 429 -> StatusColors.warning
                    else -> StatusColors.error
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MethodBadge(e.method)
            Text(e.path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.durationMillis} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    ThinDivider()
}
