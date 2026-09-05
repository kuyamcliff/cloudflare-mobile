package dev.cfmobile.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.AccountSwitcherSheet

/** The app's landing screen once a token is connected - an account overview plus a menu into
 *  every main destination, so it's immediately clear there's more to the app than one list
 *  (this replaced dropping straight into the Zones screen as the app's root). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onDomainsClick: () -> Unit,
    onAccountMembersClick: (accountId: String) -> Unit,
    onSecurityClick: () -> Unit,
    onManageAccountsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAccountSwitcher by remember { mutableStateOf(false) }
    val primaryAccountId = uiState.cfAccounts.firstOrNull()?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable(enabled = viewModel.accounts.size > 1) { showAccountSwitcher = true }) {
                        Text("Cloudflare")
                        viewModel.activeAccountLabel?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (viewModel.accounts.size > 1) {
                        IconButton(onClick = { showAccountSwitcher = true }) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch account")
                        }
                    }
                    IconButton(onClick = onManageAccountsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            DashboardSummaryCard(
                accountEmail = viewModel.activeAccountEmail,
                zoneCount = uiState.zoneCount,
                isLoading = uiState.isLoading,
                loadError = uiState.loadError,
                onClick = onDomainsClick
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            LazyColumn {
                items(dashboardMenuItems(hasAccountAccess = primaryAccountId != null), key = { it.title }) { item ->
                    DashboardMenuRow(item) {
                        when (item.title) {
                            "Domains" -> onDomainsClick()
                            "Account Members" -> primaryAccountId?.let(onAccountMembersClick)
                            "Security" -> onSecurityClick()
                            "Manage accounts" -> onManageAccountsClick()
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }

    if (showAccountSwitcher) {
        AccountSwitcherSheet(
            accounts = viewModel.accounts,
            activeId = viewModel.activeAccountId,
            onSelect = { id -> viewModel.switchAccount(id); showAccountSwitcher = false },
            onAddAccount = { showAccountSwitcher = false; onManageAccountsClick() },
            onDismiss = { showAccountSwitcher = false }
        )
    }
}

@Composable
private fun DashboardSummaryCard(
    accountEmail: String?,
    zoneCount: Int?,
    isLoading: Boolean,
    loadError: String?,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                when {
                    isLoading -> "Loading domains…"
                    loadError != null -> "Couldn't load domains"
                    zoneCount == null -> "Domains"
                    zoneCount == 1 -> "1 domain"
                    else -> "$zoneCount domains"
                },
                style = MaterialTheme.typography.titleLarge
            )
            accountEmail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isLoading) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class DashboardMenuItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val enabled: Boolean = true
)

private fun dashboardMenuItems(hasAccountAccess: Boolean): List<DashboardMenuItem> = listOf(
    DashboardMenuItem("Domains", "DNS, SSL/TLS, WAF, caching, analytics, and more for each zone", Icons.Filled.Dns),
    DashboardMenuItem(
        "Account Members", "Invite and manage who has access to this Cloudflare account",
        Icons.Filled.People, enabled = hasAccountAccess
    ),
    DashboardMenuItem("Security", "App lock and screenshot protection for this device", Icons.Filled.Security),
    DashboardMenuItem("Manage accounts", "Switch, add, or remove connected API tokens", Icons.Filled.ManageAccounts)
)

@Composable
private fun DashboardMenuRow(item: DashboardMenuItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val tint = if (item.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(item.icon, contentDescription = null, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.enabled) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
