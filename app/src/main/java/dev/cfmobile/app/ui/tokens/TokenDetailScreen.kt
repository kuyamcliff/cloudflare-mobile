package dev.cfmobile.app.ui.tokens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.capabilities.DestructiveRisk
import dev.cfmobile.app.data.remote.dto.ApiToken
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.DestructiveConfirmDialog
import dev.cfmobile.app.ui.components.KeyValueRow
import dev.cfmobile.app.ui.components.MutationContext
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.components.ThinDivider
import dev.cfmobile.app.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TokenDetailScreen(
    viewModel: TokenDetailViewModel,
    ownerLabel: String,
    profileLabel: String?,
    onBack: () -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmRoll by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }
    LaunchedEffect(ui.deleted) { if (ui.deleted) onBack() }

    val tokenName = (ui.token as? UiState.Data)?.value?.name ?: "Token"
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tokenName)
                        Text(ownerLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val secret = ui.rolledSecret
            if (secret != null) {
                TokenSecretPanel(
                    title = "Token rolled",
                    secret = secret,
                    savedAsProfile = ui.savedAsProfile,
                    savingProfile = ui.savingProfile,
                    onSaveAsProfile = viewModel::saveRolledAsProfile,
                    onDone = viewModel::dismissSecret
                )
                return@Column
            }
            StateContent(ui.token, onRetry = viewModel::load) { token ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (ui.isThisDevicesToken) {
                        Text(
                            "This app is signed in with this token.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusColors.info,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    SectionHeader("Overview")
                    KeyValueRow("Status", token.status ?: "unknown")
                    KeyValueRow("Token ID", token.id, monospace = true, copyable = true)
                    token.issuedOn?.let { KeyValueRow("Issued", it) }
                    token.modifiedOn?.let { KeyValueRow("Modified", it) }
                    KeyValueRow("Expires", token.expiresOn ?: "Never")
                    token.notBefore?.let { KeyValueRow("Not before", it) }
                    KeyValueRow("Last used", token.lastUsedOn ?: "Never")
                    val allowed = token.condition?.requestIp?.allowed.orEmpty()
                    val denied = token.condition?.requestIp?.denied.orEmpty()
                    KeyValueRow("Allowed IPs", allowed.joinToString().ifBlank { "Any" }, monospace = allowed.isNotEmpty())
                    if (denied.isNotEmpty()) KeyValueRow("Denied IPs", denied.joinToString(), monospace = true)

                    if (ui.flags.isNotEmpty()) {
                        SectionHeader("Observations")
                        ui.flags.forEach { f ->
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Badge(f.label, StatusColors.warning)
                                Text(f.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "Local observations from the token's settings, not a security rating.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    SectionHeader("Permissions")
                    if (ui.scopes.isEmpty()) {
                        Text("Cloudflare returned no policies for this token.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    ui.scopes.forEach { line ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(line.kind, style = MaterialTheme.typography.titleSmall)
                                if (line.deny) Badge("Deny", StatusColors.error)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                line.permissions.forEach { Badge(it, MaterialTheme.colorScheme.primary) }
                            }
                            line.resources.forEach { r -> Text(r, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        ThinDivider()
                    }

                    SectionHeader("Actions")
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit name, status, expiry and IPs") }
                    }
                    SectionHeader("Danger zone")
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { confirmRoll = true }, modifier = Modifier.fillMaxWidth()) { Text("Roll token secret") }
                        OutlinedButton(onClick = { confirmRevoke = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Revoke in Cloudflare", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text(
                        "Revoking changes your Cloudflare account. To only remove a saved token from this device, use Profiles in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                    if (editing) EditTokenDialog(token, onDismiss = { editing = false }) { name, active, expires, allow, deny ->
                        editing = false
                        viewModel.update(name, active, expires, allow, deny)
                    }
                }
            }
        }
    }

    if (confirmRoll) {
        DestructiveConfirmDialog(
            title = "Roll $tokenName?",
            consequence = "The current secret stops working immediately. The replacement keeps the same permissions and is shown once." +
                if (ui.isThisDevicesToken) " This device will switch to the new secret automatically." else " Anything using the old secret must be updated.",
            confirmLabel = "Roll token",
            risk = DestructiveRisk.CRITICAL,
            context = MutationContext(profile = profileLabel, target = "$ownerLabel token $tokenName"),
            typedConfirmation = tokenName,
            onConfirm = { confirmRoll = false; viewModel.roll() },
            onDismiss = { confirmRoll = false }
        )
    }
    if (confirmRevoke) {
        DestructiveConfirmDialog(
            title = "Revoke $tokenName?",
            consequence = "This revokes the token in Cloudflare. Applications using it stop working." +
                if (ui.isThisDevicesToken) " This app is signed in with it and will lose access." else "",
            confirmLabel = "Revoke",
            risk = DestructiveRisk.CRITICAL,
            context = MutationContext(profile = profileLabel, target = "$ownerLabel token $tokenName"),
            typedConfirmation = tokenName,
            onConfirm = { confirmRevoke = false; viewModel.revoke() },
            onDismiss = { confirmRevoke = false }
        )
    }
}

@Composable
private fun EditTokenDialog(
    token: ApiToken,
    onDismiss: () -> Unit,
    onSave: (name: String, active: Boolean, expiresOn: String?, allowed: String, denied: String) -> Unit
) {
    var name by remember { mutableStateOf(token.name) }
    var active by remember { mutableStateOf(token.status != "disabled") }
    var expires by remember { mutableStateOf(token.expiresOn.orEmpty()) }
    var allowed by remember { mutableStateOf(token.condition?.requestIp?.allowed.orEmpty().joinToString("\n")) }
    var denied by remember { mutableStateOf(token.condition?.requestIp?.denied.orEmpty().joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit token") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                OutlinedTextField(value = expires, onValueChange = { expires = it }, label = { Text("Expires (ISO date, blank for never)") }, singleLine = true)
                OutlinedTextField(value = allowed, onValueChange = { allowed = it }, label = { Text("Allowed IPs or CIDRs") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                OutlinedTextField(value = denied, onValueChange = { denied = it }, label = { Text("Denied IPs or CIDRs") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                Text("Policies stay exactly as they are.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, active, expires.ifBlank { null }, allowed, denied) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
