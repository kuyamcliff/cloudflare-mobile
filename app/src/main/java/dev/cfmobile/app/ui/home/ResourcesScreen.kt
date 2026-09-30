package dev.cfmobile.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.ui.common.capabilityIcon
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.NavRow
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.navigation.Routes
import dev.cfmobile.app.ui.theme.StatusColors

@Composable
fun ResourcesContent(viewModel: ResourcesViewModel, onNavigate: (String) -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize()) {
        item("search") {
            OutlinedTextField(
                value = ui.query, onValueChange = viewModel::setQuery, placeholder = { Text("Filter features") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (ui.hiddenCount > 0) {
                TextButton(onClick = { viewModel.setShowHidden(!ui.showHidden) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (ui.showHidden) "Hide features this token cannot use" else "${ui.hiddenCount} features hidden: this token lacks the permission. Show")
                }
            }
        }
        val zone = ui.context.zone
        if (zone == null) {
            item("nozone") {
                SectionHeader("Zone")
                Text("Select a zone on Home to see DNS, SSL/TLS, security, rules and caching.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
        } else {
            item("zone-h") { SectionHeader(zone.name) }
            ui.zoneGroups.forEach { g ->
                items(g.items, key = { "z-" + it.capability.id }) { item -> ResourceRow(item, onNavigate) }
            }
        }
        ui.context.account?.let { account ->
            item("acct-h") { SectionHeader(account.name) }
            ui.accountGroups.forEach { g ->
                item("g-" + g.title) {
                    Text(g.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                }
                items(g.items, key = { "a-" + it.capability.id }) { item -> ResourceRow(item, onNavigate) }
            }
        }
        item("all-apis") {
            SectionHeader("Everything else")
            NavRow("All Cloudflare APIs", "Every operation in Cloudflare's API schema, including products without a dedicated screen", onClick = { onNavigate(Routes.CATALOG) })
        }
    }
}

@Composable
private fun ResourceRow(item: ResourceItem, onNavigate: (String) -> Unit) {
    NavRow(
        title = item.capability.displayName,
        supporting = item.capability.description,
        icon = capabilityIcon(item.capability),
        badge = {
            when {
                item.state == CapabilityState.TOKEN_RESTRICTED -> Badge("No permission", StatusColors.error)
                item.readOnly -> Badge("Read-only", StatusColors.info)
            }
        },
        onClick = { item.route?.let(onNavigate) }
    )
}
