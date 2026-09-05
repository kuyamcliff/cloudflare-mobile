package dev.cfmobile.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSignedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmSignOutAll by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<AccountSummary?>(null) }

    LaunchedEffect(uiState.signedOut) {
        if (uiState.signedOut) onSignedOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onAddAccount) { Icon(Icons.Filled.PersonAdd, contentDescription = "Add account") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(uiState.accounts, key = { it.id }) { token ->
                    ListItem(
                        headlineContent = { Text(token.label) },
                        supportingContent = token.email?.let { { Text(it) } },
                        leadingContent = {
                            Icon(
                                if (token.id == uiState.activeId) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (token.id == uiState.activeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { pendingRemove = token }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.clickable(enabled = token.id != uiState.activeId) { viewModel.switchTo(token.id) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }

            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tokens are encrypted and stored only on this device. They're never uploaded anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { confirmSignOutAll = true },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign out of all accounts")
                }
            }
        }
    }

    pendingRemove?.let { token ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove ${token.label}?") },
            text = { Text("This deletes the stored API token from this device.") },
            confirmButton = { TextButton(onClick = { viewModel.remove(token.id); pendingRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } }
        )
    }

    if (confirmSignOutAll) {
        AlertDialog(
            onDismissRequest = { confirmSignOutAll = false },
            title = { Text("Sign out of all accounts?") },
            text = { Text("All stored API tokens will be deleted from this device.") },
            confirmButton = { TextButton(onClick = { viewModel.signOutAll(); confirmSignOutAll = false }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOutAll = false }) { Text("Cancel") } }
        )
    }
}
