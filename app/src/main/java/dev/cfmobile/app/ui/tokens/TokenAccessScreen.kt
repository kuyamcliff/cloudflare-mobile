package dev.cfmobile.app.ui.tokens

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.core.capabilities.DiscoveryPhase
import dev.cfmobile.app.core.capabilities.PolicySource
import dev.cfmobile.app.core.capabilities.TokenKind
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.KeyValueRow
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.components.ThinDivider
import dev.cfmobile.app.ui.explorer.CapabilityBadge
import dev.cfmobile.app.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenAccessScreen(viewModel: TokenAccessViewModel, profileLabel: String?, onBack: () -> Unit, onOpenFeature: (String) -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val caps = ui.discovery.capabilities
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("What this token can access")
                        profileLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, "Rediscover permissions") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ui.discovery.phase == DiscoveryPhase.VERIFYING || ui.discovery.phase == DiscoveryPhase.READING_POLICIES) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Discovering available controls", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item("identity") {
                    SectionHeader("Token")
                    KeyValueRow("Type", when (caps?.identity?.kind) { TokenKind.ACCOUNT -> "Account-owned token"; TokenKind.USER -> "User token"; null -> "Unknown" })
                    caps?.identity?.tokenId?.let { KeyValueRow("Token ID", it, monospace = true, copyable = true) }
                    caps?.identity?.status?.let { KeyValueRow("Status", it) }
                    KeyValueRow("Expires", caps?.identity?.expiresOn ?: "Never or unknown")
                    ui.discovery.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
                    if (caps?.source == PolicySource.OBSERVED_ONLY) {
                        Text(
                            "This token cannot read its own policies (it lacks API Tokens Read), so access is learned from Cloudflare's responses as you use the app. Nothing is probed with write requests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    ui.summary?.takeIf { it.known }?.let { s ->
                        SectionHeader("Summary")
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Stat("Read", s.read)
                            Stat("Edit", s.write)
                            Stat("Products", ui.availableProducts)
                        }
                        caps?.grantedPermissionNames?.let { names ->
                            SectionHeader("Granted permissions (${names.size})")
                            names.forEach { n -> Text(n, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)) }
                        }
                    }
                    SectionHeader("Features")
                    OutlinedTextField(
                        value = ui.query, onValueChange = viewModel::setQuery, placeholder = { Text("Search features or permissions") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
                val q = ui.query.trim()
                val list = ui.features.filter {
                    q.isEmpty() || it.capability.displayName.contains(q, true) || it.capability.product.contains(q, true) || it.permissions.any { p -> p.contains(q, true) }
                }.sortedBy { order(it.state) }
                items(list, key = { it.capability.id }) { f ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(f.capability.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            CapabilityBadge(f.state)
                            when (f.canWrite) {
                                true -> Badge("Can edit", StatusColors.success)
                                false -> if (f.state == CapabilityState.AVAILABLE_READ) Badge("Read-only", StatusColors.info)
                                null -> Unit
                            }
                        }
                        Text(f.capability.product + if (f.capability.zoneRoute != null) " · zone" else " · account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (f.permissions.isNotEmpty() && f.state == CapabilityState.TOKEN_RESTRICTED) {
                            Text("Needs one of: " + f.permissions.take(6).joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ThinDivider()
                }
            }
        }
    }
}

private fun order(s: CapabilityState) = when (s) {
    CapabilityState.AVAILABLE_WRITE -> 0
    CapabilityState.AVAILABLE_READ -> 1
    CapabilityState.UNKNOWN -> 2
    else -> 3
}

@Composable
private fun Stat(label: String, value: Int) {
    Column {
        Text("$value", style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
