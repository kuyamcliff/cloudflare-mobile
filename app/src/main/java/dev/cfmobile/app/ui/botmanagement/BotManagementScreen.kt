package dev.cfmobile.app.ui.botmanagement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        Column(Modifier.padding(padding)) {
            StateContent(state = uiState.botFightMode, onRetry = viewModel::load) { enabled ->
                ToggleRow(
                    title = "Bot Fight Mode",
                    subtitle = "Challenges requests Cloudflare identifies as automated traffic from bad bots",
                    checked = enabled,
                    isSaving = uiState.isSaving,
                    onToggle = viewModel::setBotFightMode
                )
            }
        }
    }
}
