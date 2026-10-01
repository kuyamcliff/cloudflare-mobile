package dev.cfmobile.app.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import dev.cfmobile.app.core.security.AppSettings
import dev.cfmobile.app.core.security.ThemeMode
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.DestructiveConfirmDialog
import dev.cfmobile.app.ui.components.NavRow
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.components.ThinDivider
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSignedOut: () -> Unit,
    onSecurityClick: () -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var manage by remember { mutableStateOf<AccountSummary?>(null) }
    var pendingRemove by remember { mutableStateOf<AccountSummary?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    LaunchedEffect(ui.signedOut) { if (ui.signedOut) onSignedOut() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onAddAccount) { Icon(Icons.Filled.PersonAdd, "Add profile") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item("ph") { SectionHeader("Profiles on this device") }
            items(ui.accounts, key = { it.id }) { p ->
                NavRow(
                    title = p.label,
                    supporting = listOfNotNull(
                        p.fingerprint?.let { "fingerprint $it" },
                        p.expiresOn?.let { "expires $it" },
                        p.lastVerifiedAt?.let { "verified " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
                    ).joinToString(" · ").ifBlank { null },
                    badge = { if (p.id == ui.activeId) Badge("Active", MaterialTheme.colorScheme.primary) },
                    onClick = { manage = p }
                )
                ThinDivider()
            }
            item("security") {
                SectionHeader("Security")
                NavRow("App lock and screenshots", "Biometric unlock, auto-lock timeout, screenshot protection", onClick = onSecurityClick)
                ChoiceRow("Clear copied secrets after", AppSettings.CLIPBOARD_OPTIONS, settings.clipboardClearSeconds, { "${it}s" }) { v -> viewModel.updateSettings { it.copy(clipboardClearSeconds = v) } }
            }
            item("appearance") {
                SectionHeader("Appearance")
                ChoiceRow("Theme", ThemeMode.entries.toList(), settings.themeMode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { v -> viewModel.updateSettings { it.copy(themeMode = v) } }
            }
            item("transfers") {
                SectionHeader("Transfers")
                SwitchRow("Wi-Fi only by default", settings.transfersWifiOnly) { v -> viewModel.updateSettings { it.copy(transfersWifiOnly = v) } }
                ChoiceRow("Parallel multipart parts", AppSettings.CONCURRENCY_OPTIONS, settings.transferConcurrency, { "$it" }) { v -> viewModel.updateSettings { it.copy(transferConcurrency = v) } }
                ChoiceRow("Confirm mobile data above", listOf(0, 50, 100, 500), settings.confirmMobileDataAboveMb, { if (it == 0) "Always" else "$it MB" }) { v -> viewModel.updateSettings { it.copy(confirmMobileDataAboveMb = v) } }
            }
            item("data") {
                SectionHeader("Data on this device")
                ChoiceRow("Keep request history", AppSettings.HISTORY_OPTIONS, settings.historyRetentionDays, { "${it}d" }) { v -> viewModel.updateSettings { it.copy(historyRetentionDays = v) } }
                NavRow("Clear cached Cloudflare data", "Zone cache and capability cache", onClick = viewModel::clearCache)
                NavRow("Clear request history", "All profiles", onClick = viewModel::clearHistory)
                NavRow("Clear saved templates and queries", null, onClick = viewModel::clearSaved)
                SectionHeader("Credentials")
                NavRow("Remove all tokens and local data", "Removes every saved token, R2 key and local record from this device", onClick = { confirmWipe = true })
            }
        }
    }

    manage?.let { p ->
        var label by remember(p.id) { mutableStateOf(p.label) }
        AlertDialog(
            onDismissRequest = { manage = null },
            title = { Text(p.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name") }, singleLine = true)
                    p.tokenId?.let { Text("Token ID $it", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    p.fingerprint?.let { Text("Fingerprint $it", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    Row {
                        if (p.id != ui.activeId) TextButton(onClick = { viewModel.switchTo(p.id); manage = null }) { Text("Use this profile") }
                        TextButton(onClick = { manage = null; pendingRemove = p }) { Text("Remove from device", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.rename(p.id, label); manage = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { manage = null }) { Text("Cancel") } }
        )
    }
    pendingRemove?.let { p ->
        DestructiveConfirmDialog(
            title = "Remove ${p.label} from this device?",
            consequence = "This only removes the saved token and this profile's local history from this device. The token stays valid in Cloudflare; revoke it from API Tokens if it should stop working.",
            confirmLabel = "Remove",
            risk = DestructiveRisk.MEDIUM,
            context = null,
            onConfirm = { viewModel.remove(p.id); pendingRemove = null },
            onDismiss = { pendingRemove = null }
        )
    }
    if (confirmWipe) {
        DestructiveConfirmDialog(
            title = "Remove all local data?",
            consequence = "This removes saved Cloudflare tokens, R2 keys, history, caches and settings from this device. Cloudflare itself is not affected.",
            confirmLabel = "Remove everything",
            risk = DestructiveRisk.CRITICAL,
            context = null,
            typedConfirmation = "REMOVE",
            onConfirm = { confirmWipe = false; viewModel.clearEverything() },
            onDismiss = { confirmWipe = false }
        )
    }
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(text(o)) }) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
