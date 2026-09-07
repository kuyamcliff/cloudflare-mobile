package dev.cfmobile.app.ui.dns

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.DnsRecord
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.ListSearchField
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.ZoneScopedTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsScreen(viewModel: DnsViewModel, zoneName: String, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var detailRecord by remember { mutableStateOf<DnsRecord?>(null) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            // Reading the picked file is blocking I/O - this callback otherwise runs on the
            // main thread, so a large zone file could jank or ANR without this dispatch.
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }
            }
            if (text != null) viewModel.importZoneFile(text)
        }
    }

    LaunchedEffect(uiState.exportedZoneFile) {
        val zoneFile = uiState.exportedZoneFile ?: return@LaunchedEffect
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "$zoneName DNS records")
            putExtra(Intent.EXTRA_TEXT, zoneFile)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export DNS records"))
        viewModel.exportedZoneFileConsumed()
    }

    LaunchedEffect(uiState.notice) {
        val notice = uiState.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice)
        viewModel.noticeConsumed()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        if (uiState.isBatchDeleting) {
                            CircularProgressIndicator(Modifier.padding(12.dp))
                        } else {
                            IconButton(onClick = { confirmBatchDelete = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { ZoneScopedTitle("DNS Records", zoneName) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Export zone file") },
                                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                                onClick = { overflowExpanded = false; viewModel.exportZoneFile() }
                            )
                            DropdownMenuItem(
                                text = { Text("Import zone file") },
                                leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                                onClick = { overflowExpanded = false; importLauncher.launch("text/plain") }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                FloatingActionButton(onClick = viewModel::openAddForm) {
                    Icon(Icons.Filled.Add, contentDescription = "Add record")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // A busy zone can carry hundreds of records, so searching beats scrolling.
            ListSearchField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = "Search records"
            )
            RefreshableStateContent(
                state = uiState.records,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { allRecords ->
            val query = uiState.query.trim()
            val records = allRecords.filter { dnsRecordMatches(it, query) }
            if (allRecords.isEmpty()) {
                EmptyState("No DNS records yet")
            } else if (records.isEmpty()) {
                EmptyState("No records match \"$query\".")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(records, key = { it.id }) { record ->
                        DnsRecordRow(
                            record = record,
                            zoneName = zoneName,
                            isDeleting = uiState.deletingId == record.id,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = record.id in uiState.selectedIds,
                            onClick = {
                                if (uiState.isSelectionMode) viewModel.toggleSelection(record.id) else viewModel.openEditForm(record)
                            },
                            onLongClick = { viewModel.toggleSelection(record.id) },
                            onShowDetail = { detailRecord = record },
                            onDelete = { viewModel.delete(record) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
            }
        }
    }

    uiState.form?.let { form ->
        DnsFormSheet(
            form = form,
            onDismiss = viewModel::closeForm,
            onFieldChange = viewModel::updateForm,
            onSave = viewModel::save
        )
    }

    detailRecord?.let { record ->
        DnsRecordDetailSheet(record = record, zoneName = zoneName, onDismiss = { detailRecord = null })
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("Delete ${uiState.selectedIds.size} records?") },
            text = { Text("These records on $zoneName will be removed immediately.") },
            confirmButton = {
                TextButton(onClick = { confirmBatchDelete = false; viewModel.batchDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DnsRecordRow(
    record: DnsRecord,
    zoneName: String,
    isDeleting: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowDetail: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (isSelectionMode) Modifier.semantics { selected = isSelected } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isSelectionMode) {
            // Purely visual - the Row above already carries the click and the selected state
            // (via semantics), so this doesn't need its own separate TalkBack stop.
            Checkbox(checked = isSelected, onCheckedChange = null)
        }
        AssistChip(onClick = onClick, label = { Text(record.type) })
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(record.name, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            Text(
                recordSummary(record),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (record.proxied == true) {
            FilterChip(selected = true, onClick = {}, label = { Text("Proxied", style = MaterialTheme.typography.bodySmall) })
        }
        if (!isSelectionMode) {
            IconButton(onClick = onShowDetail) {
                Icon(Icons.Filled.Info, contentDescription = "Record details")
            }
            if (isDeleting) {
                CircularProgressIndicator(Modifier.padding(4.dp))
            } else {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete record?") },
            text = { Text("${record.type} record for ${record.name} on $zoneName will be removed immediately.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

/** A one-line summary for the list row - the structured data types don't have a `content`
 *  string, so this stitches together whichever fields best identify the record at a glance. */
private fun recordSummary(record: DnsRecord): String {
    val d = record.data
    return when (record.type) {
        "SRV" -> "${d?.priority ?: "-"} ${d?.weight ?: "-"} ${d?.port ?: "-"} ${d?.target ?: ""}"
        "URI" -> "${record.priority ?: "-"} ${d?.weight ?: "-"} ${d?.content ?: ""}"
        "TLSA" -> "${d?.usage ?: "-"} ${d?.selector ?: "-"} ${d?.matchingType ?: "-"} ${d?.certificate ?: ""}"
        "NAPTR" -> "${d?.order ?: "-"} ${d?.preference ?: "-"} ${d?.service ?: ""}"
        "SSHFP" -> "${d?.algorithm ?: "-"} ${d?.type ?: "-"} ${d?.fingerprint ?: ""}"
        "CERT" -> "${d?.type ?: "-"} ${d?.certificate ?: ""}"
        else -> record.content
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsRecordDetailSheet(record: DnsRecord, zoneName: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${record.type} record", style = MaterialTheme.typography.titleMedium)
            DetailRow("Zone", zoneName)
            DetailRow("Name", record.name, copyable = true)
            DetailRow("TTL", if (record.ttl == 1) "Auto" else "${record.ttl}s")
            if (record.type in PROXIABLE_TYPES) {
                DetailRow("Proxied", if (record.proxied == true) "Yes" else "No")
            }
            when (record.type) {
                "SRV" -> record.data?.let {
                    DetailRow("Priority", it.priority?.toString() ?: "-")
                    DetailRow("Weight", it.weight?.toString() ?: "-")
                    DetailRow("Port", it.port?.toString() ?: "-")
                    DetailRow("Target", it.target ?: "", copyable = true)
                }
                "URI" -> {
                    DetailRow("Priority", record.priority?.toString() ?: "-")
                    record.data?.let {
                        DetailRow("Weight", it.weight?.toString() ?: "-")
                        DetailRow("Target", it.content ?: "", copyable = true)
                    }
                }
                "TLSA" -> record.data?.let {
                    DetailRow("Usage", it.usage?.toString() ?: "-")
                    DetailRow("Selector", it.selector?.toString() ?: "-")
                    DetailRow("Matching type", it.matchingType?.toString() ?: "-")
                    DetailRow("Certificate", it.certificate ?: "", copyable = true)
                }
                "NAPTR" -> record.data?.let {
                    DetailRow("Order", it.order?.toString() ?: "-")
                    DetailRow("Preference", it.preference?.toString() ?: "-")
                    DetailRow("Flags", it.flags ?: "-")
                    DetailRow("Service", it.service ?: "-")
                    DetailRow("Regex", it.regex ?: "-")
                    DetailRow("Replacement", it.replacement ?: "-", copyable = true)
                }
                "SSHFP" -> record.data?.let {
                    DetailRow("Algorithm", it.algorithm?.toString() ?: "-")
                    DetailRow("Type", it.type?.toString() ?: "-")
                    DetailRow("Fingerprint", it.fingerprint ?: "", copyable = true)
                }
                "CERT" -> record.data?.let {
                    DetailRow("Algorithm", it.algorithm?.toString() ?: "-")
                    DetailRow("Key tag", it.keyTag?.toString() ?: "-")
                    DetailRow("Type", it.type?.toString() ?: "-")
                    DetailRow("Certificate", it.certificate ?: "", copyable = true)
                }
                else -> DetailRow("Content", record.content, copyable = true)
            }
            if (record.type == "MX") DetailRow("Priority", record.priority?.toString() ?: "-")
            record.comment?.takeIf { it.isNotBlank() }?.let { DetailRow("Comment", it) }
            record.createdOn?.let { DetailRow("Created", it) }
            record.modifiedOn?.let { DetailRow("Modified", it) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, copyable: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
        if (copyable && value.isNotBlank()) {
            CopyIconButton(value = value, label = label.lowercase())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsFormSheet(
    form: DnsFormState,
    onDismiss: () -> Unit,
    onFieldChange: ((DnsFormState) -> DnsFormState) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (form.editingId != null) "Edit record" else "Add record", style = MaterialTheme.typography.titleMedium)

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = form.type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DNS_RECORD_TYPES.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = { onFieldChange { it.copy(type = type) }; expanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onFieldChange { it.copy(name = value) } },
                label = { Text("Name") },
                placeholder = { Text("e.g. www or @ for root") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (form.type !in DATA_DRIVEN_TYPES) {
                OutlinedTextField(
                    value = form.content,
                    onValueChange = { value -> onFieldChange { it.copy(content = value) } },
                    label = { Text("Content") },
                    placeholder = { Text(placeholderFor(form.type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (form.type == "MX") {
                NumberField("Priority", form.priority) { value -> onFieldChange { it.copy(priority = value) } }
            }

            if (form.type == "SRV") {
                NumberField("Priority", form.srvPriority) { value -> onFieldChange { it.copy(srvPriority = value) } }
                NumberField("Weight", form.srvWeight) { value -> onFieldChange { it.copy(srvWeight = value) } }
                NumberField("Port", form.srvPort) { value -> onFieldChange { it.copy(srvPort = value) } }
                TextField_("Target", form.srvTarget, "target.example.com") { value -> onFieldChange { it.copy(srvTarget = value) } }
            }

            if (form.type == "URI") {
                NumberField("Priority", form.priority) { value -> onFieldChange { it.copy(priority = value) } }
                NumberField("Weight", form.uriWeight) { value -> onFieldChange { it.copy(uriWeight = value) } }
                TextField_("Target URI", form.uriTarget, "https://example.com/") { value -> onFieldChange { it.copy(uriTarget = value) } }
            }

            if (form.type == "TLSA") {
                NumberField("Usage", form.tlsaUsage) { value -> onFieldChange { it.copy(tlsaUsage = value) } }
                NumberField("Selector", form.tlsaSelector) { value -> onFieldChange { it.copy(tlsaSelector = value) } }
                NumberField("Matching type", form.tlsaMatchingType) { value -> onFieldChange { it.copy(tlsaMatchingType = value) } }
                TextField_("Certificate association data", form.tlsaCertificate, "hex-encoded data") { value -> onFieldChange { it.copy(tlsaCertificate = value) } }
            }

            if (form.type == "NAPTR") {
                NumberField("Order", form.naptrOrder) { value -> onFieldChange { it.copy(naptrOrder = value) } }
                NumberField("Preference", form.naptrPreference) { value -> onFieldChange { it.copy(naptrPreference = value) } }
                TextField_("Flags", form.naptrFlags, "U") { value -> onFieldChange { it.copy(naptrFlags = value) } }
                TextField_("Service", form.naptrService, "SIP+D2U") { value -> onFieldChange { it.copy(naptrService = value) } }
                TextField_("Regex", form.naptrRegex, "") { value -> onFieldChange { it.copy(naptrRegex = value) } }
                TextField_("Replacement", form.naptrReplacement, ".") { value -> onFieldChange { it.copy(naptrReplacement = value) } }
            }

            if (form.type == "SSHFP") {
                NumberField("Algorithm", form.sshfpAlgorithm) { value -> onFieldChange { it.copy(sshfpAlgorithm = value) } }
                NumberField("Fingerprint type", form.sshfpType) { value -> onFieldChange { it.copy(sshfpType = value) } }
                TextField_("Fingerprint", form.sshfpFingerprint, "hex-encoded fingerprint") { value -> onFieldChange { it.copy(sshfpFingerprint = value) } }
            }

            if (form.type == "CERT") {
                NumberField("Algorithm", form.certAlgorithm) { value -> onFieldChange { it.copy(certAlgorithm = value) } }
                NumberField("Key tag", form.certKeyTag) { value -> onFieldChange { it.copy(certKeyTag = value) } }
                NumberField("Certificate type", form.certType) { value -> onFieldChange { it.copy(certType = value) } }
                TextField_("Certificate", form.certCertificate, "base64-encoded certificate") { value -> onFieldChange { it.copy(certCertificate = value) } }
            }

            OutlinedTextField(
                value = form.ttl,
                onValueChange = { value -> onFieldChange { it.copy(ttl = value.filter(Char::isDigit)) } },
                label = { Text("TTL (seconds, 1 = Auto)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (form.type in PROXIABLE_TYPES) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = form.proxied, onCheckedChange = { value -> onFieldChange { it.copy(proxied = value) } })
                    Text("Proxy through Cloudflare")
                }
            }

            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onSave, enabled = !form.isSaving) {
                    if (form.isSaving) {
                        CircularProgressIndicator(Modifier.padding(end = 6.dp))
                    }
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TextField_(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = false,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun placeholderFor(type: String): String = when (type) {
    "A" -> "203.0.113.10"
    "AAAA" -> "2001:db8::1"
    "CNAME" -> "target.example.com"
    "MX" -> "mail.example.com"
    "TXT" -> "v=spf1 ..."
    "NS" -> "ns1.example.com"
    "PTR" -> "target.example.com"
    "SPF" -> "v=spf1 ..."
    "CAA" -> "0 issue \"letsencrypt.org\""
    else -> ""
}
