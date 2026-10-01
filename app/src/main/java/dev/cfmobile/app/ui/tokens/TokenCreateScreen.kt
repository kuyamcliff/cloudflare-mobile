package dev.cfmobile.app.ui.tokens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.tokens.AccountSelection
import dev.cfmobile.app.core.tokens.ExpiryChoice
import dev.cfmobile.app.core.tokens.PermissionKind
import dev.cfmobile.app.core.tokens.TokenTemplates
import dev.cfmobile.app.core.tokens.ZoneSelection
import dev.cfmobile.app.core.tokens.kind
import dev.cfmobile.app.data.remote.dto.PermissionGroup
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.components.MutationContext
import dev.cfmobile.app.ui.components.MutationContextBlock
import dev.cfmobile.app.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenCreateScreen(viewModel: TokenCreateViewModel, ownerLabel: String, isAccountOwned: Boolean, profileLabel: String?, onBack: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var leaveWithSecret by remember { mutableStateOf(false) }

    // Leaving while the secret is on screen loses it for good, so ask first (spec 140).
    BackHandler(enabled = ui.secret != null && !ui.savedAsProfile) { leaveWithSecret = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (ui.secret != null) "Token created" else "Create token")
                        Text(ownerLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (ui.secret != null && !ui.savedAsProfile) leaveWithSecret = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (ui.submitting) LinearProgressIndicator(Modifier.fillMaxWidth())
            val secret = ui.secret
            if (secret != null) {
                TokenSecretPanel(
                    title = ui.createdName ?: "New token",
                    secret = secret,
                    savedAsProfile = ui.savedAsProfile,
                    savingProfile = ui.savingProfile,
                    onSaveAsProfile = viewModel::saveAsProfile,
                    onDone = onBack
                )
                return@Column
            }
            StateContent(ui.catalog, onRetry = viewModel::load) { catalog ->
                if (ui.reviewing) ReviewStep(viewModel, ui, ownerLabel, profileLabel)
                else EditStep(viewModel, ui, catalog, isAccountOwned, onAddPermission = { picker = true })
                if (picker) PermissionPicker(catalog, ui.draft.selected.map { it.id }.toSet(), onToggle = viewModel::toggle, onDismiss = { picker = false })
            }
        }
    }
    if (leaveWithSecret) {
        AlertDialog(
            onDismissRequest = { leaveWithSecret = false },
            title = { Text("Leave without saving the secret?") },
            text = { Text("Cloudflare will not show this secret again. If you have not copied it, you will need to roll the token to get a new one.") },
            confirmButton = { TextButton(onClick = { leaveWithSecret = false; onBack() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { leaveWithSecret = false }) { Text("Stay") } }
        )
    }
}

@Composable
private fun EditStep(vm: TokenCreateViewModel, ui: TokenCreateUiState, catalog: List<PermissionGroup>, isAccountOwned: Boolean, onAddPermission: () -> Unit) {
    val d = ui.draft
    LazyColumn(Modifier.fillMaxSize()) {
        item("name") {
            SectionHeader("Name")
            OutlinedTextField(value = d.name, onValueChange = { v -> vm.edit { it.copy(name = v) } }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            SectionHeader("Start from")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TokenTemplates.all.forEach { t -> FilterChip(selected = false, onClick = { vm.applyTemplate(t) }, label = { Text(t.title) }) }
            }
            ui.templateNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item("perms") {
            SectionHeader("Permissions (${d.selected.size})") { TextButton(onClick = onAddPermission) { Text("Add") } }
            if (d.selected.isEmpty()) Text("No permissions yet. Add from ${catalog.size} permission groups Cloudflare currently offers.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(d.selected, key = { "sel-" + it.id }) { g ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.bodyMedium)
                    Text(g.kind().label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.toggle(g) }) { Icon(Icons.Filled.Close, "Remove ${g.name}") }
            }
        }
        item("resources") {
            if (PermissionKind.ACCOUNT in ui.kinds && !isAccountOwned) {
                SectionHeader("Account resources")
                RadioLine("All accounts", d.accounts is AccountSelection.All) { vm.edit { it.copy(accounts = AccountSelection.All) } }
                RadioLine("Specific accounts", d.accounts is AccountSelection.Specific) { vm.edit { it.copy(accounts = AccountSelection.Specific(emptySet())) } }
                (d.accounts as? AccountSelection.Specific)?.let { sel ->
                    ui.accounts.forEach { a ->
                        CheckLine(a.name, a.id in sel.ids) { vm.edit { it.copy(accounts = AccountSelection.Specific(if (a.id in sel.ids) sel.ids - a.id else sel.ids + a.id)) } }
                    }
                }
            }
            if (PermissionKind.ZONE in ui.kinds) {
                SectionHeader("Zone resources")
                if (!isAccountOwned) RadioLine("All zones", d.zones is ZoneSelection.All) { vm.edit { it.copy(zones = ZoneSelection.All) } }
                ui.accounts.forEach { a ->
                    if (!isAccountOwned || (d.zones as? ZoneSelection.AllInAccount)?.accountId == a.id) {
                        RadioLine("All zones in ${a.name}", (d.zones as? ZoneSelection.AllInAccount)?.accountId == a.id) { vm.edit { it.copy(zones = ZoneSelection.AllInAccount(a.id)) } }
                    }
                }
                RadioLine("Specific zones", d.zones is ZoneSelection.Specific) { vm.edit { it.copy(zones = ZoneSelection.Specific(emptySet())) } }
                (d.zones as? ZoneSelection.Specific)?.let { sel ->
                    ui.zones.forEach { z ->
                        CheckLine(z.name, z.id in sel.ids) { vm.edit { it.copy(zones = ZoneSelection.Specific(if (z.id in sel.ids) sel.ids - z.id else sel.ids + z.id)) } }
                    }
                }
            }
        }
        item("conditions") {
            SectionHeader("Client IP filtering")
            OutlinedTextField(
                value = d.allowedIps, onValueChange = { v -> vm.edit { it.copy(allowedIps = v) } },
                label = { Text("Allow only these IPs or CIDRs") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            OutlinedTextField(
                value = d.deniedIps, onValueChange = { v -> vm.edit { it.copy(deniedIps = v) } },
                label = { Text("Deny these IPs or CIDRs") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            SectionHeader("TTL")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpiryChoice.entries.forEach { e -> FilterChip(selected = d.expiry == e, onClick = { vm.edit { it.copy(expiry = e) } }, label = { Text(e.label) }) }
            }
            OutlinedTextField(
                value = d.notBefore, onValueChange = { v -> vm.edit { it.copy(notBefore = v) } },
                label = { Text("Start (optional, e.g. 2026-10-01)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        item("next") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = vm::review, modifier = Modifier.fillMaxWidth()) { Text("Review") }
            }
        }
    }
}

@Composable
private fun ReviewStep(vm: TokenCreateViewModel, ui: TokenCreateUiState, ownerLabel: String, profileLabel: String?) {
    LazyColumn(Modifier.fillMaxSize()) {
        item("head") {
            SectionHeader("This token will allow")
            Text(ui.draft.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(vm.summaryLines()) { line -> Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        item("cond") {
            SectionHeader("Conditions")
            Text("Expires: ${ui.draft.expiry.label}", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Client IPs: " + (ui.draft.allowedIps.ifBlank { "any" }),
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "This describes the scope Cloudflare will grant. Effective access also depends on the owner's own membership and the account's plan.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)
            )
            Column(Modifier.padding(horizontal = 16.dp)) { MutationContextBlock(MutationContext(profile = profileLabel, target = "New $ownerLabel token")) }
        }
        item("actions") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = vm::create, enabled = !ui.submitting, modifier = Modifier.fillMaxWidth()) { Text("Create token") }
                OutlinedButton(onClick = vm::backToEdit, modifier = Modifier.fillMaxWidth()) { Text("Back to edit") }
            }
        }
    }
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CheckLine(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onClick() })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Searchable by name, product word, or scope (spec 143). */
@Composable
private fun PermissionPicker(catalog: List<PermissionGroup>, selected: Set<String>, onToggle: (PermissionGroup) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<PermissionKind?>(null) }
    val filtered = catalog.filter { g ->
        (kind == null || g.kind() == kind) &&
            (query.isBlank() || query.trim().split(Regex("\\s+")).all { t -> g.name.contains(t, true) || g.kind().label.contains(t, true) || g.description.orEmpty().contains(t, true) })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search, e.g. r2 or dns") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("All") })
                    PermissionKind.entries.filter { k -> catalog.any { it.kind() == k } }.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it.id }) { g ->
                        Row(Modifier.fillMaxWidth().clickable { onToggle(g) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = g.id in selected, onCheckedChange = { onToggle(g) })
                            Column {
                                Text(g.name, style = MaterialTheme.typography.bodyMedium)
                                Text(g.kind().label + (g.description?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
