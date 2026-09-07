package dev.cfmobile.app.ui.botmanagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.BotManagementConfig
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotManagementScreen(viewModel: BotManagementViewModel, zoneName: String, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Bot Management", zoneName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
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
            StateContent(state = uiState.config, onRetry = viewModel::load) { config ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Cloudflare omits fight_mode on plans where Super Bot Fight Mode replaces
                    // it, so the free-tier toggle only appears when the zone actually has it.
                    config.fightMode?.let { enabled ->
                        ToggleRow(
                            title = "Bot Fight Mode",
                            subtitle = "Challenges requests Cloudflare identifies as automated traffic from bad bots",
                            checked = enabled,
                            isSaving = uiState.isSaving,
                            onToggle = viewModel::setFightMode
                        )
                    }
                    if (hasSuperBotFightMode(config)) {
                        SuperBotFightMode(config, uiState.isSaving, viewModel)
                    } else if (config.fightMode == null) {
                        Text(
                            "This zone's plan doesn't expose any bot settings this app can change. Bot analytics and per-rule bot scores aren't implemented.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuperBotFightMode(
    config: BotManagementConfig,
    isSaving: Boolean,
    viewModel: BotManagementViewModel
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Text(
        "Super Bot Fight Mode",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
    )
    config.definitelyAutomated?.let { action ->
        SbfmRow("Definitely automated", "Traffic Cloudflare is confident is a bot", action, isSaving, viewModel::setDefinitelyAutomated)
    }
    config.likelyAutomated?.let { action ->
        SbfmRow("Likely automated", "Traffic that scores as probably automated", action, isSaving, viewModel::setLikelyAutomated)
    }
    config.verifiedBots?.let { action ->
        SbfmRow("Verified bots", "Search engine crawlers and other bots Cloudflare has verified", action, isSaving, viewModel::setVerifiedBots)
    }
    config.staticResourceProtection?.let { enabled ->
        ToggleRow(
            title = "Protect static resources",
            subtitle = "Applies the same actions to images, CSS, and other static files",
            checked = enabled,
            isSaving = isSaving,
            onToggle = viewModel::setStaticResourceProtection
        )
    }
    config.optimizeWordpress?.let { enabled ->
        ToggleRow(
            title = "Optimize for WordPress",
            subtitle = "Skips bot checks on WordPress paths that legitimate plugins use",
            checked = enabled,
            isSaving = isSaving,
            onToggle = viewModel::setOptimizeWordpress
        )
    }
}

@Composable
private fun SbfmRow(
    title: String,
    subtitle: String,
    action: String,
    isSaving: Boolean,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp))
        OptionRow(
            title = title,
            currentValue = action,
            options = SBFM_ACTIONS,
            isSaving = isSaving,
            onSelect = onSelect
        )
    }
}
