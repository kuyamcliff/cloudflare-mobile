package dev.cfmobile.app.ui.zonesettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.ZoneScopedTitle

/** Renders any [ZoneSettingSpec] group - Speed, Network, Scrape Shield, and so on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneSettingsGroupScreen(
    title: String,
    zoneName: String,
    viewModel: ZoneSettingsGroupViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle(title, zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            RefreshableStateContent(
                state = uiState.values,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { values ->
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(viewModel.specs, key = { it.id }) { spec ->
                        val unavailable = spec.id in uiState.unavailableIds
                        if (unavailable) {
                            // Saying so beats rendering a control that looks off but isn't
                            // actually reflecting anything.
                            UnavailableSettingRow(spec)
                        } else {
                            when (spec) {
                                is ZoneSettingSpec.Toggle -> ToggleRow(
                                    title = spec.title,
                                    subtitle = spec.subtitle,
                                    checked = values[spec.id].isSettingOn(),
                                    isSaving = uiState.savingId == spec.id,
                                    onToggle = { viewModel.toggle(spec, it) }
                                )

                                is ZoneSettingSpec.Options -> OptionRow(
                                    title = spec.title,
                                    subtitle = spec.subtitle,
                                    currentValue = values[spec.id].orEmpty(),
                                    options = spec.options,
                                    isSaving = uiState.savingId == spec.id,
                                    onSelect = { viewModel.setValue(spec, it) }
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun UnavailableSettingRow(spec: ZoneSettingSpec) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(spec.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Not available on this zone's plan",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
