package dev.cfmobile.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.core.net.ConnectionState
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.remote.ApiHealth
import dev.cfmobile.app.data.remote.NetworkStatus
import dev.cfmobile.app.ui.design.Banner
import dev.cfmobile.app.ui.design.Space
import dev.cfmobile.app.ui.design.Tag
import dev.cfmobile.app.ui.theme.CfTheme
import dev.cfmobile.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.StateFlow

enum class ShellTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    BROWSE("Browse", Icons.Outlined.GridView, Icons.Filled.GridView),
    ACTIVITY("Activity", Icons.Outlined.History, Icons.Filled.History)
}

/**
 * The top-level shell. The command bar sits just above the tabs on every destination, where a
 * thumb rests: typing what you want is always one tap away. The app bar names the account the
 * user is acting on, and a status tag appears only when something is wrong (spec 104).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    profile: AccountSummary?,
    accountName: String?,
    networkStatus: NetworkStatus,
    connection: StateFlow<ConnectionState>,
    onSwitchAccount: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCommand: () -> Unit,
    onReconnect: () -> Unit,
    home: @Composable () -> Unit,
    browse: @Composable () -> Unit,
    activity: @Composable () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(ShellTab.HOME) }
    val snapshot by networkStatus.snapshot.collectAsState()
    val conn by connection.collectAsState()
    val health = if (!conn.online) ApiHealth.UNREACHABLE else networkStatus.health(snapshot).let { if (it == ApiHealth.UNREACHABLE) ApiHealth.OK else it }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onSwitchAccount)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .semantics { contentDescription = "Account ${accountName ?: "none"}. Switch account" }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(accountName ?: "Cloudflare Control", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(profile?.label ?: "No profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    when (health) {
                        ApiHealth.OK -> Unit
                        ApiHealth.RATE_LIMITED -> Tag("Rate limited", color = StatusColors.warning)
                        ApiHealth.POSSIBLE_API_ISSUES -> Tag("API issues", color = StatusColors.warning)
                        ApiHealth.UNREACHABLE -> Tag("Offline", color = StatusColors.error)
                    }
                    Avatar(profile?.label, onOpenProfile)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                CommandBar(onOpenCommand)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    ShellTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = CfTheme.colors.accentSoft,
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("tab-${t.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (snapshot.tokenRejected) {
                Banner(
                    "Cloudflare rejected this token. It may have been revoked or expired.",
                    MaterialTheme.colorScheme.error,
                    actionLabel = "Reconnect",
                    onAction = onReconnect
                )
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    ShellTab.HOME -> home()
                    ShellTab.BROWSE -> browse()
                    ShellTab.ACTIVITY -> activity()
                }
            }
        }
    }
}

/** Looks like a field, opens the command surface. */
@Composable
fun CommandBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm)
            .clip(RoundedCornerShape(26.dp))
            .background(CfTheme.colors.card)
            .border(1.dp, CfTheme.colors.accent.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .height(52.dp)
            .padding(horizontal = Space.lg)
            .testTag("command-bar")
            .semantics { contentDescription = "What do you want to do?" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = CfTheme.colors.accent, modifier = Modifier.size(20.dp))
        Text("What do you want to do?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Avatar(label: String?, onClick: () -> Unit) {
    val initial = label?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.padding(end = Space.md, start = Space.sm).size(34.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Profile and settings" },
        contentAlignment = Alignment.Center
    ) {
        Text(initial, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary)
    }
}
