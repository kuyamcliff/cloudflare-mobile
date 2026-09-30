package dev.cfmobile.app.ui.r2

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.capabilities.DestructiveRisk
import dev.cfmobile.app.core.transfers.TransferRepository
import dev.cfmobile.app.data.remote.dto.R2Object
import dev.cfmobile.app.ui.common.FullScreenError
import dev.cfmobile.app.ui.components.DestructiveConfirmDialog
import dev.cfmobile.app.ui.components.KeyValueRow
import dev.cfmobile.app.ui.components.MutationContext
import dev.cfmobile.app.ui.components.SecureWindow
import dev.cfmobile.app.ui.components.ThinDivider
import dev.cfmobile.app.ui.components.copyToClipboard
import dev.cfmobile.app.ui.explorer.formatBytes
import dev.cfmobile.app.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun R2ObjectsScreen(
    viewModel: R2ObjectsViewModel,
    profileLabel: String?,
    accountLabel: String?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTransfers: () -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<R2Object?>(null) }
    var pendingDownload by remember { mutableStateOf<R2Object?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var credsOpen by remember { mutableStateOf(false) }

    // Asked for at the moment it matters: when a transfer is about to run in the background.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val repository = (context.applicationContext as dev.cfmobile.app.CfApplication).container.transferRepository
            viewModel.planUpload(uris.map { uri -> repository.describe(context.contentResolver, uri) })
        }
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val obj = pendingDownload
        pendingDownload = null
        if (uri != null && obj != null) { askForNotifications(); viewModel.download(obj, uri) }
    }

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }
    BackHandler(enabled = ui.prefix.isNotEmpty() || ui.selected.isNotEmpty()) {
        if (ui.selected.isNotEmpty()) viewModel.clearSelection() else viewModel.up()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (ui.selected.isEmpty()) viewModel.bucket else "${ui.selected.size} selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(accountLabel, "R2").joinToString(" / "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (ui.selected.isNotEmpty()) viewModel.clearSelection() else onBack() }) {
                        Icon(if (ui.selected.isNotEmpty()) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, if (ui.selected.isNotEmpty()) "Clear selection" else "Back")
                    }
                },
                actions = {
                    if (ui.selected.isNotEmpty()) {
                        IconButton(onClick = viewModel::selectAll) { Icon(Icons.Filled.SelectAll, "Select all loaded") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete selected") }
                    } else {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Transfers") }, onClick = { menu = false; onOpenTransfers() })
                            DropdownMenuItem(text = { Text("Bucket settings") }, onClick = { menu = false; onOpenSettings() })
                            DropdownMenuItem(text = { Text(if (ui.hasS3Credentials) "R2 S3 credentials (saved)" else "Add R2 S3 credentials") }, onClick = { menu = false; credsOpen = true })
                            DropdownMenuItem(text = { Text("Copy bucket name") }, onClick = { menu = false; copyToClipboard(context, "bucket", viewModel.bucket) })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (ui.selected.isEmpty()) {
                ExtendedFloatingActionButton(onClick = { picker.launch(arrayOf("*/*")) }, icon = { Icon(Icons.Filled.Upload, null) }, text = { Text("Upload") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ui.deleting) LinearProgressIndicator(Modifier.fillMaxWidth())
            Breadcrumbs(viewModel.bucket, ui.prefix, onGo = viewModel::goTo)
            var search by remember(ui.prefix) { mutableStateOf(ui.search) }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Prefix search in this folder") },
                singleLine = true,
                trailingIcon = { TextButton(onClick = { viewModel.setSearch(search) }) { Text("Search") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ObjectSort.entries.forEach { s -> FilterChip(selected = ui.sort == s, onClick = { viewModel.setSort(s) }, label = { Text(s.label) }) }
            }
            when {
                ui.loading -> Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                ui.error != null -> FullScreenError(ui.error!!, onRetry = { viewModel.load(true) })
                ui.folders.isEmpty() && ui.objects.isEmpty() -> Text(
                    if (ui.search.isNotEmpty()) "No objects start with \"${ui.search}\" here." else "This folder is empty.",
                    modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.folders, key = { "d-$it" }) { folder ->
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(onClick = { viewModel.openFolder(folder) }).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Text(folder.removePrefix(ui.prefix), style = MaterialTheme.typography.bodyLarge)
                        }
                        ThinDivider()
                    }
                    items(ui.sortedObjects, key = { "o-${it.key}" }) { obj ->
                        ObjectRow(
                            obj = obj,
                            name = obj.key.removePrefix(ui.prefix),
                            selected = obj.key in ui.selected,
                            selecting = ui.selected.isNotEmpty(),
                            onClick = { if (ui.selected.isNotEmpty()) viewModel.toggleSelect(obj.key) else details = obj },
                            onLongClick = { viewModel.toggleSelect(obj.key) }
                        )
                    }
                    if (ui.cursor != null) {
                        item("more") {
                            OutlinedButton(onClick = { viewModel.load(false) }, enabled = !ui.loadingMore, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(if (ui.loadingMore) "Loading" else "Load more")
                            }
                        }
                    }
                }
            }
        }
    }

    details?.let { obj ->
        ObjectDetailsDialog(
            obj = obj,
            onDismiss = { details = null },
            onPreview = { details = null; viewModel.preview(obj) },
            onDownload = { details = null; pendingDownload = obj; saver.launch(obj.key.substringAfterLast('/')) },
            onCopyKey = { copyToClipboard(context, "object key", obj.key) },
            onDelete = { details = null; viewModel.clearSelection(); viewModel.toggleSelect(obj.key); confirmDelete = true }
        )
    }
    ui.preview?.let { PreviewDialog(it, onDismiss = viewModel::closePreview) }
    ui.uploadPlan?.let { plan ->
        var wifiOnly by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::cancelUpload,
            title = { Text("Upload ${plan.files.size} file${if (plan.files.size == 1) "" else "s"}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dev.cfmobile.app.ui.components.MutationContextBlock(
                        MutationContext(profile = profileLabel, account = accountLabel, target = "${viewModel.bucket}/${ui.prefix}")
                    )
                    plan.files.forEachIndexed { i, f ->
                        val method = plan.methods.getOrNull(i)
                        Text(
                            "${f.name}  ${if (f.size >= 0) formatBytes(f.size) else "size unknown"}" +
                                (method?.let { if (it == TransferRepository.METHOD_S3) "  multipart" else "" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace
                        )
                    }
                    plan.problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text("Total ${formatBytes(plan.totalBytes)}", style = MaterialTheme.typography.bodyMedium)
                    if (plan.needsMobileConfirm) Text("You are on a metered connection. This upload may use significant mobile data.", color = StatusColors.warning, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wait for Wi-Fi", modifier = Modifier.weight(1f))
                        Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                    }
                    Text("Existing objects with the same key are replaced.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { askForNotifications(); viewModel.confirmUpload(wifiOnly) }, enabled = plan.problems.size < plan.files.size) { Text("Upload") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelUpload) { Text("Cancel") } }
        )
    }
    if (confirmDelete) {
        val n = ui.selected.size
        DestructiveConfirmDialog(
            title = if (n == 1) "Delete ${ui.selected.first().substringAfterLast('/')}?" else "Delete $n objects?",
            consequence = "$n object${if (n == 1) "" else "s"} will be permanently deleted from ${viewModel.bucket}. R2 has no undo for this.",
            confirmLabel = "Delete",
            risk = DestructiveRisk.HIGH,
            context = MutationContext(profile = profileLabel, account = accountLabel, target = viewModel.bucket + "/" + ui.prefix),
            onConfirm = { confirmDelete = false; viewModel.deleteSelected() },
            onDismiss = { confirmDelete = false }
        )
    }
    if (credsOpen) {
        CredentialsDialog(
            hasSaved = ui.hasS3Credentials,
            onSave = { id, secret -> credsOpen = false; viewModel.saveCredentials(id, secret, null) },
            onRemove = { credsOpen = false; viewModel.removeCredentials() },
            onDismiss = { credsOpen = false }
        )
    }
}

@Composable
private fun Breadcrumbs(bucket: String, prefix: String, onGo: (String) -> Unit) {
    val parts = prefix.trimEnd('/').split('/').filter { it.isNotEmpty() }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onGo("") }) { Text(bucket) }
        parts.forEachIndexed { i, p ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onGo(parts.take(i + 1).joinToString("/") + "/") }) { Text(p) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ObjectRow(obj: R2Object, name: String, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selecting) Checkbox(checked = selected, onCheckedChange = { onClick() })
        else Icon(Icons.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(formatBytes(obj.sizeBytes), obj.lastModified?.take(19)?.replace('T', ' '), obj.storageClass?.takeIf { it != "Standard" }).joinToString("  "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    ThinDivider()
}

@Composable
private fun ObjectDetailsDialog(obj: R2Object, onDismiss: () -> Unit, onPreview: () -> Unit, onDownload: () -> Unit, onCopyKey: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(obj.key.substringAfterLast('/'), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KeyValueRow("Key", obj.key, monospace = true)
                KeyValueRow("Size", "${formatBytes(obj.sizeBytes)} (${obj.sizeBytes} bytes)")
                obj.lastModified?.let { KeyValueRow("Modified", it) }
                obj.etag?.let { KeyValueRow("ETag", it, monospace = true) }
                obj.httpMetadata?.contentType?.let { KeyValueRow("Content type", it) }
                obj.httpMetadata?.cacheControl?.let { KeyValueRow("Cache-Control", it) }
                obj.httpMetadata?.contentDisposition?.let { KeyValueRow("Disposition", it) }
                obj.storageClass?.let { KeyValueRow("Storage class", it) }
                obj.customMetadata?.forEach { (k, v) -> KeyValueRow(k, v, monospace = true) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = onPreview) { Text("Preview") }
                    TextButton(onClick = onDownload) { Text("Download") }
                    TextButton(onClick = onCopyKey) { Text("Copy key") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
    )
}

@Composable
private fun PreviewDialog(preview: Preview, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (preview) {
                    is Preview.Text -> preview.key; is Preview.Image -> preview.key; is Preview.Unsupported -> preview.key; is Preview.Loading -> preview.key
                }.substringAfterLast('/'),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            when (preview) {
                is Preview.Loading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                is Preview.Text -> Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(preview.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.horizontalScroll(rememberScrollState()))
                    }
                    if (preview.truncated) Text("Showing the first 512 KB.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is Preview.Image -> Image(bitmap = preview.bitmap.asImageBitmap(), contentDescription = preview.key, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp))
                is Preview.Unsupported -> Text(preview.reason)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CredentialsDialog(hasSaved: Boolean, onSave: (String, String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    SecureWindow()
    var id by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("R2 S3 credentials") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "These are R2 API keys (Access Key ID and Secret Access Key), not your Cloudflare API token. They enable resumable multipart uploads and files over 300 MB. They are verified against this bucket, encrypted with the Android Keystore, and only ever sent to r2.cloudflarestorage.com.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (hasSaved) Text("Credentials are saved for this account on this device.", color = StatusColors.success, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Access Key ID") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                OutlinedTextField(
                    value = secret, onValueChange = { secret = it }, label = { Text("Secret Access Key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(id, secret) }) { Text("Verify and save") } },
        dismissButton = {
            Row {
                if (hasSaved) TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
