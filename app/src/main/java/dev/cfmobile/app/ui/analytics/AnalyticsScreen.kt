package dev.cfmobile.app.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.AnalyticsDashboard
import dev.cfmobile.app.ui.common.StateContent
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rangeHours by viewModel.rangeHours.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = rangeHours == 24L, onClick = { viewModel.selectRange(24) }, label = { Text("24 hours") })
                FilterChip(selected = rangeHours == 168L, onClick = { viewModel.selectRange(168) }, label = { Text("7 days") })
            }

            StateContent(state = state, onRetry = viewModel::load) { dashboard ->
                AnalyticsGrid(dashboard)
            }
        }
    }
}

@Composable
private fun AnalyticsGrid(dashboard: AnalyticsDashboard) {
    val totals = dashboard.totals
    val stats = listOf(
        "Requests" to formatCount(totals?.requests?.all ?: 0.0),
        "Bandwidth" to formatBytes(totals?.bandwidth?.all ?: 0.0),
        "Threats blocked" to formatCount(totals?.threats?.all ?: 0.0),
        "Unique visitors" to formatCount(totals?.uniques?.all ?: 0.0)
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stats) { (label, value) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(value, style = MaterialTheme.typography.titleLarge)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatCount(value: Double): String {
    if (value < 1000) return value.toLong().toString()
    val units = listOf("K", "M", "B")
    var v = value
    var unitIndex = -1
    while (v >= 1000 && unitIndex < units.lastIndex) {
        v /= 1000
        unitIndex++
    }
    return "%.1f%s".format(v, units[unitIndex])
}

private fun formatBytes(bytes: Double): String {
    if (bytes < 1024) return "${bytes.toLong()} B"
    val units = listOf("KB", "MB", "GB", "TB")
    val exponent = (ln(bytes) / ln(1024.0)).toInt().coerceAtMost(units.size)
    val value = bytes / 1024.0.pow(exponent.toDouble())
    return "%.1f %s".format(value, units[exponent - 1])
}
