package dev.cfmobile.app.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.remote.ApiHealth
import dev.cfmobile.app.data.remote.NetworkStatus
import dev.cfmobile.app.core.net.ConnectionState
import dev.cfmobile.app.ui.common.AccountSwitcherSheet
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.StateFlow

enum class ShellTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    RESOURCES("Resources", Icons.Filled.Apps),
    ACTIVITY("Activity", Icons.Filled.History),
    MORE("More", Icons.Filled.MoreHoriz)
}

/**
 * The top-level shell (spec 84): four destinations, a compact app bar that always names the
 * active profile, and a status indicator that appears only when something is wrong (spec 104).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    profile: AccountSummary?,
    profiles: List<AccountSummary>,
    networkStatus: NetworkStatus,
    connection: StateFlow<ConnectionState>,
    onSwitchProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    home: @Composable () -> Unit,
    resources: @Composable () -> Unit,
    activity: @Composable () -> Unit,
    more: @Composable () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(ShellTab.HOME) }
    var switcher by rememberSaveable { mutableStateOf(false) }
    val snapshot by networkStatus.snapshot.collectAsState()
    val conn by connection.collectAsState()
    val health = if (!conn.online) ApiHealth.UNREACHABLE else networkStatus.health(snapshot).let { if (it == ApiHealth.UNREACHABLE) ApiHealth.OK else it }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { switcher = true }) {
                        Text(if (tab == ShellTab.HOME) "Cloudflare Control" else tab.label)
                        Text(profile?.label ?: "No profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    when (health) {
                        ApiHealth.OK -> Unit
                        ApiHealth.RATE_LIMITED -> Badge("Rate limited", StatusColors.warning)
                        ApiHealth.POSSIBLE_API_ISSUES -> Badge("Possible API issues", StatusColors.warning)
                        ApiHealth.UNREACHABLE -> Badge("Offline", StatusColors.error)
                    }
                    if (tab == ShellTab.HOME) IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search everywhere") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                ShellTab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                ShellTab.HOME -> home()
                ShellTab.RESOURCES -> resources()
                ShellTab.ACTIVITY -> activity()
                ShellTab.MORE -> more()
            }
        }
    }
    if (switcher) {
        AccountSwitcherSheet(
            accounts = profiles,
            activeId = profile?.id,
            onSelect = { id -> switcher = false; onSwitchProfile(id) },
            onAddAccount = { switcher = false; onAddProfile() },
            onDismiss = { switcher = false }
        )
    }
}
