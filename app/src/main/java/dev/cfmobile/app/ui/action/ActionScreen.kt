package dev.cfmobile.app.ui.action

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.api.BodyField
import dev.cfmobile.app.core.api.EndpointParam
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.core.command.ActionDraft
import dev.cfmobile.app.core.command.Json
import dev.cfmobile.app.core.command.ResultModel
import dev.cfmobile.app.ui.components.copyToClipboard
import dev.cfmobile.app.ui.components.highlightJsonLine
import dev.cfmobile.app.ui.components.jsonColors
import dev.cfmobile.app.ui.design.Banner
import dev.cfmobile.app.ui.design.GroupTitle
import dev.cfmobile.app.ui.design.ListRow
import dev.cfmobile.app.ui.design.MethodLabel
import dev.cfmobile.app.ui.design.PrimaryButton
import dev.cfmobile.app.ui.design.RowDivider
import dev.cfmobile.app.ui.design.Space
import dev.cfmobile.app.ui.design.StatusDot
import dev.cfmobile.app.ui.design.Tag
import dev.cfmobile.app.ui.design.groupItem
import dev.cfmobile.app.ui.theme.CfTheme
import dev.cfmobile.app.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionScreen(
    viewModel: ActionViewModel,
    contextLabel: String?,
    onBack: () -> Unit,
    onOpenDraft: (ActionDraft) -> Unit,
    onOpenExplorer: (method: String, path: String, query: String) -> Unit
) {
    val s by viewModel.ui.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<String?>(null) }
    var openItem by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = viewModel::togglePin) {
                        Icon(if (s.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (s.pinned) "Unpin from Home" else "Pin to Home")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Copy path") }, onClick = { menu = false; copyToClipboard(context, "path", s.resolvedPath) })
                            DropdownMenuItem(text = { Text("Open in API Explorer") }, onClick = {
                                menu = false
                                onOpenExplorer(s.method, s.resolvedPath, s.query.filterValues { it.isNotBlank() }.entries.joinToString("&") { "${it.key}=${it.value}" })
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (!s.loading) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.imePadding()) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = Space.gutter, vertical = Space.md)) {
                        s.failure?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = Space.sm))
                        }
                        PrimaryButton(
                            label = if (s.running) "Running" else s.verb,
                            loading = s.running,
                            destructive = s.method == "DELETE",
                            modifier = Modifier.testTag("action-run"),
                            onClick = { if (s.isRead) viewModel.run() else if (viewModel.validate() == null) confirm = true else viewModel.run() }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Space.xxl)) {
            item("header") { Header(s, contextLabel) }

            if (s.pathParams.isNotEmpty()) {
                item("path-h") { GroupTitle("Target") }
                itemsIndexed(s.pathParams, key = { _, n -> "p-$n" }) { i, name ->
                    Column(Modifier.groupItem(i, s.pathParams.size)) {
                        PathRow(name, s, onClick = { picker = name })
                        if (i < s.pathParams.size - 1) RowDivider()
                    }
                }
            }

            val queryParams = ActionViewModel.queryParamsFor(s.endpoint)
            if (queryParams.isNotEmpty()) {
                val required = queryParams.filter { it.required || s.query.containsKey(it.name) }
                val optional = queryParams - required.toSet()
                item("q-h") { GroupTitle("Options") }
                item("q") {
                    var open by rememberSaveable { mutableStateOf(false) }
                    Column(Modifier.groupItem(0, 1).padding(vertical = Space.xs)) {
                        required.forEach { p -> QueryField(p, s.query[p.name].orEmpty(), s.errors["q:${p.name}"]) { viewModel.setQuery(p.name, it) } }
                        if (optional.isNotEmpty()) {
                            ToggleRow(if (open) "Fewer options" else "More options (${optional.size})", open) { open = !open }
                            AnimatedVisibility(open) {
                                Column { optional.forEach { p -> QueryField(p, s.query[p.name].orEmpty(), null) { viewModel.setQuery(p.name, it) } } }
                            }
                        }
                    }
                }
            }

            if (s.hasBody) {
                item("b-h") {
                    GroupTitle("Details", action = {
                        if (s.endpoint?.bodyFields?.isNotEmpty() == true && s.isJsonBody) {
                            SingleChoiceSegmentedButtonRow(Modifier.heightIn(max = 34.dp)) {
                                SegmentedButton(selected = s.bodyMode == BodyMode.FORM, onClick = { viewModel.setBodyMode(BodyMode.FORM) }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) {
                                    Text("Form", style = MaterialTheme.typography.labelMedium)
                                }
                                SegmentedButton(selected = s.bodyMode == BodyMode.JSON, onClick = { viewModel.setBodyMode(BodyMode.JSON) }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) {
                                    Text("JSON", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    })
                }
                item("body") {
                    Column(Modifier.groupItem(0, 1).padding(vertical = Space.xs)) {
                        if (s.bodyMode == BodyMode.JSON || s.endpoint?.bodyFields.isNullOrEmpty()) {
                            JsonEditor(s.bodyText, s.errors["body"], onChange = viewModel::setBodyText)
                        } else {
                            BodyForm(s, viewModel)
                        }
                    }
                }
            }

            s.result?.let { result ->
                item("r-h") { ResultHeader(result) }
                val items = result.items
                if (items != null) {
                    if (items.isEmpty()) {
                        item("r-empty") {
                            Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.sm))
                        }
                    }
                    itemsIndexed(items, key = { i, _ -> "it-$i" }) { i, item ->
                        Column(Modifier.groupItem(i, items.size)) {
                            ListRow(title = ResultModel.title(item), subtitle = ResultModel.subtitle(item), onClick = { openItem = item })
                            if (i < items.size - 1) RowDivider()
                        }
                    }
                    if (result.parsed.hasMore) {
                        item("more") {
                            TextButton(onClick = viewModel::loadMore, enabled = !s.loadingMore, modifier = Modifier.fillMaxWidth().padding(Space.sm)) {
                                Text(if (s.loadingMore) "Loading" else "Load more")
                            }
                        }
                    }
                } else if (result.parsed.result is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val obj = result.parsed.result as Map<String, Any?>
                    item("obj") { ObjectCard(obj) }
                    if (result.response.isSuccess && s.isRead) {
                        val follow = viewModel.followUps(isList = false)
                        if (follow.isNotEmpty()) {
                            item("obj-f") { FollowUpRow(follow) { f -> onOpenDraft(viewModel.followUpDraft(f, obj)) } }
                        }
                    }
                } else if (result.parsed.result != null && result.parsed.result !is String) {
                    item("scalar") {
                        Text(Json.stringify(result.parsed.result), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = Space.gutter + 4.dp))
                    }
                }
                item("raw") { RawResponse(result) }
            }
        }
    }

    picker?.let { name ->
        PathPickerSheet(name, s, onPick = { viewModel.setPathValue(name, it); picker = null }, onDismiss = { picker = null })
    }
    openItem?.let { item ->
        ItemSheet(
            item = item,
            followUps = viewModel.followUps(isList = true),
            onFollow = { f -> openItem = null; onOpenDraft(viewModel.followUpDraft(f, item)) },
            onDismiss = { openItem = null }
        )
    }
    if (confirm) {
        ConfirmSheet(s, contextLabel, onConfirm = { confirm = false; viewModel.run() }, onDismiss = { confirm = false })
    }
}

@Composable
private fun Header(s: ActionUiState, contextLabel: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter + 4.dp).padding(bottom = Space.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            MethodLabel(s.method)
            s.endpoint?.group?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text(s.title, style = MaterialTheme.typography.headlineSmall)
        Text(s.path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            when (s.tokenState) {
                CapabilityState.TOKEN_RESTRICTED -> Tag("Token may not allow this", color = StatusColors.warning)
                CapabilityState.AVAILABLE_WRITE, CapabilityState.AVAILABLE_READ -> Tag("Allowed by token", color = StatusColors.success)
                else -> Unit
            }
            if (s.endpoint?.deprecated == true) Tag("Deprecated", color = StatusColors.warning)
            if (s.endpoint?.plans?.isNotEmpty() == true) Tag(s.endpoint.plans.joinToString("/") { it.removePrefix("CF_").lowercase() })
            contextLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        s.endpoint?.description?.takeIf { it.isNotBlank() && it != s.title }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    if (s.source == ActionDraft.Source.AI) {
        Banner(
            (s.note?.let { "$it " } ?: "") + "Planned by Workers AI. Check every field before running.",
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun PathRow(name: String, s: ActionUiState, onClick: () -> Unit) {
    val value = s.pathValues[name].orEmpty()
    val options = (s.pathOptions[name] as? OptionsState.Loaded)?.options
    val label = options?.firstOrNull { it.value == value }?.label
    val error = s.errors[name]
    ListRow(
        title = Humanize.param(name),
        subtitle = when {
            value.isBlank() -> error?.let { "Required" } ?: "Choose"
            label != null && label != value -> label
            else -> null
        },
        meta = value.ifBlank { null },
        trailing = {
            when (s.pathOptions[name]) {
                OptionsState.Loading -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else -> Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        },
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PathPickerSheet(name: String, s: ActionUiState, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(s.pathValues[name].orEmpty()) }
    var filter by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Space.gutter)) {
            Text(Humanize.param(name), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(Space.md))
            OutlinedTextField(
                value = text, onValueChange = { text = it; filter = it }, singleLine = true,
                label = { Text("Value") }, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                trailingIcon = { TextButton(onClick = { onPick(text) }, enabled = text.isNotBlank()) { Text("Use") } }
            )
        }
        when (val o = s.pathOptions[name]) {
            OptionsState.Loading -> Box(Modifier.fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is OptionsState.Unavailable -> Text(o.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.gutter))
            is OptionsState.Loaded -> {
                val shown = o.options.filter { filter.isBlank() || it.label.contains(filter, true) || it.value.contains(filter, true) || it.detail?.contains(filter, true) == true }
                LazyColumn(Modifier.heightIn(max = 460.dp).padding(top = Space.sm), contentPadding = PaddingValues(bottom = Space.xl)) {
                    items(shown, key = { it.value }) { opt ->
                        ListRow(
                            title = opt.label, subtitle = opt.detail, meta = opt.value.takeIf { it != opt.label },
                            trailing = { if (opt.value == s.pathValues[name]) Tag("Selected") },
                            onClick = { onPick(opt.value) }
                        )
                    }
                }
            }
            else -> Text("Fill in the values above it first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.gutter))
        }
        Spacer(Modifier.size(Space.lg))
    }
}

@Composable
private fun ToggleRow(label: String, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.gutter, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QueryField(p: EndpointParam, value: String, error: String?, onChange: (String) -> Unit) {
    FieldShell(Humanize.field(p.name), p.description, p.required, error) {
        when {
            p.enumValues.isNotEmpty() -> ChoiceChips(p.enumValues, value.ifBlank { null }, allowClear = !p.required) { onChange(it ?: "") }
            p.type == "boolean" -> BoolSwitch(value.toBooleanStrictOrNull()) { onChange(it?.toString() ?: "") }
            else -> TextInput(value, numeric = p.type == "integer" || p.type == "number", onChange = onChange)
        }
    }
}

@Composable
private fun BodyForm(s: ActionUiState, viewModel: ActionViewModel) {
    val fields = s.endpoint?.bodyFields.orEmpty().sortedBy { f ->
        val i = FIELD_PRIORITY.indexOf(f.name)
        when {
            f.required -> -100 + (if (i >= 0) i else 50)
            i >= 0 -> i
            else -> 100
        }
    }
    val primary = fields.filter { it.required || s.body.containsKey(it.name) }
    val rest = fields - primary.toSet()
    val shown = if (s.showAllFields || primary.isEmpty() && rest.size <= 4) fields else primary
    shown.forEach { f -> BodyFieldInput(f, s, viewModel) }
    val extra = s.body.keys - fields.map { it.name }.toSet()
    if (extra.isNotEmpty()) {
        Text(
            "Also sent: " + extra.joinToString(", "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm)
        )
    }
    if (rest.isNotEmpty() && !(primary.isEmpty() && rest.size <= 4)) {
        ToggleRow(if (s.showAllFields) "Fewer fields" else "More fields (${rest.size})", s.showAllFields, viewModel::toggleAllFields)
    }
}

@Composable
private fun BodyFieldInput(f: BodyField, s: ActionUiState, viewModel: ActionViewModel) {
    val value = s.body[f.name]
    val error = s.errors["b:${f.name}"]
    FieldShell(Humanize.field(f.name), f.description, f.required, error) {
        when {
            f.enumValues.isNotEmpty() && f.type != "array" ->
                ChoiceChips(f.enumValues, value?.toString(), allowClear = !f.required) { v ->
                    viewModel.setField(f, v?.let { coerceEnum(it, f) })
                }
            f.type == "boolean" -> BoolSwitch(value as? Boolean) { viewModel.setField(f, it) }
            f.type == "integer" || f.type == "number" -> TextInput(
                value = when (value) { is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString(); null -> ""; else -> value.toString() },
                numeric = true,
                placeholder = f.example?.toString()
            ) { t -> viewModel.setField(f, if (t.isBlank()) null else t.toLongOrNull() ?: t.toDoubleOrNull() ?: t) }
            f.type == "array" && f.itemType in setOf("string", "integer", "number") -> TextInput(
                value = (value as? List<*>)?.joinToString(", ").orEmpty(),
                placeholder = "Comma separated"
            ) { t ->
                val items = t.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                viewModel.setField(f, if (items.isEmpty()) null else if (f.itemType == "string") items else items.map { it.toLongOrNull() ?: it.toDoubleOrNull() ?: it })
            }
            ActionViewModel.isJsonField(f) -> JsonEditor(s.jsonFieldText[f.name].orEmpty(), null, compact = true) { viewModel.setJsonField(f, it) }
            else -> TextInput(
                value = value?.toString().orEmpty(),
                placeholder = f.example?.toString(),
                multiline = f.name in setOf("content", "script", "description", "expression", "query", "sql", "prompt", "notes", "comment")
            ) { viewModel.setField(f, it.ifEmpty { null }) }
        }
    }
}

/** Identity fields first, so a form reads "type, name, content" rather than schema order. */
private val FIELD_PRIORITY = listOf("type", "mode", "name", "title", "hostname", "email", "content", "value", "url", "target", "action", "expression", "description", "enabled", "proxied", "priority", "ttl", "comment")

private fun coerceEnum(value: String, f: BodyField): Any = when {
    f.type == "integer" || f.type == "number" -> value.toLongOrNull() ?: value.toDoubleOrNull() ?: value
    f.type == "boolean" -> value.toBooleanStrictOrNull() ?: value
    f.type == "any" && value.toLongOrNull() != null -> value.toLong()
    else -> value
}

@Composable
private fun FieldShell(label: String, description: String, required: Boolean, error: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (required) Text("Required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
        when {
            error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            description.isNotBlank() -> Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TextInput(value: String, numeric: Boolean = false, placeholder: String? = null, multiline: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        maxLines = if (multiline) 8 else 1,
        placeholder = placeholder?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(values: List<String>, selected: String?, allowClear: Boolean, onSelect: (String?) -> Unit) {
    if (values.size > 12) {
        var open by remember { mutableStateOf(false) }
        Box {
            OutlinedTextField(
                value = selected.orEmpty(), onValueChange = {}, readOnly = true, singleLine = true,
                placeholder = { Text("Choose") }, shape = RoundedCornerShape(12.dp),
                trailingIcon = { Icon(Icons.Filled.ExpandMore, null) },
                modifier = Modifier.fillMaxWidth()
            )
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).clickable { open = true })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                if (allowClear) DropdownMenuItem(text = { Text("Not set") }, onClick = { open = false; onSelect(null) })
                values.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { open = false; onSelect(v) }) }
            }
        }
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            FilterChip(
                selected = v == selected,
                onClick = { onSelect(if (v == selected && allowClear) null else v) },
                label = { Text(v) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun BoolSwitch(value: Boolean?, onChange: (Boolean?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Switch(checked = value == true, onCheckedChange = { onChange(it) })
        Text(
            when (value) { null -> "Not sent"; true -> "On"; false -> "Off" },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (value != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
    }
}

@Composable
private fun JsonEditor(text: String, error: String?, compact: Boolean = false, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = if (compact) 0.dp else Space.gutter, vertical = if (compact) 0.dp else Space.sm)) {
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            minLines = if (compact) 2 else 6,
            maxLines = if (compact) 10 else 24,
            isError = error != null,
            placeholder = { Text("{ }", fontFamily = FontFamily.Monospace) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun ResultHeader(result: RunResult) {
    val ok = result.response.isSuccess && result.parsed.success
    val color = if (ok) StatusColors.success else MaterialTheme.colorScheme.error
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter + 4.dp).padding(top = Space.xl, bottom = Space.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatusDot(color)
            Text(
                when {
                    ok && result.items != null -> "${result.parsed.totalCount ?: result.items.size} ${if ((result.parsed.totalCount ?: result.items.size) == 1) "item" else "items"}"
                    ok -> "Done"
                    else -> "Cloudflare refused this"
                },
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)
            )
            Text("${result.response.statusCode} · ${result.response.durationMillis} ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        result.parsed.errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        result.parsed.messages.take(3).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (result.response.truncated) Text("Large response: showing the first 2 MB.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ObjectCard(obj: Map<String, Any?>) {
    Column(Modifier.groupItem(0, 1).padding(vertical = Space.xs)) {
        KeyValues(obj)
    }
}

@Composable
private fun KeyValues(obj: Map<String, Any?>) {
    val context = LocalContext.current
    obj.entries.take(60).forEachIndexed { i, (k, v) ->
        val text = when (v) {
            null -> "null"
            is Map<*, *>, is List<*> -> Json.stringify(v).let { if (it.length > 160) it.take(157) + "…" else it }
            is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
            else -> v.toString()
        }
        Row(
            Modifier.fillMaxWidth().clickable { copyToClipboard(context, k, if (v is Map<*, *> || v is List<*>) Json.stringify(v, pretty = true) else text) }
                .padding(horizontal = Space.gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = if (v is String && v.length > 20 || v is Map<*, *> || v is List<*>) FontFamily.Monospace else null, modifier = Modifier.weight(1f), maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (i < obj.size - 1 && i < 59) RowDivider()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowUpRow(followUps: List<ResultModel.FollowUp>, onClick: (ResultModel.FollowUp) -> Unit) {
    Column(Modifier.padding(top = Space.md)) {
        GroupTitle("Next")
        FlowRow(Modifier.padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            followUps.forEach { f -> FollowChip(f, onClick) }
        }
    }
}

@Composable
private fun FollowChip(f: ResultModel.FollowUp, onClick: (ResultModel.FollowUp) -> Unit) {
    val destructive = f.endpoint.method == "DELETE"
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(CfTheme.colors.card).clickable { onClick(f) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(f.label, style = MaterialTheme.typography.labelLarge, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ItemSheet(item: Map<String, Any?>, followUps: List<ResultModel.FollowUp>, onFollow: (ResultModel.FollowUp) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(bottom = Space.xxl)) {
            item {
                Column(Modifier.padding(horizontal = Space.gutter + 4.dp)) {
                    Text(ResultModel.title(item), style = MaterialTheme.typography.titleLarge)
                    ResultModel.subtitle(item)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (followUps.isNotEmpty()) {
                item {
                    FlowRow(Modifier.padding(horizontal = Space.gutter, vertical = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        followUps.forEach { f -> FollowChip(f, onFollow) }
                    }
                }
            }
            item { Column(Modifier.groupItem(0, 1)) { KeyValues(item) } }
        }
    }
}

@Composable
private fun RawResponse(result: RunResult) {
    var open by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = jsonColors()
    Column(Modifier.padding(top = Space.lg)) {
        GroupTitle("Response", action = {
            Row {
                TextButton(onClick = { copyToClipboard(context, "response", result.response.body) }) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copy")
                }
                TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Show") }
            }
        })
        if (open) {
            Column(Modifier.groupItem(0, 1).padding(vertical = Space.sm).horizontalScroll(rememberScrollState())) {
                result.prettyLines.take(MAX_LINES).forEach { line ->
                    Text(highlightJsonLine(line, colors), fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = false, modifier = Modifier.padding(horizontal = Space.md))
                }
                if (result.prettyLines.size > MAX_LINES) {
                    Text("${result.prettyLines.size - MAX_LINES} more lines. Copy to see everything.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.md))
                }
            }
        }
    }
}

private const val MAX_LINES = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(s: ActionUiState, contextLabel: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val critical = s.method == "DELETE" && Humanize.isCritical(s.path)
    val target = s.pathValues[s.pathParams.lastOrNull().orEmpty()].orEmpty()
    var typed by remember { mutableStateOf("") }
    val body = if (s.hasBody) {
        if (s.bodyMode == BodyMode.JSON) s.bodyText else Json.stringify(s.body, pretty = true)
    } else null
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Space.gutter + 4.dp).padding(bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(if (s.method == "DELETE") "${s.title}?" else "${s.verb}: ${s.title}", style = MaterialTheme.typography.titleLarge)
            Text(
                when (s.method) {
                    "DELETE" -> "This deletes it from Cloudflare. It can't be undone from this app."
                    else -> "This changes your Cloudflare configuration as soon as you confirm."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CfTheme.colors.card).padding(Space.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                contextLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                Text("${s.method} ${s.resolvedPath}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                body?.takeIf { it.isNotBlank() && it != "{}" }?.let {
                    Text(it.lines().take(14).joinToString("\n") + if (it.lines().size > 14) "\n…" else "", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            if (critical && target.isNotBlank()) {
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, label = { Text("Type $target to confirm") }, modifier = Modifier.fillMaxWidth())
            }
            PrimaryButton(
                label = s.verb, destructive = s.method == "DELETE", enabled = !critical || target.isBlank() || typed.trim() == target,
                modifier = Modifier.testTag("action-confirm"), onClick = onConfirm
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

/** Plain-language labels for schema names. */
object Humanize {
    private val ACRONYMS = setOf("dns", "ip", "ips", "url", "urls", "id", "ssl", "tls", "api", "r2", "kv", "d1", "waf", "ttl", "cidr", "asn", "http", "https", "ddos", "sni", "mtls", "ca", "csr", "cname", "jwt", "sso", "saml", "oidc", "scim", "gre", "ipsec", "bgp", "warp", "ai", "sql", "uuid", "pdf", "ns")

    fun param(name: String): String {
        val n = name.lowercase()
        val stem = n.removeSuffix("_identifier").removeSuffix("_id").removeSuffix("_uuid").removeSuffix("_tag").let { if (it == "identifier") "ID" else it }
        return words(stem.ifBlank { n })
    }

    fun field(name: String): String = words(name)

    private fun words(raw: String): String = raw.split('_', '-', '.').filter { it.isNotBlank() }.mapIndexed { i, w ->
        when {
            w.lowercase() in ACRONYMS -> w.uppercase()
            i == 0 -> w.replaceFirstChar { it.uppercase() }
            else -> w
        }
    }.joinToString(" ")

    private val CRITICAL = listOf(
        Regex("^zones/\\{[^}]+}$"),
        Regex("^accounts/\\{[^}]+}/r2/buckets/\\{[^}]+}$"),
        Regex("^accounts/\\{[^}]+}/d1/database/\\{[^}]+}$"),
        Regex("^accounts/\\{[^}]+}/storage/kv/namespaces/\\{[^}]+}$"),
        Regex("^accounts/\\{[^}]+}/workers/scripts/\\{[^}]+}$"),
        Regex("^accounts/\\{[^}]+}/pages/projects/\\{[^}]+}$"),
        Regex("^accounts/\\{[^}]+}/cfd_tunnel/\\{[^}]+}$"),
        Regex("^accounts/\\{[^}]+}/members/\\{[^}]+}$"),
        Regex("^(user|accounts/\\{[^}]+})/tokens/\\{[^}]+}$")
    )

    fun isCritical(path: String) = CRITICAL.any { it.matches(path) }
}
