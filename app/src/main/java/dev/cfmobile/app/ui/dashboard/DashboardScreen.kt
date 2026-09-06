package dev.cfmobile.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.capabilities.CapabilityRegistry
import dev.cfmobile.app.ui.common.AccountSwitcherSheet
import dev.cfmobile.app.ui.common.capabilityIcon

/** One row in the Dashboard's menu. */
private data class DashboardMenuItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    /** Extra text the search box also matches on, so "storage" finds R2 even though neither
     *  its name nor description contains that word. */
    val keywords: String = "",
    val onClick: () -> Unit = {}
)

private data class DashboardSection(val title: String?, val items: List<DashboardMenuItem>)

private fun DashboardMenuItem.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return title.contains(q, ignoreCase = true) ||
        description.contains(q, ignoreCase = true) ||
        keywords.contains(q, ignoreCase = true)
}

/**
 * The app's landing screen once a token is connected - an account overview plus a menu into
 * every main destination, so it's immediately clear there's more to the app than one list
 * (this replaced dropping straight into the Zones screen as the app's root).
 *
 * The account-scoped rows are generated from [CapabilityRegistry] rather than hand-listed:
 * adding Cloudflare product coverage means adding a registry row plus its screen, and this
 * menu picks it up automatically. That is also why navigation is a single [onNavigate] taking
 * a route, instead of one callback per feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onDomainsClick: () -> Unit,
    onNavigate: (route: String) -> Unit,
    onSecurityClick: () -> Unit,
    onManageAccountsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val primaryAccountId = uiState.cfAccounts.firstOrNull()?.id
    val hasAccountAccess = primaryAccountId != null

    // Account-scoped capabilities, grouped under their Cloudflare product area. Registry order
    // is preserved so related products stay together rather than sorting alphabetically into
    // an arbitrary jumble.
    val capabilitySections = remember(primaryAccountId) {
        CapabilityRegistry.accountMenu().map { (product, capabilities) ->
            DashboardSection(
                title = product,
                items = capabilities.map { capability ->
                    DashboardMenuItem(
                        title = capability.displayName,
                        description = capability.description,
                        icon = capabilityIcon(capability),
                        enabled = primaryAccountId != null,
                        keywords = "${capability.product} ${capability.id}",
                        onClick = {
                            primaryAccountId?.let { accountId ->
                                capability.accountRoute?.invoke(accountId)?.let(onNavigate)
                            }
                        }
                    )
                }
            )
        }
    }

    val sections = buildList {
        add(
            DashboardSection(
                title = null,
                items = listOf(
                    DashboardMenuItem(
                        "Domains", "DNS, SSL/TLS, WAF, caching, analytics, and more for each zone",
                        Icons.Filled.Dns, keywords = "zones domain dns ssl waf cache analytics",
                        onClick = onDomainsClick
                    )
                )
            )
        )
        addAll(capabilitySections)
        add(
            DashboardSection(
                title = "Settings",
                items = listOf(
                    DashboardMenuItem(
                        "Security", "App lock and screenshot protection for this device",
                        Icons.Filled.Security, keywords = "lock biometric pin screenshot",
                        onClick = onSecurityClick
                    ),
                    DashboardMenuItem(
                        "Manage accounts", "Switch, add, or remove connected API tokens",
                        Icons.Filled.ManageAccounts, keywords = "token account switch add remove",
                        onClick = onManageAccountsClick
                    )
                )
            )
        )
    }

    val visibleSections = sections
        .map { section -> section to section.items.filter { it.matches(query) } }
        .filter { (_, items) -> items.isNotEmpty() }

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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search features") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (visibleSections.isEmpty()) {
                Text(
                    "Nothing matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    visibleSections.forEach { (section, items) ->
                        section.title?.let { title ->
                            item(key = "header-$title") {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)
                                )
                            }
                        }
                        items(items, key = { it.title }) { item ->
                            DashboardMenuRow(item)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
