package dev.cfmobile.app.ui.magicnetwork

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.RefreshableStateContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicNetworkScreen(viewModel: MagicNetworkViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Magic WAN") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = MagicTab.entries.indexOf(uiState.tab)) {
                MagicTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    MagicTab.GRE -> "GRE"
                                    MagicTab.IPSEC -> "IPsec"
                                    MagicTab.ROUTES -> "Routes"
                                }
                            )
                        }
                    )
                }
            }
            Text(
                "Read-only inventory. Changing network routing from a phone isn't something this app offers - these are Enterprise features configured with Cloudflare's networking team.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when (uiState.tab) {
                MagicTab.GRE -> RefreshableStateContent(uiState.greTunnels, uiState.isRefreshing, viewModel::refresh) { tunnels ->
                    if (tunnels.isEmpty()) {
                        EmptyState("No GRE tunnels configured")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(tunnels, key = { it.id }) { tunnel ->
                                ReadOnlyListRow(
                                    icon = Icons.Filled.Hub,
                                    title = tunnel.name,
                                    subtitle = tunnelEndpointsLabel(tunnel.cloudflareEndpoint, tunnel.customerEndpoint),
                                    detail = tunnel.description ?: tunnel.interfaceAddress
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }

                MagicTab.IPSEC -> RefreshableStateContent(uiState.ipsecTunnels, uiState.isRefreshing, viewModel::refresh) { tunnels ->
                    if (tunnels.isEmpty()) {
                        EmptyState("No IPsec tunnels configured")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(tunnels, key = { it.id }) { tunnel ->
                                ReadOnlyListRow(
                                    icon = Icons.Filled.Lock,
                                    title = tunnel.name,
                                    subtitle = tunnelEndpointsLabel(tunnel.cloudflareEndpoint, tunnel.customerEndpoint),
                                    detail = tunnel.description ?: tunnel.interfaceAddress
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }

                MagicTab.ROUTES -> RefreshableStateContent(uiState.routes, uiState.isRefreshing, viewModel::refresh) { routes ->
                    if (routes.isEmpty()) {
                        EmptyState("No static routes configured")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(routes, key = { it.id }) { route ->
                                ReadOnlyListRow(
                                    icon = Icons.Filled.Route,
                                    title = route.prefix,
                                    monospaceTitle = true,
                                    subtitle = route.nexthop?.let { "via $it" },
                                    detail = listOfNotNull(
                                        route.priority?.let { "Priority $it" },
                                        route.description
                                    ).joinToString(" · ").ifBlank { null }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}
