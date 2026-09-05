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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
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

/** One row in the Dashboard's menu. Unlike CapabilityRegistry (which drives the per-zone menu
 *  and needs to describe capabilities independent of any specific navigation callback), this
 *  carries its own onClick directly - the Dashboard's destinations are a small, hand-written
 *  set, not a growing registry, so a lambda per item is simpler than a lookup-by-id dispatch. */
private data class DashboardMenuItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {}
)

private data class DashboardSection(val title: String?, val items: List<DashboardMenuItem>)

/** The app's landing screen once a token is connected - an account overview plus a menu into
 *  every main destination, so it's immediately clear there's more to the app than one list
 *  (this replaced dropping straight into the Zones screen as the app's root). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onDomainsClick: () -> Unit,
    onAccountMembersClick: (accountId: String) -> Unit,
    onAuditLogsClick: (accountId: String) -> Unit,
    onLoadBalancingClick: (accountId: String) -> Unit,
    onR2Click: (accountId: String) -> Unit,
    onKvClick: (accountId: String) -> Unit,
    onD1Click: (accountId: String) -> Unit,
    onWorkersClick: (accountId: String) -> Unit,
    onPagesClick: (accountId: String) -> Unit,
    onAccessClick: (accountId: String) -> Unit,
    onGatewayClick: (accountId: String) -> Unit,
    onTunnelsClick: (accountId: String) -> Unit,
    onSecurityClick: () -> Unit,
    onManageAccountsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAccountSwitcher by remember { mutableStateOf(false) }
    val primaryAccountId = uiState.cfAccounts.firstOrNull()?.id
    val hasAccountAccess = primaryAccountId != null

    val sections = listOf(
        DashboardSection(
            title = null,
            items = listOf(
                DashboardMenuItem(
                    "Domains", "DNS, SSL/TLS, WAF, caching, analytics, and more for each zone",
                    Icons.Filled.Dns, onClick = onDomainsClick
                )
            )
        ),
        DashboardSection(
            title = "Account",
            items = listOf(
                DashboardMenuItem(
                    "Account Members", "Invite and manage who has access to this Cloudflare account",
                    Icons.Filled.People, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onAccountMembersClick) }
                ),
                DashboardMenuItem(
                    "Audit Logs", "Who changed what, and when",
                    Icons.Filled.History, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onAuditLogsClick) }
                )
            )
        ),
        DashboardSection(
            title = "Traffic",
            items = listOf(
                DashboardMenuItem(
                    "Load Balancing", "Pools, origins, and load balancers across your zones",
                    Icons.Filled.CallSplit, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onLoadBalancingClick) }
                )
            )
        ),
        DashboardSection(
            title = "Developer Platform",
            items = listOf(
                DashboardMenuItem(
                    "R2 Storage", "Object storage buckets",
                    Icons.Filled.Inventory2, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onR2Click) }
                ),
                DashboardMenuItem(
                    "Workers KV", "Key-value namespaces for Workers",
                    Icons.Filled.Key, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onKvClick) }
                ),
                DashboardMenuItem(
                    "D1", "Serverless SQL databases",
                    Icons.Filled.Storage, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onD1Click) }
                ),
                DashboardMenuItem(
                    "Workers", "Deployed Worker scripts",
                    Icons.Filled.Bolt, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onWorkersClick) }
                ),
                DashboardMenuItem(
                    "Pages", "Static site and full-stack deployments",
                    Icons.Filled.Language, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onPagesClick) }
                )
            )
        ),
        DashboardSection(
            title = "Zero Trust",
            items = listOf(
                DashboardMenuItem(
                    "Access", "Applications and email-based policies",
                    Icons.Filled.Shield, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onAccessClick) }
                ),
                DashboardMenuItem(
                    "Gateway", "DNS policies to block or allow domains",
                    Icons.Filled.Dns, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onGatewayClick) }
                ),
                DashboardMenuItem(
                    "Tunnels", "Register Cloudflare Tunnels for this account",
                    Icons.Filled.Router, enabled = hasAccountAccess,
                    onClick = { primaryAccountId?.let(onTunnelsClick) }
                )
            )
        ),
        DashboardSection(
            title = "Settings",
            items = listOf(
                DashboardMenuItem("Security", "App lock and screenshot protection for this device", Icons.Filled.Security, onClick = onSecurityClick),
                DashboardMenuItem("Manage accounts", "Switch, add, or remove connected API tokens", Icons.Filled.ManageAccounts, onClick = onManageAccountsClick)
            )
        )
    )

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
                sections.forEach { section ->
                    section.title?.let { title ->
                        item {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)
                            )
                        }
                    }
                    items(section.items, key = { it.title }) { item ->
                        DashboardMenuRow(item)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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

@Composable
private fun DashboardMenuRow(item: DashboardMenuItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = item.onClick)
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
