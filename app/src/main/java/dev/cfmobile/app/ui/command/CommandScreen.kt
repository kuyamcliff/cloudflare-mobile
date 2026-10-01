package dev.cfmobile.app.ui.command

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.command.ActionDraft
import dev.cfmobile.app.core.command.CommandEngine
import dev.cfmobile.app.core.command.CommandHit
import dev.cfmobile.app.core.command.SavedAction
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.ui.common.iconFor
import dev.cfmobile.app.ui.design.GroupTitle
import dev.cfmobile.app.ui.design.IconTile
import dev.cfmobile.app.ui.design.ListRow
import dev.cfmobile.app.ui.design.MethodLabel
import dev.cfmobile.app.ui.design.Pill
import dev.cfmobile.app.ui.design.RowDivider
import dev.cfmobile.app.ui.design.Space
import dev.cfmobile.app.ui.design.Tag
import dev.cfmobile.app.ui.design.groupItem
import dev.cfmobile.app.ui.theme.CfTheme
import dev.cfmobile.app.ui.theme.StatusColors

@Composable
fun CommandScreen(
    viewModel: CommandViewModel,
    onClose: () -> Unit,
    onOpenDraft: (ActionDraft) -> Unit,
    onNavigate: (String) -> Unit
) {
    val s by viewModel.ui.collectAsStateWithLifecycle()
    var accountSheet by remember { mutableStateOf(false) }
    var zoneSheet by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(s.event) {
        when (val e = s.event) {
            is CommandEvent.Open -> { viewModel.consumeEvent(); onOpenDraft(e.draft) }
            is CommandEvent.Navigate -> { viewModel.consumeEvent(); onNavigate(e.route) }
            null -> Unit
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = Space.sm, end = Space.gutter, top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Pill(
                    label = s.context.account?.name ?: "Account",
                    trailingIcon = Icons.Filled.ExpandMore,
                    onClick = { accountSheet = true }
                )
                Pill(
                    label = s.results.mentionedZone?.name ?: s.context.zone?.name ?: "No zone",
                    leadingIcon = Icons.Filled.Language,
                    trailingIcon = Icons.Filled.ExpandMore,
                    selected = s.results.mentionedZone != null,
                    onClick = { zoneSheet = true }
                )
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = Space.sm, bottom = Space.lg)) {
            if (s.query.isBlank()) {
                idle(s, viewModel)
            } else {
                results(s, viewModel)
            }
        }

        InputBar(
            value = s.query,
            onValueChange = viewModel::setQuery,
            onSubmit = viewModel::submit,
            planning = s.planner is PlannerState.Planning,
            focus = focus
        )
    }

    if (accountSheet) {
        PickerSheet("Account", s.accounts, s.context.account?.id, allowNone = false, onPick = { it?.let(viewModel::switchAccount); accountSheet = false }, onDismiss = { accountSheet = false })
    }
    if (zoneSheet) {
        PickerSheet("Zone", s.zones, s.context.zone?.id, allowNone = true, onPick = { viewModel.switchZone(it); zoneSheet = false }, onDismiss = { zoneSheet = false })
    }
}

private fun LazyListScope.idle(s: CommandUiState, vm: CommandViewModel) {
    if (s.pins.isNotEmpty()) {
        item("pins-h") { GroupTitle("Pinned") }
        savedRows("pin", s.pins, Icons.Filled.PushPin, vm::openSaved)
    }
    if (s.recents.isNotEmpty()) {
        item("rec-h") { GroupTitle("Recent") }
        savedRows("rec", s.recents.take(6), Icons.Filled.History, vm::openSaved)
    }
    item("try-h") { GroupTitle("Try") }
    val zone = s.context.zone?.name ?: "example.com"
    val examples = listOf(
        "purge cache on $zone",
        "add A record www 192.0.2.1",
        "turn on development mode",
        "block 203.0.113.7",
        "set ssl to strict",
        "create kv namespace sessions",
        "forward hi@$zone to me@gmail.com",
        "list waiting rooms",
        "ask ai what does a CNAME record do"
    )
    itemsIndexed(examples, key = { _, e -> "ex-$e" }) { i, e ->
        Column(Modifier.groupItem(i, examples.size)) {
            ListRow(title = e, icon = Icons.Filled.Search, onClick = { vm.setQuery(e) }, showChevron = false)
            if (i < examples.size - 1) RowDivider(inset = 64.dp)
        }
    }
    item("hint") {
        Text(
            "Type a task in your own words, the name of anything you own, or any Cloudflare API operation. " +
                "Nothing runs until you review it. Add name=value to set any field directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.lg)
        )
    }
}

private fun LazyListScope.savedRows(prefix: String, list: List<SavedAction>, icon: androidx.compose.ui.graphics.vector.ImageVector, onOpen: (SavedAction) -> Unit) {
    itemsIndexed(list, key = { _, a -> "$prefix-${a.key}" }) { i, a ->
        Column(Modifier.groupItem(i, list.size)) {
            ListRow(title = a.title, subtitle = listOfNotNull(a.context, a.method).joinToString(" · "), icon = icon, onClick = { onOpen(a) })
            if (i < list.size - 1) RowDivider(inset = 64.dp)
        }
    }
}

private fun LazyListScope.results(s: CommandUiState, vm: CommandViewModel) {
    val r = s.results
    val best = r.best
    if (best != null) {
        item("best") { BestMatch(best, onClick = { vm.choose(best) }) }
    }
    if (r.suggestPlanner || r.isEmpty || s.planner !is PlannerState.Idle) {
        item("planner") { PlannerRow(s.planner, onPlan = vm::plan, onCancel = vm::cancelPlan) }
    }
    val actions = r.actions.filter { it.key != best?.key }
    if (actions.isNotEmpty()) {
        item("a-h") { GroupTitle("Do") }
        hits("a", actions, vm)
    }
    val goes: List<CommandHit> = (r.places + r.resources).filter { it.key != best?.key }.sortedByDescending { it.score }.take(8)
    if (goes.isNotEmpty()) {
        item("g-h") { GroupTitle("Go to") }
        hits("g", goes, vm)
    }
    val ops = r.operations.filter { it.key != best?.key }.take(12)
    if (ops.isNotEmpty()) {
        item("o-h") { GroupTitle("Cloudflare API") }
        hits("o", ops, vm)
    }
    if (r.isEmpty && s.ready) {
        item("none") {
            Text(
                "No direct match. Workers AI can turn this into a request for you to review.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.md)
            )
        }
    }
}

private fun LazyListScope.hits(prefix: String, list: List<CommandHit>, vm: CommandViewModel) {
    itemsIndexed(list, key = { _, h -> "$prefix-${h.key}" }) { i, h ->
        Column(Modifier.groupItem(i, list.size)) {
            HitRow(h, onClick = { vm.choose(h) })
            if (i < list.size - 1) RowDivider(inset = 64.dp)
        }
    }
}

@Composable
private fun HitRow(h: CommandHit, onClick: () -> Unit) {
    val restricted = (h as? CommandHit.Run)?.restricted == true || (h as? CommandHit.Go)?.restricted == true
    ListRow(
        title = h.title,
        subtitle = h.subtitle,
        leading = {
            when (h) {
                is CommandHit.Run -> if (h.recipe != null) IconTile(Icons.Filled.Bolt, tint = CfTheme.colors.accent) else IconTile(Icons.Filled.Api)
                is CommandHit.Go -> IconTile(h.place?.capabilityId?.let { iconFor(it) } ?: Icons.Filled.AccountTree)
                is CommandHit.Resource -> IconTile(if (h.ref.kind == "zone") Icons.Filled.Language else Icons.Filled.Storage)
            }
        },
        trailing = {
            when {
                restricted -> Tag("Not allowed", color = StatusColors.warning)
                h is CommandHit.Run && h.recipe == null -> MethodLabel(h.draft.method)
                h is CommandHit.Resource -> Tag(h.ref.kind)
                else -> Unit
            }
        },
        onClick = onClick
    )
}

@Composable
private fun BestMatch(h: CommandHit, onClick: () -> Unit) {
    Column(
        Modifier.padding(horizontal = Space.gutter).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CfTheme.colors.card)
            .border(1.dp, CfTheme.colors.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(Space.lg)
            .testTag("command-best"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(
                when (h) { is CommandHit.Run -> if (h.draft.isRead) "Show" else "Review and run"; is CommandHit.Go -> "Open"; is CommandHit.Resource -> "Open" },
                style = MaterialTheme.typography.labelMedium, color = CfTheme.colors.accent, modifier = Modifier.weight(1f)
            )
            if (h.score >= CommandEngine.CONFIDENT) Text("Enter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(h.title, style = MaterialTheme.typography.titleMedium)
        h.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun PlannerRow(state: PlannerState, onPlan: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.padding(top = Space.md)) {
        Column(Modifier.groupItem(0, 1)) {
            ListRow(
                title = when (state) { PlannerState.Planning -> "Planning with Workers AI"; else -> "Plan with Workers AI" },
                subtitle = when (state) {
                    is PlannerState.Failed -> state.message
                    PlannerState.Planning -> "Choosing the operation and filling it in"
                    PlannerState.Idle -> "Turns your sentence into a request you review. Runs in your Cloudflare account."
                },
                leading = { IconTile(Icons.Filled.AutoAwesome, tint = MaterialTheme.colorScheme.tertiary) },
                trailing = {
                    if (state is PlannerState.Planning) {
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                },
                onClick = if (state is PlannerState.Planning) null else onPlan
            )
        }
    }
}

@Composable
private fun InputBar(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit, planning: Boolean, focus: FocusRequester) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.gutter, vertical = Space.md)
                .clip(RoundedCornerShape(26.dp))
                .background(CfTheme.colors.card)
                .border(1.dp, CfTheme.colors.accent.copy(alpha = if (value.isEmpty()) 0.35f else 0.8f), RoundedCornerShape(26.dp))
                .heightIn(min = 52.dp)
                .padding(start = Space.lg, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                if (value.isEmpty()) {
                    Text("What do you want to do?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(CfTheme.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("command-input").semantics { contentDescription = "Command" }
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (value.isBlank()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary)
                    .clickable(enabled = value.isNotBlank() && !planning, onClick = onSubmit),
                contentAlignment = Alignment.Center
            ) {
                if (planning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.AutoMirrored.Filled.ArrowForward, "Go", tint = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(title: String, options: List<NamedRef>, selectedId: String?, allowNone: Boolean, onPick: (NamedRef?) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Space.gutter)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (options.size > 8) {
                Spacer(Modifier.size(Space.sm))
                dev.cfmobile.app.ui.common.ListSearchField(filter, { filter = it }, "Search", Modifier.padding(horizontal = 0.dp))
            }
        }
        val shown = options.filter { filter.isBlank() || it.name.contains(filter, true) }
        LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(top = Space.sm, bottom = Space.xl)) {
            if (allowNone) item { ListRow(title = "None", subtitle = "Work at account level", onClick = { onPick(null) }, showChevron = false) }
            if (options.isEmpty()) item { Text("Loading", modifier = Modifier.padding(Space.gutter), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(shown, key = { it.id }) { o ->
                ListRow(
                    title = o.name, meta = o.id,
                    trailing = { if (o.id == selectedId) Tag("Current") },
                    onClick = { onPick(o) }
                )
            }
        }
    }
}
