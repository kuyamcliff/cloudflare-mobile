package dev.cfmobile.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.command.SavedAction
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.ui.common.iconFor
import dev.cfmobile.app.ui.common.zoneStatusColor
import dev.cfmobile.app.ui.design.Banner
import dev.cfmobile.app.ui.design.GroupTitle
import dev.cfmobile.app.ui.design.IconTile
import dev.cfmobile.app.ui.design.ListRow
import dev.cfmobile.app.ui.design.RowDivider
import dev.cfmobile.app.ui.design.Space
import dev.cfmobile.app.ui.design.StatusDot
import dev.cfmobile.app.ui.design.groupItem
import dev.cfmobile.app.ui.navigation.Routes
import dev.cfmobile.app.ui.theme.CfTheme
import dev.cfmobile.app.ui.theme.StatusColors

/**
 * Home: the zones in the working account, what the user pinned, what they did last, and the
 * products the token can reach. Everything else is one search away in the command bar.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    viewModel: HomeViewModel,
    pins: List<SavedAction>,
    recents: List<SavedAction>,
    onNavigate: (String) -> Unit,
    onOpenSaved: (SavedAction) -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val ctx = ui.context
    val favorites = ctx.favoriteZones.map { it.id }.toSet()
    val zones = ui.zones.sortedWith(compareBy({ it.id !in favorites }, { it.id != ctx.zone?.id }, { it.name }))
    val shown = zones.take(MAX_ZONES)

    PullToRefreshBox(isRefreshing = ui.loading && ui.accounts.isNotEmpty(), onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xl)) {
            ui.expiryWarning?.let { w -> item("expiry") { Banner(w, StatusColors.warning) } }
            ui.accountsError?.let { e -> item("acc-err") { Banner(e, MaterialTheme.colorScheme.error, actionLabel = "Retry", onAction = viewModel::refresh) } }

            item("zones-h") {
                GroupTitle(
                    if (ui.zones.size > MAX_ZONES) "Zones (${if (ui.zones.size >= 200) "200+" else ui.zones.size})" else "Zones",
                    action = { TextButton(onClick = { onNavigate(Routes.ZONES) }) { Text(if (ui.zones.size > MAX_ZONES) "All" else "Manage") } }
                )
            }
            when {
                ui.zonesLoading && ui.zones.isEmpty() -> item("zones-loading") { Placeholder("Loading zones") }
                ui.zonesError != null && ui.zones.isEmpty() -> item("zones-err") { Placeholder(ui.zonesError ?: "Couldn't load zones") }
                ui.zones.isEmpty() -> item("zones-empty") { Placeholder("No zones in this account yet. Type \"add zone example.com\" below to add one.") }
                else -> itemsIndexed(shown, key = { _, z -> "z-${z.id}" }) { i, z ->
                    val ref = NamedRef(z.id, z.name, z.account?.id)
                    Column(Modifier.groupItem(i, shown.size)) {
                        ListRow(
                            title = z.name,
                            subtitle = listOfNotNull(z.plan?.name, if (z.paused) "Paused" else z.status.takeIf { it != "active" }).joinToString(" · ").ifBlank { null },
                            leading = { StatusDot(zoneStatusColor(if (z.paused) "pending" else z.status), size = 10.dp) },
                            trailing = {
                                IconButton(onClick = { viewModel.toggleFavorite(ref) }) {
                                    Icon(
                                        if (z.id in favorites) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (z.id in favorites) "Remove ${z.name} from favorites" else "Add ${z.name} to favorites",
                                        tint = if (z.id in favorites) CfTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = { viewModel.selectZone(z); onNavigate(Routes.zoneMenu(z.id, z.name)) }
                        )
                        if (i < shown.size - 1) RowDivider(inset = 42.dp)
                    }
                }
            }

            if (pins.isNotEmpty()) {
                item("pins-h") { GroupTitle("Pinned") }
                itemsIndexed(pins, key = { _, a -> "pin-${a.key}" }) { i, a ->
                    Column(Modifier.groupItem(i, pins.size)) {
                        ListRow(title = a.title, subtitle = a.context, icon = Icons.Filled.PushPin, onClick = { onOpenSaved(a) })
                        if (i < pins.size - 1) RowDivider(inset = 64.dp)
                    }
                }
            }

            if (ui.quickActions.isNotEmpty()) {
                item("prod-h") { GroupTitle("Shortcuts") }
                item("prod") { Shortcuts(ui.quickActions, onNavigate) }
            }

            if (recents.isNotEmpty()) {
                item("rec-h") { GroupTitle("Recent") }
                val list = recents.take(5)
                itemsIndexed(list, key = { _, a -> "rec-${a.key}" }) { i, a ->
                    Column(Modifier.groupItem(i, list.size)) {
                        ListRow(title = a.title, subtitle = a.context, icon = Icons.Filled.History, onClick = { onOpenSaved(a) })
                        if (i < list.size - 1) RowDivider(inset = 64.dp)
                    }
                }
            }
        }
    }
}

private const val MAX_ZONES = 6

@Composable
private fun Placeholder(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.groupItem(0, 1).fillMaxWidth().padding(Space.lg)
    )
}

/** Up to eight product shortcuts as a 4-column grid. */
@Composable
private fun Shortcuts(actions: List<QuickAction>, onNavigate: (String) -> Unit) {
    val rows = actions.take(8).chunked(4)
    Column(Modifier.padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { a ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(CfTheme.colors.card)
                            .clickable { onNavigate(a.route) }.heightIn(min = 84.dp).padding(vertical = Space.md, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconTile(iconFor(a.id))
                        Text(a.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
