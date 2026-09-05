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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.ui.common.StateContent

private data class FeatureEntry(val label: String, val subtitle: String, val icon: ImageVector, val route: (String) -> String)

private val features = listOf(
    FeatureEntry("DNS Records", "A, CNAME, MX, TXT and more", Icons.Filled.Dns) { dev.cfmobile.app.ui.navigation.Routes.dns(it) },
    FeatureEntry("SSL/TLS", "Encryption mode, HTTPS, TLS version", Icons.Filled.Lock) { dev.cfmobile.app.ui.navigation.Routes.ssl(it) },
    FeatureEntry("Firewall", "Firewall rules and IP access rules", Icons.Filled.Shield) { dev.cfmobile.app.ui.navigation.Routes.firewall(it) },
    FeatureEntry("Page Rules", "URL-based configuration overrides", Icons.AutoMirrored.Filled.Rule) { dev.cfmobile.app.ui.navigation.Routes.pageRules(it) },
    FeatureEntry("Caching", "Cache level, dev mode, purge cache", Icons.Filled.Http) { dev.cfmobile.app.ui.navigation.Routes.caching(it) },
    FeatureEntry("Analytics", "Requests, bandwidth, threats", Icons.Filled.Analytics) { dev.cfmobile.app.ui.navigation.Routes.analytics(it) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneMenuScreen(
    zoneName: String,
    viewModel: ZoneMenuViewModel,
    onBack: () -> Unit,
    onFeatureClick: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                    items(features) { feature ->
                        FeatureRow(feature) { onFeatureClick(feature.route(zone.id)) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
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
private fun FeatureRow(feature: FeatureEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(feature.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(feature.label, style = MaterialTheme.typography.bodyLarge)
            Text(feature.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
