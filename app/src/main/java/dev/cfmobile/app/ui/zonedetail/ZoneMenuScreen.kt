package dev.cfmobile.app.ui.zonedetail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.capabilities.Capability
import dev.cfmobile.app.core.capabilities.CapabilityRegistry
import dev.cfmobile.app.core.capabilities.CapabilityScope
import dev.cfmobile.app.core.capabilities.CapabilityStatus
import dev.cfmobile.app.core.capabilities.RoadmapPhase
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.capabilityIcon
import dev.cfmobile.app.ui.common.FreshnessLabel
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.zoneStatusColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneMenuScreen(
    zoneName: String,
    viewModel: ZoneMenuViewModel,
    onBack: () -> Unit,
    onFeatureClick: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastUpdatedAt by viewModel.lastUpdatedAt.collectAsStateWithLifecycle()
    var notImplementedInfo by remember { mutableStateOf<Capability?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        StateContent(state = state, onRetry = viewModel::load) { zone ->
            Column(Modifier.padding(padding)) {
                ZoneOverviewCard(zone, lastUpdatedAt)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                // Zone-scoped capabilities only: account-scoped products (R2, Workers, Zero
                // Trust, ...) belong to the account, not to this domain, and are reached from
                // the Dashboard instead - listing them under a single zone implied a
                // relationship that doesn't exist.
                val zoneNotImplemented = CapabilityRegistry.notYetImplementedForScope(CapabilityScope.ZONE)
                LazyColumn {
                    items(CapabilityRegistry.implementedForScope(CapabilityScope.ZONE), key = { it.id }) { capability ->
                        CapabilityRow(capability, implemented = true) {
                            capability.zoneRoute?.invoke(zone.id, zone.name)?.let(onFeatureClick)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                    if (zoneNotImplemented.isNotEmpty()) {
                        item {
                            Text(
                                "More Cloudflare products",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp)
                            )
                            Text(
                                "Not yet implemented in this app - tap to see status.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp)
                            )
                        }
                        items(zoneNotImplemented, key = { it.id }) { capability ->
                            CapabilityRow(capability, implemented = false) { notImplementedInfo = capability }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }

    notImplementedInfo?.let { capability ->
        NotImplementedDialog(capability, onDismiss = { notImplementedInfo = null })
    }
}

@Composable
private fun ZoneOverviewCard(zone: CfZone, lastUpdatedAt: Long?) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(zone.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            CopyIconButton(value = zone.name, label = "domain")
            CopyIconButton(value = zone.id, label = "zone ID")
            IconButton(onClick = { openInDashboard(context, zone.name) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Cloudflare dashboard")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(zone.status.replaceFirstChar { it.uppercase() }, zoneStatusColor(zone.status))
            zone.plan?.let {
                Text(it.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (zone.nameServers.isNotEmpty()) {
            Text(
                "Nameservers: ${zone.nameServers.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        FreshnessLabel(lastUpdatedAt, modifier = Modifier.padding(top = 4.dp))
    }
}

/** PRD §46: hand off to the Cloudflare web dashboard for anything this app doesn't (yet) cover,
 *  rather than dead-ending the user - dash.cloudflare.com resolves the account slug itself from
 *  the zone name, so no account ID needs to be known here. */
private fun openInDashboard(context: android.content.Context, zoneName: String) {
    val uri = Uri.parse("https://dash.cloudflare.com/?to=/:account/$zoneName")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser app available", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun CapabilityRow(capability: Capability, implemented: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val tint = if (implemented) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(capabilityIcon(capability), contentDescription = null, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(
                capability.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (implemented) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(capability.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (implemented && capability.migrationHint != null) {
                Text(
                    capability.migrationHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotImplementedDialog(capability: Capability, onDismiss: () -> Unit) {
    val phaseLabel = when (capability.roadmapPhase) {
        RoadmapPhase.P0 -> "Planned next"
        RoadmapPhase.P1 -> "Planned"
        RoadmapPhase.P2 -> "Planned, further out"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(capability.displayName) },
        text = {
            Text(
                when (capability.status) {
                    CapabilityStatus.LIMITATION_EXTERNAL_PLATFORM ->
                        "Cloudflare requires this workflow in the web dashboard - it can't be safely done from a mobile app."
                    else -> "This isn't implemented in this app yet. $phaseLabel on the Cloudflare product roadmap."
                }
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
