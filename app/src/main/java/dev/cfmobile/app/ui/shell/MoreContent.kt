package dev.cfmobile.app.ui.shell

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.BuildConfig
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.ui.design.Group
import dev.cfmobile.app.ui.design.ListRow
import dev.cfmobile.app.ui.design.RowDivider
import dev.cfmobile.app.ui.design.ScreenTitle
import dev.cfmobile.app.ui.design.Space
import dev.cfmobile.app.ui.design.Tag
import dev.cfmobile.app.ui.navigation.Routes

/** Profile, token, tools and app settings, reached from the avatar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    accountId: String?,
    profile: AccountSummary?,
    profiles: List<AccountSummary>,
    schemaRevision: String?,
    onSwitchProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        MoreContent(accountId, profile, profiles, schemaRevision, onSwitchProfile, onAddProfile, onNavigate, Modifier.padding(padding))
    }
}

@Composable
fun MoreContent(
    accountId: String?,
    profile: AccountSummary?,
    profiles: List<AccountSummary>,
    schemaRevision: String?,
    onSwitchProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xxl)) {
        item("title") { ScreenTitle(profile?.label ?: "No profile", subtitle = profile?.fingerprint?.let { "Token $it" }) }
        item("profiles") {
            Group(title = "Profiles") {
                profiles.forEach { p ->
                    ListRow(
                        title = p.label,
                        subtitle = p.fingerprint?.let { "Token $it" },
                        icon = Icons.Filled.Person,
                        trailing = if (p.id == profile?.id) ({ Tag("Active") }) else null,
                        onClick = if (p.id == profile?.id) null else ({ onSwitchProfile(p.id) })
                    )
                    RowDivider(inset = 64.dp)
                }
                ListRow(title = "Connect another token", icon = Icons.Filled.Add, onClick = onAddProfile)
            }
        }
        item("token") {
            Group(title = "Token") {
                ListRow("What this token can do", subtitle = "Permissions, scopes and hidden features", icon = Icons.Filled.Policy, onClick = { onNavigate(Routes.TOKEN_ACCESS) })
                if (accountId != null) {
                    RowDivider(inset = 64.dp)
                    ListRow("API tokens", subtitle = "Create, edit, roll and revoke", icon = Icons.Filled.VpnKey, onClick = { onNavigate(Routes.apiTokens(accountId)) })
                }
            }
        }
        item("tools") {
            Group(title = "Tools") {
                ListRow("All Cloudflare APIs", subtitle = "Every operation, as a form", icon = Icons.Filled.Api, onClick = { onNavigate(Routes.CATALOG) })
                RowDivider(inset = 64.dp)
                ListRow("API Explorer", subtitle = "Raw requests and cURL import", icon = Icons.Filled.Terminal, onClick = { onNavigate(Routes.explorer()) })
                RowDivider(inset = 64.dp)
                ListRow("Analytics query", subtitle = "GraphQL Analytics", icon = Icons.Filled.BarChart, onClick = { onNavigate(Routes.GRAPHQL) })
                RowDivider(inset = 64.dp)
                ListRow("Transfers", subtitle = "R2 uploads and downloads", icon = Icons.Filled.CloudSync, onClick = { onNavigate(Routes.TRANSFERS) })
            }
        }
        item("app") {
            Group(title = "App") {
                ListRow("App lock", subtitle = "Biometrics and screenshot protection", icon = Icons.Filled.Lock, onClick = { onNavigate(Routes.SECURITY) })
                RowDivider(inset = 64.dp)
                ListRow("Settings", subtitle = "Appearance, transfers, data on this device", icon = Icons.Filled.Settings, onClick = { onNavigate(Routes.SETTINGS) })
                RowDivider(inset = 64.dp)
                ListRow("Diagnostics", subtitle = "Connectivity, rate limits, sanitized report", icon = Icons.Filled.Build, onClick = { onNavigate(Routes.DIAGNOSTICS) })
            }
        }
        item("cf") {
            Group(title = "Cloudflare") {
                ListRow("Dashboard", subtitle = "dash.cloudflare.com", icon = Icons.AutoMirrored.Filled.OpenInNew, onClick = { open("https://dash.cloudflare.com/") }, showChevron = false)
                RowDivider(inset = 64.dp)
                ListRow("System status", subtitle = "cloudflarestatus.com", icon = Icons.AutoMirrored.Filled.OpenInNew, onClick = { open("https://www.cloudflarestatus.com/") }, showChevron = false)
                RowDivider(inset = 64.dp)
                ListRow("API documentation", subtitle = "developers.cloudflare.com/api", icon = Icons.AutoMirrored.Filled.OpenInNew, onClick = { open("https://developers.cloudflare.com/api/") }, showChevron = false)
            }
        }
        item("about") {
            Column(Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.xl)) {
                Text(
                    "Cloudflare Control ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.GIT_COMMIT})" +
                        (schemaRevision?.let { " · API schema $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "An independent client for the Cloudflare API, not made or endorsed by Cloudflare, Inc. " +
                        "Tokens stay on this device and are sent only to Cloudflare.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
        }
    }
}
