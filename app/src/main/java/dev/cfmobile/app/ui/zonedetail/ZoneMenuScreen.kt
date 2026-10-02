package dev.cfmobile.app.ui.zonedetail

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.capabilities.CapabilityRegistry
import dev.cfmobile.app.core.capabilities.CapabilityScope
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.FreshnessLabel
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.capabilityIcon
import dev.cfmobile.app.ui.common.zoneStatusColor
import dev.cfmobile.app.ui.design.GroupTitle
import dev.cfmobile.app.ui.design.ListRow
import dev.cfmobile.app.ui.design.RowDivider
import dev.cfmobile.app.ui.design.Sections
import dev.cfmobile.app.ui.design.Space
import dev.cfmobile.app.ui.design.groupItem
import dev.cfmobile.app.ui.theme.CfTheme

/**
 * A zone's home: what state it is in, the switches people reach for in an incident, and every
 * zone feature grouped by what it is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneMenuScreen(
    zoneName: String,
    viewModel: ZoneMenuViewModel,
    onBack: () -> Unit,
    onFeatureClick: (String) -> Unit,
    onAsk: () -> Unit = {},
    onAllOperations: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastUpdatedAt by viewModel.lastUpdatedAt.collectAsStateWithLifecycle()
    val toggles by viewModel.toggles.collectAsStateWithLifecycle()
    val purge by viewModel.purge.collectAsStateWithLifecycle()
    val purgeBlocked by viewModel.purgeBlocked.collectAsStateWithLifecycle()
    var confirmPurge by remember { mutableStateOf(false) }
    var confirmToggle by remember { mutableStateOf<Pair<QuickToggle, Boolean>?>(null) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(zoneName, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onAsk) { Icon(Icons.Filled.AutoAwesome, "Ask about this zone", tint = CfTheme.colors.accent) }
                    IconButton(onClick = { openInDashboard(context, zoneName) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in Cloudflare dashboard") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        StateContent(state = state, onRetry = viewModel::load) { zone ->
            val sections = CapabilityRegistry.implementedForScope(CapabilityScope.ZONE)
                .groupBy { Sections.of(it.product, it.id) }
                .toList()
                .sortedBy { Sections.rank(it.first) }
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Space.xxl)) {
                item("overview") { Overview(zone, lastUpdatedAt) }

                item("quick-h") { GroupTitle("Quick controls") }
                itemsIndexed(toggles, key = { _, t -> "t-${t.settingId}" }) { i, t ->
                    Column(Modifier.groupItem(i, toggles.size + 1)) {
                        ListRow(
                            title = t.label,
                            subtitle = t.error ?: t.detail,
                            trailing = {
                                when {
                                    t.saving || (t.value == null && t.error == null) -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    t.value != null -> Switch(checked = t.isOn, onCheckedChange = { on -> confirmToggle = t to on })
                                    else -> Unit
                                }
                            }
                        )
                        RowDivider()
                    }
                }
                item("purge") {
                    Column(Modifier.groupItem(toggles.size, toggles.size + 1)) {
                        ListRow(
                            title = "Purge everything",
                            subtitle = when {
                                purgeBlocked -> "This token doesn't have the Cache Purge permission"
                                else -> purge ?: "Remove every cached file for this zone"
                            },
                            icon = Icons.Filled.CleaningServices,
                            enabled = !purgeBlocked,
                            onClick = { confirmPurge = true },
                            showChevron = false
                        )
                    }
                }

                sections.forEach { (section, caps) ->
                    item("s-$section") { GroupTitle(section) }
                    itemsIndexed(caps, key = { _, c -> "c-${c.id}" }) { i, cap ->
                        Column(Modifier.groupItem(i, caps.size)) {
                            ListRow(
                                title = cap.displayName,
                                subtitle = cap.description,
                                icon = capabilityIcon(cap),
                                onClick = { cap.zoneRoute?.invoke(zone.id, zone.name)?.let(onFeatureClick) }
                            )
                            if (i < caps.size - 1) RowDivider(inset = 64.dp)
                        }
                    }
                }
                item("all-h") { GroupTitle("Everything else") }
                item("all") {
                    Column(Modifier.groupItem(0, 1)) {
                        ListRow(
                            title = "All zone operations",
                            subtitle = "Every zone-level operation in Cloudflare's API, as forms",
                            icon = Icons.Filled.Api,
                            onClick = onAllOperations
                        )
                    }
                }
            }
        }
    }

    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text("Purge everything on $zoneName?") },
            text = { Text("Every cached file is removed. Your origin serves the next requests, so expect a short load increase.") },
            confirmButton = { TextButton(onClick = { confirmPurge = false; viewModel.purgeEverything() }) { Text("Purge", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmPurge = false }) { Text("Cancel") } }
        )
    }
    confirmToggle?.let { (t, on) ->
        AlertDialog(
            onDismissRequest = { confirmToggle = null },
            title = { Text("${if (on) "Turn on" else "Turn off"} ${t.label.replaceFirstChar { it.lowercase() }}?") },
            text = { Text("This changes $zoneName right away for every visitor.") },
            confirmButton = { TextButton(onClick = { confirmToggle = null; viewModel.setToggle(t.settingId, on) }) { Text(if (on) "Turn on" else "Turn off") } },
            dismissButton = { TextButton(onClick = { confirmToggle = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Overview(zone: CfZone, lastUpdatedAt: Long?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter + 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(zone.name, style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatusPill(if (zone.paused) "Paused" else zone.status.replaceFirstChar { it.uppercase() }, zoneStatusColor(if (zone.paused) "pending" else zone.status))
            zone.plan?.let { Text(it.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            CopyIconButton(value = zone.id, label = "zone ID")
        }
        if (zone.nameServers.isNotEmpty()) {
            Text(zone.nameServers.joinToString("   "), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FreshnessLabel(lastUpdatedAt)
    }
}

/** Hands off to the Cloudflare dashboard for anything only it offers (spec 46). */
private fun openInDashboard(context: android.content.Context, zoneName: String) {
    val uri = "https://dash.cloudflare.com/?to=/:account/$zoneName".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser app available", Toast.LENGTH_SHORT).show()
    }
}
