package dev.cfmobile.app.ui.r2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import dev.cfmobile.app.data.remote.dto.R2CustomDomain
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.ToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R2BucketScreen(bucketName: String, viewModel: R2BucketViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmPublish by remember { mutableStateOf(false) }
    var confirmClearCors by remember { mutableStateOf(false) }
    var confirmDeleteDomain by remember { mutableStateOf<R2CustomDomain?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bucket settings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            bucketName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }

            SectionTitle("Public access")
            uiState.managedDomain?.let { managed ->
                ToggleRow(
                    title = "r2.dev development URL",
                    subtitle = managed.domain?.let { "https://$it" }
                        ?: "Serve this bucket publicly on Cloudflare's r2.dev hostname",
                    checked = managed.enabled,
                    isSaving = uiState.isSaving,
                    onToggle = { enabled ->
                        // Turning this on makes every object in the bucket world-readable.
                        if (enabled) confirmPublish = true else viewModel.setManagedDomain(false)
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SectionTitle("Custom domains")
            StateContent(state = uiState.customDomains, onRetry = viewModel::refresh) { domains ->
                Column(Modifier.fillMaxWidth()) {
                    if (domains.isEmpty()) {
                        EmptySection("No custom domains on this bucket. Connecting one is done from the dashboard, which walks through DNS and certificate setup.")
                    } else {
                        domains.forEach { domain ->
                            DomainRow(
                                domain = domain,
                                isDeleting = uiState.deletingDomain == domain.domain,
                                onDelete = { confirmDeleteDomain = domain }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SectionTitle("CORS")
            if (uiState.corsRules.isEmpty()) {
                EmptySection("No CORS policy - browsers on other origins can't call this bucket directly.")
            } else {
                uiState.corsRules.forEach { rule ->
                    Text(
                        corsSummary(rule),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp, 4.dp)
                    )
                }
                // Cloudflare has no per-rule delete here, only "replace the policy" or
                // "remove it", so removing the whole thing is the only edit this app offers.
                TextButton(
                    onClick = { confirmClearCors = true },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text("Remove CORS policy") }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SectionTitle("Lifecycle")
            if (uiState.lifecycleRules.isEmpty()) {
                EmptySection("No lifecycle rules - objects stay until something deletes them.")
            } else {
                uiState.lifecycleRules.forEach { rule ->
                    Text(
                        lifecycleSummary(rule),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp, 4.dp)
                    )
                }
                EmptySection("Read-only: editing lifecycle rules replaces the whole policy, which is a data-loss risk worth a bigger screen than a phone.")
            }
        }
    }

    if (confirmPublish) {
        AlertDialog(
            onDismissRequest = { confirmPublish = false },
            title = { Text("Make this bucket public?") },
            text = {
                Text("Every object in \"$bucketName\" becomes readable by anyone with the r2.dev URL, with no authentication and no rate limit you control.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmPublish = false
                    viewModel.setManagedDomain(true)
                }) { Text("Make public") }
            },
            dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text("Cancel") } }
        )
    }

    if (confirmClearCors) {
        AlertDialog(
            onDismissRequest = { confirmClearCors = false },
            title = { Text("Remove the CORS policy?") },
            text = { Text("Browser requests from other origins will start failing. This removes every rule, not just one.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearCors = false
                    viewModel.clearCors()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmClearCors = false }) { Text("Cancel") } }
        )
    }

    confirmDeleteDomain?.let { domain ->
        AlertDialog(
            onDismissRequest = { confirmDeleteDomain = null },
            title = { Text("Disconnect this domain?") },
            text = { Text("\"${domain.domain}\" will stop serving objects from this bucket.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteDomain = null
                    viewModel.deleteCustomDomain(domain)
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteDomain = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
    )
}

@Composable
private fun EmptySection(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 8.dp)
    )
}

@Composable
private fun DomainRow(domain: R2CustomDomain, isDeleting: Boolean, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(domain.domain, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            Text(
                customDomainStatus(domain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDeleting) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Disconnect domain", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
