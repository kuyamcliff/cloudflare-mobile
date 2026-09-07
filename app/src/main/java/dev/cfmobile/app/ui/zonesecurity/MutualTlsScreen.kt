package dev.cfmobile.app.ui.zonesecurity

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.ToggleRow

@Composable
fun MutualTlsScreen(zoneName: String, viewModel: MutualTlsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Client Certificates",
        subtitle = zoneName,
        onBack = onBack,
        state = uiState.certificates,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No client certificates on this zone",
        key = { it.id },
        searchPlaceholder = "Search certificates",
        searchMatches = { certificate, query ->
            certificate.commonName.orEmpty().contains(query, ignoreCase = true)
        },
        header = {
            uiState.originPullsEnabled?.let { enabled ->
                ToggleRow(
                    title = "Authenticated Origin Pulls",
                    subtitle = "Cloudflare presents a client certificate to your origin so it can reject traffic that didn't come through Cloudflare",
                    checked = enabled,
                    isSaving = uiState.isSaving,
                    onToggle = viewModel::setOriginPulls
                )
            }
            uiState.totalTls?.let { totalTls ->
                ToggleRow(
                    title = "Total TLS",
                    subtitle = "Issue certificates for every subdomain, not just the ones on the universal certificate",
                    checked = totalTls.enabled,
                    isSaving = uiState.isSaving,
                    onToggle = { viewModel.setTotalTls(it) }
                )
                if (totalTls.enabled) {
                    OptionRow(
                        title = "Certificate authority",
                        currentValue = totalTls.certificateAuthority ?: TOTAL_TLS_AUTHORITIES.first().first,
                        options = TOTAL_TLS_AUTHORITIES,
                        isSaving = uiState.isSaving,
                        onSelect = { viewModel.setTotalTls(enabled = true, authority = it) }
                    )
                }
            }
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
            Text(
                // Uploading one means holding a private key, which this app doesn't do.
                "Certificates are uploaded from the dashboard or the API - this app lists and revokes them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp)
            )
        }
    ) { certificate ->
        val title = certificate.commonName?.takeIf { it.isNotBlank() } ?: certificate.id
        // A revoked certificate can't be revoked again, so it's a plain row.
        if (isRevoked(certificate)) {
            ReadOnlyListRow(
                icon = Icons.Filled.Badge,
                title = title,
                subtitle = certificateStatusLabel(certificate),
                detail = certificate.issuer
            )
        } else {
            DeletableListRow(
                icon = Icons.Filled.Badge,
                title = title,
                subtitle = certificateStatusLabel(certificate),
                detail = certificate.issuer,
                isDeleting = uiState.revokingId == certificate.id,
                deleteContentDescription = "Revoke certificate",
                confirmTitle = "Revoke this certificate?",
                confirmText = "Clients presenting \"$title\" will stop being accepted. Revoking can't be undone - a replacement has to be issued.",
                onDelete = { viewModel.revoke(certificate) }
            )
        }
    }
}
