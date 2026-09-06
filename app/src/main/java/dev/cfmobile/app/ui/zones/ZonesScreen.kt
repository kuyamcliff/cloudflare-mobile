package dev.cfmobile.app.ui.zones

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.ui.common.AccountSwitcherSheet
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.FreshnessLabel
import dev.cfmobile.app.ui.common.ListSearchField
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.zoneStatusColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesScreen(
    viewModel: ZonesViewModel,
    onBack: () -> Unit,
    onZoneClick: (CfZone) -> Unit,
    onSettingsClick: () -> Unit,
    onAddAccount: () -> Unit = onSettingsClick
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val lastUpdatedAt by viewModel.lastUpdatedAt.collectAsStateWithLifecycle()
    var showAccountSwitcher by remember { mutableStateOf(false) }

    // Zones are account-scoped; if the active account changed while this screen was
    // backgrounded (e.g. switched from Settings), refresh on resume so stale zones from the
    // previous account never linger under the new account's context (PRD §49).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable(enabled = viewModel.accounts.size > 1) { showAccountSwitcher = true }) {
                        Text("Zones")
                        viewModel.activeAccountLabel?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (viewModel.accounts.size > 1) {
                        IconButton(onClick = { showAccountSwitcher = true }) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch account")
                        }
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListSearchField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = "Search domains"
            )
            FreshnessLabel(lastUpdatedAt, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))

            RefreshableStateContent(
                state = state,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                onReauthenticate = onSettingsClick
            ) { zones ->
                if (zones.isEmpty()) {
                    EmptyState("No domains found for this account")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(zones, key = { it.id }) { zone ->
                            ZoneRow(zone, onClick = { onZoneClick(zone) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }

    if (showAccountSwitcher) {
        AccountSwitcherSheet(
            accounts = viewModel.accounts,
            activeId = viewModel.activeAccountId,
            onSelect = { id -> viewModel.switchAccount(id); showAccountSwitcher = false },
            onAddAccount = { showAccountSwitcher = false; onAddAccount() },
            onDismiss = { showAccountSwitcher = false }
        )
    }
}

@Composable
private fun ZoneRow(zone: CfZone, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(zone.name, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(zone.status.replaceFirstChar { it.uppercase() }, zoneStatusColor(zone.status))
                zone.plan?.let {
                    Text(
                        it.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
