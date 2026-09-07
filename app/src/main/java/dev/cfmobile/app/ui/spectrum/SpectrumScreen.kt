package dev.cfmobile.app.ui.spectrum

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SettingsEthernet
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectrumScreen(zoneName: String, viewModel: SpectrumViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Spectrum", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                "TCP/UDP applications proxied through Cloudflare. Creating one means choosing origin, protocol, edge IP and TLS settings together, which is a desktop-sized form - so this lists and removes them only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            RefreshableStateContent(
                state = uiState.apps,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { apps ->
                if (apps.isEmpty()) {
                    EmptyState("No Spectrum applications on this zone")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(apps, key = { it.id }) { app ->
                            val title = spectrumAppLabel(app)
                            DeletableListRow(
                                icon = Icons.Filled.SettingsEthernet,
                                title = title,
                                monospaceTitle = true,
                                subtitle = spectrumRouteLabel(app),
                                detail = listOfNotNull(
                                    app.trafficType,
                                    if (app.ipFirewall == true) "IP firewall on" else null
                                ).joinToString(" · ").ifBlank { null },
                                isDeleting = uiState.deletingId == app.id,
                                deleteContentDescription = "Delete application",
                                confirmTitle = "Delete Spectrum application?",
                                confirmText = "\"$title\" will be permanently deleted and traffic to it will stop being proxied. This can't be undone.",
                                onDelete = { viewModel.delete(app) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}
