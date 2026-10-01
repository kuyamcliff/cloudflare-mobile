package dev.cfmobile.app.ui.r2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.transfers.R2S3Client
import dev.cfmobile.app.core.transfers.TransferRepository
import dev.cfmobile.app.data.local.db.TransferDirection
import dev.cfmobile.app.data.local.db.TransferEntity
import dev.cfmobile.app.data.local.db.TransferState
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.components.ThinDivider
import dev.cfmobile.app.ui.explorer.formatBytes
import dev.cfmobile.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Speed from two real samples; null until there are two (spec 185: no fake numbers). */
private data class Sample(val bytes: Long, val at: Long, val speed: Double?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    transfers: Flow<List<TransferEntity>>,
    repository: TransferRepository,
    s3: R2S3Client,
    onBack: () -> Unit
) {
    val list by transfers.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateMapOf<String, Sample>() }
    androidx.compose.runtime.LaunchedEffect(list) {
        list.filter { it.state == TransferState.RUNNING }.forEach { t ->
            val prev = samples[t.id]
            if (prev == null || prev.bytes != t.transferredBytes) {
                val speed = prev?.let { p ->
                    val dt = (t.updatedAt - p.at).coerceAtLeast(1)
                    (t.transferredBytes - p.bytes) * 1000.0 / dt
                }?.takeIf { it > 0 } ?: prev?.speed
                samples[t.id] = Sample(t.transferredBytes, t.updatedAt, speed)
            }
        }
    }
    val groups = listOf(
        "In progress" to list.filter { it.state == TransferState.RUNNING || it.state == TransferState.QUEUED },
        "Paused" to list.filter { it.state == TransferState.PAUSED },
        "Failed" to list.filter { it.state == TransferState.FAILED },
        "Completed" to list.filter { it.state == TransferState.COMPLETED || it.state == TransferState.CANCELED }
    ).filter { it.second.isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfers") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { scope.launch { repository.clearFinished() } }) { Icon(Icons.Filled.DeleteSweep, "Clear finished") } }
            )
        }
    ) { padding ->
        if (list.isEmpty()) {
            Column(Modifier.padding(padding)) { EmptyState("No uploads or downloads yet. Upload from an R2 bucket to see progress here.") }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            groups.forEach { (title, items) ->
                item("h-$title") { SectionHeader(title) }
                items(items, key = { it.id }) { t ->
                    TransferRow(
                        t, samples[t.id]?.speed,
                        onPause = { scope.launch { repository.pause(t.id) } },
                        onResume = { scope.launch { repository.resume(t.id) } },
                        onCancel = { scope.launch { repository.cancel(t.id, s3) } },
                        onRemove = { scope.launch { repository.remove(t.id) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferRow(t: TransferEntity, speed: Double?, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (t.direction == TransferDirection.UPLOAD) "Upload" else "Download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Badge(
                when (t.state) {
                    TransferState.QUEUED -> if (t.wifiOnly) "Waiting for Wi-Fi" else "Queued"
                    TransferState.RUNNING -> "Running"
                    TransferState.PAUSED -> "Paused"
                    TransferState.COMPLETED -> "Done"
                    TransferState.FAILED -> "Failed"
                    TransferState.CANCELED -> "Canceled"
                },
                when (t.state) {
                    TransferState.COMPLETED -> StatusColors.success
                    TransferState.FAILED -> StatusColors.error
                    TransferState.PAUSED, TransferState.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> StatusColors.info
                }
            )
        }
        Text("${t.bucket}/${t.objectKey}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (t.totalBytes > 0 && t.state != TransferState.COMPLETED) {
            LinearProgressIndicator(progress = { (t.transferredBytes.toFloat() / t.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        val pieces = mutableListOf<String>()
        if (t.totalBytes > 0) {
            pieces += "${(t.transferredBytes * 100 / t.totalBytes).coerceIn(0, 100)}%"
            pieces += "${formatBytes(t.transferredBytes)} / ${formatBytes(t.totalBytes)}"
        } else pieces += formatBytes(t.transferredBytes)
        if (t.state == TransferState.RUNNING && speed != null && speed > 0) {
            pieces += "${formatBytes(speed.toLong())}/s"
            if (t.totalBytes > 0) {
                val eta = ((t.totalBytes - t.transferredBytes) / speed).toLong()
                pieces += "ETA " + if (eta >= 3600) "${eta / 3600}h ${eta % 3600 / 60}m" else if (eta >= 60) "${eta / 60}m ${eta % 60}s" else "${eta}s"
            }
        }
        if (t.method == TransferRepository.METHOD_S3 && t.direction == TransferDirection.UPLOAD) pieces += "multipart"
        Text(pieces.joinToString("  "), style = MaterialTheme.typography.bodySmall)
        t.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (t.state == TransferState.FAILED) MaterialTheme.colorScheme.error else StatusColors.warning) }
        Row {
            when (t.state) {
                TransferState.RUNNING, TransferState.QUEUED -> {
                    TextButton(onClick = onPause) { Text("Pause") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                TransferState.PAUSED -> {
                    TextButton(onClick = onResume) { Text("Resume") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                TransferState.FAILED -> {
                    TextButton(onClick = onResume) { Text("Retry") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
                TransferState.COMPLETED, TransferState.CANCELED -> TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
    ThinDivider()
}
