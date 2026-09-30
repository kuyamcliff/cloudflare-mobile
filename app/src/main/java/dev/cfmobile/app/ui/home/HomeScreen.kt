package dev.cfmobile.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.ui.common.zoneStatusColor
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.NavRow
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.components.ThinDivider
import dev.cfmobile.app.ui.navigation.Routes
import dev.cfmobile.app.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeContent(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var accountSheet by remember { mutableStateOf(false) }
    var zoneSheet by remember { mutableStateOf(false) }
    val ctx = ui.context

    LazyColumn(Modifier.fillMaxSize()) {
        ui.expiryWarning?.let { w ->
            item("expiry") {
                Text(w, style = MaterialTheme.typography.bodySmall, color = StatusColors.warning, modifier = Modifier.padding(16.dp))
            }
        }
        item("context") {
            SectionHeader("Working in")
            NavRow(
                title = ctx.account?.name ?: if (ui.loading) "Loading accounts" else "No account visible to this token",
                supporting = "Account" + (ctx.account?.id?.let { " · $it" } ?: ""),
                enabled = ui.accounts.size > 1,
                onClick = { accountSheet = true }
            )
            ThinDivider()
            NavRow(
                title = ctx.zone?.name ?: "No zone selected",
                supporting = if (ctx.zone != null) "Zone · tap to change" else "Choose a zone for DNS, SSL, cache and rules",
                onClick = { zoneSheet = true }
            )
            ui.accountsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item("actions") {
            SectionHeader("Quick actions")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.quickActions.forEach { a -> AssistChip(onClick = { onNavigate(a.route) }, label = { Text(a.label) }) }
            }
        }
        ctx.zone?.let { zone ->
            item("zone-open") {
                SectionHeader("Current zone")
                NavRow(title = zone.name, supporting = "DNS, SSL/TLS, security, rules, caching and analytics", onClick = { onNavigate(Routes.zoneMenu(zone.id, zone.name)) })
            }
        }
        if (ctx.favoriteZones.isNotEmpty()) {
            item("fav-h") { SectionHeader("Favorite zones") }
            items(ctx.favoriteZones, key = { "fav-" + it.id }) { z -> ZoneRefRow(z, favorite = true, onOpen = { viewModel.selectZoneRef(z); onNavigate(Routes.zoneMenu(z.id, z.name)) }, onStar = { viewModel.toggleFavorite(z) }) }
        }
        val recents = ctx.recentZones.filter { r -> ctx.favoriteZones.none { it.id == r.id } }
        if (recents.isNotEmpty()) {
            item("rec-h") { SectionHeader("Recent zones") }
            items(recents.take(5), key = { "rec-" + it.id }) { z -> ZoneRefRow(z, favorite = false, onOpen = { viewModel.selectZoneRef(z); onNavigate(Routes.zoneMenu(z.id, z.name)) }, onStar = { viewModel.toggleFavorite(z) }) }
        }
        item("all") {
            SectionHeader("Zones in this account")
            NavRow(
                title = when {
                    ui.zonesLoading && ui.zones.isEmpty() -> "Loading zones"
                    ui.zones.isEmpty() -> "No zones visible"
                    ui.zones.size >= 200 -> "200+ zones"
                    else -> "${ui.zones.size} zone${if (ui.zones.size == 1) "" else "s"}"
                },
                supporting = "Browse, search and add zones",
                onClick = { onNavigate(Routes.ZONES) }
            )
        }
    }

    if (accountSheet) {
        ModalBottomSheet(onDismissRequest = { accountSheet = false }) {
            Text("Accounts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(ui.accounts, key = { it.id }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.selectAccount(a); accountSheet = false }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyLarge)
                            Text(a.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (a.id == ctx.account?.id) Badge("Current", MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (zoneSheet) {
        ModalBottomSheet(onDismissRequest = { zoneSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Zones in ${ctx.account?.name ?: "all accounts"}", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = ui.zoneQuery, onValueChange = viewModel::searchZones, placeholder = { Text("Search zones") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                if (ctx.zone != null) TextButton(onClick = { viewModel.clearZone(); zoneSheet = false }) { Text("Clear zone selection") }
            }
            if (ui.zonesLoading) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            ui.zonesError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(ui.zones, key = { it.id }) { z ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.selectZone(z); zoneSheet = false }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(z.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        z.plan?.name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Badge(z.status, zoneStatusColor(z.status))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneRefRow(ref: NamedRef, favorite: Boolean, onOpen: () -> Unit, onStar: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(ref.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onStar) {
            Icon(if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = if (favorite) "Remove from favorites" else "Add to favorites")
        }
    }
}
