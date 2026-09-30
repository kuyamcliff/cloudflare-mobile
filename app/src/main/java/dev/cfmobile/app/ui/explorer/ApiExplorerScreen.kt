package dev.cfmobile.app.ui.explorer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.capabilities.DestructiveRisk
import dev.cfmobile.app.core.net.ConnectionState
import dev.cfmobile.app.data.local.db.SavedRequestEntity
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.DestructiveConfirmDialog
import dev.cfmobile.app.ui.components.MethodBadge
import dev.cfmobile.app.ui.components.MutationContext
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.components.copyToClipboard
import dev.cfmobile.app.ui.components.jsonColors
import dev.cfmobile.app.ui.components.jsonLines
import dev.cfmobile.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiExplorerScreen(
    viewModel: ApiExplorerViewModel,
    profileLabel: String?,
    accountLabel: String?,
    connection: StateFlow<ConnectionState>,
    templates: Flow<List<SavedRequestEntity>>,
    onDeleteTemplate: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val online by connection.collectAsState()
    val savedTemplates by templates.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var saveOpen by remember { mutableStateOf(false) }
    var templatesOpen by remember { mutableStateOf(false) }
    var showHeaders by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val colors = jsonColors()

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = ui.result ?: return@launch
            val error = if (result.response.truncated) {
                context.contentResolver.openOutputStream(uri)?.use { viewModel.downloadFullResponse(it) } ?: "Could not open the file"
            } else {
                context.contentResolver.openOutputStream(uri)?.use { it.write(result.response.body.toByteArray()) }
                null
            }
            snackbar.showSnackbar(error ?: "Saved")
        }
    }

    LaunchedEffect(ui.notice) {
        ui.notice?.let { snackbar.showSnackbar(it); viewModel.dismissNotice() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.endpoint?.summary ?: "API Explorer", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Cloudflare API only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Browse all APIs") }, onClick = { menu = false; onOpenCatalog() })
                        DropdownMenuItem(text = { Text("Templates") }, onClick = { menu = false; templatesOpen = true })
                        DropdownMenuItem(text = { Text("Save as template") }, onClick = { menu = false; saveOpen = true })
                        DropdownMenuItem(text = { Text("Copy as cURL") }, onClick = {
                            menu = false
                            copyToClipboard(context, "cURL", viewModel.curl())
                        })
                        DropdownMenuItem(text = { Text("Import cURL") }, onClick = { menu = false; importOpen = true })
                        DropdownMenuItem(text = { Text("Request history") }, onClick = { menu = false; onOpenHistory() })
                        DropdownMenuItem(text = { Text("Cloudflare API reference") }, onClick = {
                            menu = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.cloudflare.com/api/")))
                        })
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding()) {
            ui.endpoint?.let { e ->
                item("doc") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MethodBadge(e.method)
                            Text(e.path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (e.native) Badge("Native screen exists", MaterialTheme.colorScheme.primary) else Badge("Generic API", MaterialTheme.colorScheme.onSurfaceVariant)
                            CapabilityBadge(ui.tokenState)
                            if (e.deprecated) Badge("Deprecated by Cloudflare", StatusColors.warning)
                        }
                        if (e.permissions.isNotEmpty()) {
                            Text(
                                "Accepted permissions: " + e.permissions.distinct().joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (e.plans.isNotEmpty()) {
                            Text("Plans: " + e.plans.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item("request") {
                SectionHeader("Request")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ui.endpoint == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            METHODS.forEach { m -> FilterChip(selected = ui.method == m, onClick = { viewModel.setMethod(m) }, label = { Text(m) }) }
                        }
                        OutlinedTextField(
                            value = ui.pathTemplate,
                            onValueChange = viewModel::setPathTemplate,
                            label = { Text("Path") },
                            placeholder = { Text("accounts/{account_id}/r2/buckets") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Relative to api.cloudflare.com/client/v4/") }
                        )
                    }
                    val placeholders = ui.pathTemplate.split('/').filter { it.startsWith("{") && it.endsWith("}") }.map { it.trim('{', '}') }
                    placeholders.forEach { name ->
                        OutlinedTextField(
                            value = ui.pathValues[name].orEmpty(),
                            onValueChange = { viewModel.setPathValue(name, it) },
                            label = { Text(name) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = ui.endpoint?.pathParams?.firstOrNull { it.name == name }?.description?.takeIf { it.isNotBlank() }?.let { d -> { Text(d, maxLines = 2) } }
                        )
                    }
                }
            }

            item("query") {
                SectionHeader("Query parameters") {
                    QueryParamAdder(ui, onAdd = viewModel::addQuery)
                }
            }
            itemsIndexed(ui.query, key = { i, _ -> "q$i" }) { i, kv ->
                KeyValueEditor(
                    kv = kv,
                    hint = ui.endpoint?.queryParams?.firstOrNull { it.name == kv.key }?.let { p ->
                        listOfNotNull(p.type, p.enumValues.takeIf { it.isNotEmpty() }?.joinToString("|"), p.description.takeIf { it.isNotBlank() }).joinToString(" · ")
                    },
                    onChange = { viewModel.setQuery(i, it) },
                    onRemove = { viewModel.removeQuery(i) }
                )
            }

            item("headers") {
                SectionHeader("Headers") {
                    IconButton(onClick = viewModel::addHeader) { Icon(Icons.Filled.Add, "Add header") }
                }
                Text(
                    "Authorization is added by the app from the active profile and cannot be edited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            itemsIndexed(ui.headers, key = { i, _ -> "h$i" }) { i, kv ->
                KeyValueEditor(kv = kv, hint = null, onChange = { viewModel.setHeader(i, it) }, onRemove = { viewModel.removeHeader(i) })
            }

            if (ui.method != "GET") {
                item("body") {
                    SectionHeader("Body (JSON)") {
                        TextButton(onClick = viewModel::formatBody) { Text("Format") }
                    }
                    OutlinedTextField(
                        value = ui.body,
                        onValueChange = viewModel::setBody,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        isError = ui.bodyError != null,
                        supportingText = ui.bodyError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(horizontal = 16.dp)
                    )
                }
            }

            item("send") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!online.online) {
                        Text("No connection. You can keep editing; nothing is sent until you are online.", style = MaterialTheme.typography.bodySmall, color = StatusColors.warning)
                    }
                    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (ui.isSending) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.padding(4.dp))
                            OutlinedButton(onClick = viewModel::cancel) { Text("Cancel request") }
                        }
                    } else {
                        Button(
                            onClick = { if (ui.isMutation) confirm = true else viewModel.send() },
                            enabled = online.online,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Send ${ui.method}") }
                    }
                }
            }

            ui.result?.let { result ->
                val r = result.response
                item("resp-head") {
                    SectionHeader("Response")
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Badge("HTTP ${r.statusCode}", if (r.isSuccess) StatusColors.success else MaterialTheme.colorScheme.error)
                        Text("${r.durationMillis} ms · ${formatBytes(r.totalBytes)}${if (r.truncated) " · showing first ${formatBytes(RawApiClient.MAX_INLINE_BYTES)}" else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        TextButton(onClick = { showHeaders = !showHeaders }) { Text(if (showHeaders) "Hide headers" else "Headers") }
                        TextButton(onClick = { copyToClipboard(context, "response", r.body) }) { Text("Copy") }
                        TextButton(onClick = { saveLauncher.launch("cloudflare-response.json") }) { Text(if (r.truncated) "Save full" else "Save") }
                    }
                    if (showHeaders) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            r.headers.forEach { (k, v) -> Text("$k: $v", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search response") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    if (search.isNotBlank()) {
                        val count = result.lines.count { it.contains(search, ignoreCase = true) }
                        Text("$count matching lines", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                jsonLines(result.lines, colors, search)
            }
        }
    }

    if (confirm) {
        DestructiveConfirmDialog(
            title = "${ui.method} ${ui.endpoint?.summary ?: ui.resolvedPath}?",
            consequence = if (ui.method == "DELETE") "This deletes the resource in Cloudflare. It may not be recoverable."
            else "This changes your Cloudflare configuration.",
            confirmLabel = "Send ${ui.method}",
            risk = if (ui.method == "DELETE") DestructiveRisk.HIGH else DestructiveRisk.MEDIUM,
            context = MutationContext(profile = profileLabel, account = accountLabel, target = ui.resolvedPath),
            onConfirm = { confirm = false; viewModel.send() },
            onDismiss = { confirm = false }
        )
    }
    if (importOpen) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { Text("Import cURL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Only api.cloudflare.com commands are accepted. Any Authorization header is removed.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = text, onValueChange = { text = it }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.heightIn(min = 120.dp))
                }
            },
            confirmButton = { TextButton(onClick = { importOpen = false; viewModel.importCurl(text) }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { importOpen = false }) { Text("Cancel") } }
        )
    }
    if (saveOpen) {
        var name by remember { mutableStateOf(ui.endpoint?.summary.orEmpty()) }
        AlertDialog(
            onDismissRequest = { saveOpen = false },
            title = { Text("Save template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saves the method, path, query and body. Credentials are never saved.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { saveOpen = false; viewModel.saveTemplate(name) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { saveOpen = false }) { Text("Cancel") } }
        )
    }
    if (templatesOpen) {
        AlertDialog(
            onDismissRequest = { templatesOpen = false },
            title = { Text("Templates") },
            text = {
                if (savedTemplates.isEmpty()) {
                    Text("No saved templates yet.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(savedTemplates, key = { it.id }) { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { templatesOpen = false; viewModel.loadTemplate(t) }, modifier = Modifier.weight(1f)) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(t.name, style = MaterialTheme.typography.bodyMedium)
                                        Text("${t.method} ${t.path}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { onDeleteTemplate(t.id) }) { Icon(Icons.Filled.Close, "Delete template") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { templatesOpen = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun QueryParamAdder(ui: ApiExplorerUiState, onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val known = ui.endpoint?.queryParams.orEmpty().filter { p -> ui.query.none { it.key == p.name } }
    IconButton(onClick = { if (known.isEmpty()) onAdd("") else open = true }) { Icon(Icons.Filled.Add, "Add query parameter") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        known.take(40).forEach { p ->
            DropdownMenuItem(
                text = { Text(p.name + if (p.required) " (required)" else "", fontFamily = FontFamily.Monospace) },
                onClick = { open = false; onAdd(p.name) }
            )
        }
        DropdownMenuItem(text = { Text("Custom parameter") }, onClick = { open = false; onAdd("") })
    }
}

@Composable
private fun KeyValueEditor(kv: KeyValue, hint: String?, onChange: (KeyValue) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = kv.key, onValueChange = { onChange(kv.copy(key = it)) }, placeholder = { Text("name") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = kv.value, onValueChange = { onChange(kv.copy(value = it)) }, placeholder = { Text("value") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove") }
        }
        hint?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
