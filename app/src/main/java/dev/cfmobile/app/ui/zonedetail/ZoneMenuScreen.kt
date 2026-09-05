package dev.cfmobile.app.ui.zonedetail

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
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.capabilities.Capability
import dev.cfmobile.app.core.capabilities.CapabilityRegistry
import dev.cfmobile.app.core.capabilities.CapabilityStatus
import dev.cfmobile.app.core.capabilities.RoadmapPhase
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.ui.common.StateContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneMenuScreen(
    zoneName: String,
    viewModel: ZoneMenuViewModel,
    onBack: () -> Unit,
    onFeatureClick: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                ZoneOverviewCard(zone)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                LazyColumn {
                    items(CapabilityRegistry.implemented(), key = { it.id }) { capability ->
                        CapabilityRow(capability, implemented = true) {
                            capability.zoneRoute?.let { route -> onFeatureClick(route(zone.id, zone.name)) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
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
                    items(CapabilityRegistry.notYetImplemented(), key = { it.id }) { capability ->
                        CapabilityRow(capability, implemented = false) { notImplementedInfo = capability }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
private fun ZoneOverviewCard(zone: CfZone) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(zone.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "Status: ${zone.status}" + (zone.plan?.let { " · Plan: ${it.name}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (zone.nameServers.isNotEmpty()) {
            Text(
                "Nameservers: ${zone.nameServers.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

private fun capabilityIcon(capability: Capability) = when (capability.id) {
    "dns.records" -> Icons.Filled.Dns
    "ssl.tls" -> Icons.Filled.Lock
    "firewall.legacy", "waf.rulesets" -> Icons.Filled.Shield
    "page_rules" -> Icons.AutoMirrored.Filled.Rule
    "caching" -> Icons.Filled.Http
    "analytics" -> Icons.Filled.Analytics
    else -> Icons.Filled.Extension
}
