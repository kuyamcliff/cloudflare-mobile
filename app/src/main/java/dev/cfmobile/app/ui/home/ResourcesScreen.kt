package dev.cfmobile.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.ListSearchField
import dev.cfmobile.app.ui.common.capabilityIcon
import dev.cfmobile.app.ui.design.GroupTitle
import dev.cfmobile.app.ui.design.ListRow
import dev.cfmobile.app.ui.design.RowDivider
import dev.cfmobile.app.ui.design.Sections
import dev.cfmobile.app.ui.design.Space
import dev.cfmobile.app.ui.design.Tag
import dev.cfmobile.app.ui.design.groupItem
import dev.cfmobile.app.ui.navigation.Routes
import dev.cfmobile.app.ui.theme.StatusColors

/** Browse: every product the token can reach, for the working zone or the whole account. */
@Composable
fun ResourcesContent(viewModel: ResourcesViewModel, onNavigate: (String) -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var accountScope by rememberSaveable { mutableStateOf(ui.context.zone == null) }
    val groups = (if (accountScope || ui.context.zone == null) ui.accountGroups else ui.zoneGroups)
        .flatMap { it.items }
        .groupBy { Sections.of(it.capability.product, it.capability.id) }
        .toList()
        .sortedBy { Sections.rank(it.first) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xl)) {
        item("scope") {
            Column(Modifier.padding(horizontal = Space.gutter).padding(top = Space.sm)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !accountScope && ui.context.zone != null, enabled = ui.context.zone != null,
                        onClick = { accountScope = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}
                    ) { Text(ui.context.zone?.name ?: "No zone selected", maxLines = 1) }
                    SegmentedButton(
                        selected = accountScope || ui.context.zone == null,
                        onClick = { accountScope = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}
                    ) { Text("Account", maxLines = 1) }
                }
            }
        }
        item("search") {
            ListSearchField(ui.query, viewModel::setQuery, "Filter products", Modifier.padding(horizontal = 0.dp))
        }
        groups.forEach { (section, items) ->
            item("h-$section") { GroupTitle(section) }
            itemsIndexed(items, key = { _, it -> "i-${it.capability.id}" }) { i, item ->
                Column(Modifier.groupItem(i, items.size)) {
                    ListRow(
                        title = item.capability.displayName,
                        subtitle = item.capability.description,
                        icon = capabilityIcon(item.capability),
                        enabled = item.route != null,
                        trailing = when {
                            item.state == dev.cfmobile.app.core.capabilities.CapabilityState.TOKEN_RESTRICTED -> { { Tag("Not allowed", color = StatusColors.warning) } }
                            item.readOnly -> { { Tag("Read only") } }
                            else -> null
                        },
                        onClick = item.route?.let { r -> { onNavigate(r) } }
                    )
                    if (i < items.size - 1) RowDivider(inset = 64.dp)
                }
            }
        }
        if (groups.isEmpty()) {
            item("none") {
                Text(
                    if (ui.query.isNotBlank()) "Nothing matches \"${ui.query}\"." else "Nothing to show for this token yet.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.lg)
                )
            }
        }
        if (ui.hiddenCount > 0) {
            item("hidden") {
                Column(Modifier.padding(top = Space.md).groupItem(0, 1)) {
                    ListRow(
                        title = "Show products this token can't use",
                        subtitle = "${ui.hiddenCount} hidden",
                        trailing = { Switch(checked = ui.showHidden, onCheckedChange = viewModel::setShowHidden) }
                    )
                }
            }
        }
        item("api-h") { GroupTitle("Everything in the API") }
        val tools = listOf(
            Triple("All Cloudflare APIs", "Every operation in Cloudflare's schema, as forms", Icons.Filled.Api) to Routes.CATALOG,
            Triple("Analytics query", "GraphQL Analytics with ready-made templates", Icons.Filled.BarChart) to Routes.GRAPHQL,
            Triple("API Explorer", "Build any request by hand, import cURL", Icons.Filled.Terminal) to Routes.explorer()
        )
        itemsIndexed(tools, key = { _, t -> "t-${t.second}" }) { i, (t, route) ->
            Column(Modifier.groupItem(i, tools.size)) {
                ListRow(title = t.first, subtitle = t.second, icon = t.third, onClick = { onNavigate(route) })
                if (i < tools.size - 1) RowDivider(inset = 64.dp)
            }
        }
    }
}
