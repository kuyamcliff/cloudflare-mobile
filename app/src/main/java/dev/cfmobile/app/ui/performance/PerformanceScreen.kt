package dev.cfmobile.app.ui.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import dev.cfmobile.app.data.remote.dto.ManagedHeader
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(zoneName: String, viewModel: PerformanceViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Routing & Cache", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxWidth().padding(padding).padding(32.dp)) {
                CircularProgressIndicator(Modifier.padding(4.dp))
            }
            return@Scaffold
        }

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

            SectionTitle("Argo")
            // A control the zone's plan doesn't include isn't rendered at all - a dead switch
            // would be worse than its absence, which the note at the bottom explains.
            uiState.smartRouting?.let { value ->
                ToggleRow(
                    title = "Smart Routing",
                    subtitle = "Route requests over Cloudflare's network instead of the public internet",
                    checked = isOn(value),
                    isSaving = uiState.isSaving,
                    onToggle = viewModel::setSmartRouting
                )
            }
            uiState.tieredCaching?.let { value ->
                ToggleRow(
                    title = "Tiered Caching",
                    subtitle = "Let edge locations fetch from an upper-tier cache instead of your origin",
                    checked = isOn(value),
                    isSaving = uiState.isSaving,
                    onToggle = viewModel::setTieredCaching
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SectionTitle("Cache")
            uiState.cacheReserve?.let { value ->
                ToggleRow(
                    title = "Cache Reserve",
                    subtitle = "Keep cached content in R2 so it survives eviction - this is billed storage",
                    checked = isOn(value),
                    isSaving = uiState.isSaving,
                    onToggle = viewModel::setCacheReserve
                )
            }
            uiState.smartTieredCache?.let { value ->
                ToggleRow(
                    title = "Smart Tiered Cache Topology",
                    subtitle = "Let Cloudflare pick the upper tier rather than fixing one",
                    checked = isOn(value),
                    isSaving = uiState.isSaving,
                    onToggle = viewModel::setSmartTieredCache
                )
            }
            uiState.regionalTieredCache?.let { value ->
                ToggleRow(
                    title = "Regional Tiered Cache",
                    subtitle = "Add a regional tier for traffic that's far from the upper tier",
                    checked = isOn(value),
                    isSaving = uiState.isSaving,
                    onToggle = viewModel::setRegionalTieredCache
                )
            }

            uiState.managedHeaders?.let { headers ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SectionTitle("Managed Transforms")
                Text(
                    "Headers Cloudflare can add or remove without you writing a Transform Rule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp)
                )
                headers.requestHeaders.forEach { header ->
                    ManagedHeaderRow(header, uiState.isSaving, isRequest = true, viewModel = viewModel)
                }
                headers.responseHeaders.forEach { header ->
                    ManagedHeaderRow(header, uiState.isSaving, isRequest = false, viewModel = viewModel)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SectionTitle("URL Normalization")
            when (val normalization = uiState.urlNormalization) {
                is UiState.Loading -> Unit
                is UiState.Error -> Text(
                    normalization.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
                is UiState.Data -> {
                    OptionRow(
                        title = "Normalization type",
                        subtitle = "How Cloudflare rewrites incoming URLs before rules and the cache see them",
                        currentValue = normalization.value.type ?: URL_NORMALIZATION_TYPES.first().first,
                        options = URL_NORMALIZATION_TYPES,
                        isSaving = uiState.isSaving,
                        onSelect = { viewModel.setUrlNormalization(type = it) }
                    )
                    OptionRow(
                        title = "Applies to",
                        currentValue = normalization.value.scope ?: URL_NORMALIZATION_SCOPES.first().first,
                        options = URL_NORMALIZATION_SCOPES,
                        isSaving = uiState.isSaving,
                        onSelect = { viewModel.setUrlNormalization(scope = it) }
                    )
                }
            }

            if (uiState.unavailable.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Text(
                    "Not available on this zone: ${uiState.unavailable.joinToString(", ")}. Cloudflare returns these only on the plans that include them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 12.dp)
                )
            }
        }
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
private fun ManagedHeaderRow(
    header: ManagedHeader,
    isSaving: Boolean,
    isRequest: Boolean,
    viewModel: PerformanceViewModel
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        ToggleRow(
            title = managedHeaderLabel(header),
            subtitle = if (isRequest) "Request header" else "Response header",
            checked = header.enabled,
            isSaving = isSaving,
            // Cloudflare refuses to enable a header that clashes with one of your own
            // Transform Rules, so the switch is disabled rather than failing on tap.
            enabled = managedHeaderConflict(header) == null,
            onToggle = { viewModel.setManagedHeader(header, it, isRequest) }
        )
        managedHeaderConflict(header)?.let { conflict ->
            Text(
                conflict,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp)
            )
        }
    }
}
