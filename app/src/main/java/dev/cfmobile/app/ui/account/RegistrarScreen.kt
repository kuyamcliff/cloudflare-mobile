package dev.cfmobile.app.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.ReadOnlyListRow

@Composable
fun RegistrarScreen(viewModel: RegistrarViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Registrar",
        onBack = onBack,
        state = uiState.domains,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No domains registered through Cloudflare",
        key = { it.name },
        searchPlaceholder = "Search domains",
        searchMatches = { domain, query -> domain.name.contains(query, ignoreCase = true) },
        header = {
            Text(
                // Same reason the Billing screen can't change a plan: every write here either
                // authorizes a charge or moves a domain between registrars.
                "Read-only. Renewals, auto-renew, transfer locks, and transfers all cost money or move a domain, so this app doesn't change them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 4.dp)
            )
        }
    ) { domain ->
        ReadOnlyListRow(
            icon = Icons.Filled.Language,
            title = domain.name,
            monospaceTitle = true,
            subtitle = registrarSummary(domain),
            detail = registrarDetail(domain)
        )
    }
}
