package dev.cfmobile.app.ui.dns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.DnsRecord
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.StateContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsScreen(viewModel: DnsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Records") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddForm) {
                Icon(Icons.Filled.Add, contentDescription = "Add record")
            }
        }
    ) { padding ->
        StateContent(state = uiState.records, onRetry = viewModel::refresh) { records ->
            if (records.isEmpty()) {
                EmptyState("No DNS records yet", Modifier.padding(padding))
            } else {
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(records, key = { it.id }) { record ->
                        DnsRecordRow(
                            record = record,
                            isDeleting = uiState.deletingId == record.id,
                            onClick = { viewModel.openEditForm(record) },
                            onDelete = { viewModel.delete(record) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
}

@Composable
private fun DnsRecordRow(record: DnsRecord, isDeleting: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AssistChip(onClick = onClick, label = { Text(record.type) })
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(record.name, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            Text(
                record.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (record.proxied == true) {
            FilterChip(selected = true, onClick = {}, label = { Text("Proxied", style = MaterialTheme.typography.bodySmall) })
        }
        if (isDeleting) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        } else {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete record?") },
            text = { Text("${record.type} record for ${record.name} will be removed immediately.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (form.editingId != null) "Edit record" else "Add record", style = MaterialTheme.typography.titleMedium)

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = form.type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
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

            OutlinedTextField(
                value = form.content,
                onValueChange = { value -> onFieldChange { it.copy(content = value) } },
                label = { Text("Content") },
                placeholder = { Text(placeholderFor(form.type)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (form.type == "MX") {
                OutlinedTextField(
                    value = form.priority,
                    onValueChange = { value -> onFieldChange { it.copy(priority = value.filter(Char::isDigit)) } },
                    label = { Text("Priority") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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

private fun placeholderFor(type: String): String = when (type) {
    "A" -> "203.0.113.10"
    "AAAA" -> "2001:db8::1"
    "CNAME" -> "target.example.com"
    "MX" -> "mail.example.com"
    "TXT" -> "v=spf1 ..."
    "NS" -> "ns1.example.com"
    "CAA" -> "0 issue \"letsencrypt.org\""
    else -> ""
}
