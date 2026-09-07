package dev.cfmobile.app.ui.caching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.ZoneScopedTitle
import dev.cfmobile.app.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachingScreen(viewModel: CachingViewModel, zoneName: String, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmPurge by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Caching", zoneName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        StateContent(state = uiState.settings, onRetry = viewModel::load) { settings ->
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                OptionRow(
                    title = "Caching level",
                    currentValue = settings.cacheLevel,
                    isSaving = uiState.saving == SavingField.CACHE_LEVEL,
                    options = listOf(
                        "aggressive" to "Standard",
                        "basic" to "No query string",
                        "simplified" to "Ignore query string"
                    ),
                    onSelect = viewModel::setCacheLevel
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                ToggleRow(
                    title = "Development Mode",
                    subtitle = "Bypass cache for 3 hours while you make changes",
                    checked = settings.developmentMode == "on",
                    isSaving = uiState.saving == SavingField.DEV_MODE,
                    onToggle = viewModel::setDevelopmentMode
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                OptionRow(
                    title = "Browser cache TTL",
                    currentValue = settings.browserCacheTtl.toString(),
                    isSaving = uiState.saving == SavingField.BROWSER_TTL,
                    options = listOf(
                        "0" to "Respect existing headers",
                        "1800" to "30 minutes",
                        "3600" to "1 hour",
                        "14400" to "4 hours",
                        "86400" to "1 day",
                        "604800" to "1 week"
                    ),
                    onSelect = { viewModel.setBrowserCacheTtl(it.toInt()) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Purge Cache", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Removes all cached content for this domain from Cloudflare's edge immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { confirmPurge = true },
                        enabled = uiState.saving != SavingField.PURGE,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                    ) {
                        Text(if (uiState.saving == SavingField.PURGE) "Purging..." else "Purge Everything")
                    }
                    uiState.purgeResult?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text("Purge everything?") },
            text = { Text("All cached files for $zoneName will be removed from Cloudflare's edge network right away.") },
            confirmButton = {
                TextButton(onClick = { confirmPurge = false; viewModel.purgeEverything() }) { Text("Purge") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = false }) { Text("Cancel") }
            }
        )
    }
}
