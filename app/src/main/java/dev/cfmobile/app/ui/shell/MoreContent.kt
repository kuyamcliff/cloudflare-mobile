package dev.cfmobile.app.ui.shell

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.BuildConfig
import dev.cfmobile.app.ui.components.NavRow
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.navigation.Routes

@Composable
fun MoreContent(accountId: String?, profileLabel: String?, fingerprint: String?, schemaRevision: String?, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    LazyColumn(Modifier.fillMaxSize()) {
        item("profile") {
            SectionHeader("Profile")
            NavRow(profileLabel ?: "No profile", fingerprint?.let { "Token fingerprint $it" }, Icons.Filled.ManageAccounts, onClick = { onNavigate(Routes.SETTINGS) })
            NavRow("What this token can access", "Permissions, scopes and available features", Icons.Filled.Policy, onClick = { onNavigate(Routes.TOKEN_ACCESS) })
            if (accountId != null) NavRow("API tokens", "Create, edit, roll and revoke tokens", Icons.Filled.VpnKey, onClick = { onNavigate(Routes.apiTokens(accountId)) })
        }
        item("tools") {
            SectionHeader("Tools")
            NavRow("API Explorer", "Send any Cloudflare API request with this token", Icons.Filled.Api, onClick = { onNavigate(Routes.explorer()) })
            NavRow("All Cloudflare APIs", "Browse every operation in Cloudflare's schema", Icons.Filled.ViewList, onClick = { onNavigate(Routes.CATALOG) })
            NavRow("GraphQL Analytics", "Query analytics datasets", Icons.Filled.BarChart, onClick = { onNavigate(Routes.GRAPHQL) })
            NavRow("Transfers", "R2 uploads and downloads", Icons.Filled.CloudSync, onClick = { onNavigate(Routes.TRANSFERS) })
        }
        item("app") {
            SectionHeader("App")
            NavRow("Security", "App lock, biometrics and screenshot protection", Icons.Filled.Lock, onClick = { onNavigate(Routes.SECURITY) })
            NavRow("Settings", "Profiles, appearance, transfers and data", Icons.Filled.Settings, onClick = { onNavigate(Routes.SETTINGS) })
            NavRow("Diagnostics", "Connectivity, rate limits and a sanitized report", Icons.Filled.Build, onClick = { onNavigate(Routes.DIAGNOSTICS) })
        }
        item("external") {
            SectionHeader("Cloudflare")
            NavRow("Open Cloudflare dashboard", "For settings Cloudflare only offers in the dashboard", Icons.Filled.OpenInBrowser, onClick = { open("https://dash.cloudflare.com/") })
            NavRow("Cloudflare system status", "cloudflarestatus.com", Icons.Filled.OpenInBrowser, onClick = { open("https://www.cloudflarestatus.com/") })
            NavRow("Cloudflare API documentation", "developers.cloudflare.com/api", Icons.Filled.OpenInBrowser, onClick = { open("https://developers.cloudflare.com/api/") })
        }
        item("about") {
            SectionHeader("About")
            Text(
                "Cloudflare Control ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.GIT_COMMIT})\n" +
                    (schemaRevision?.let { "API schema $it\n" } ?: "") +
                    "An independent client for the Cloudflare API. Not made or endorsed by Cloudflare, Inc. " +
                    "Your tokens are stored only on this device and are sent only to Cloudflare.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
