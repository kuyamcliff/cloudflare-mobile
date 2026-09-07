package dev.cfmobile.app.ui.certificates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificatesScreen(zoneName: String, viewModel: CertificatesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Certificates & DNSSEC", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            if (uiState.tab == CertificatesTab.HOSTNAMES) {
                FloatingActionButton(onClick = viewModel::openForm) {
                    Icon(Icons.Filled.Add, contentDescription = "Add custom hostname")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = CertificatesTab.entries.indexOf(uiState.tab)) {
                CertificatesTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    CertificatesTab.CERTIFICATES -> "Edge certs"
                                    CertificatesTab.HOSTNAMES -> "Hostnames"
                                    CertificatesTab.DNSSEC -> "DNSSEC"
                                }
                            )
                        }
                    )
                }
            }
            when (uiState.tab) {
                CertificatesTab.CERTIFICATES -> RefreshableStateContent(
                    state = uiState.packs,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh
                ) { packs ->
                    if (packs.isEmpty()) {
                        EmptyState("No certificate packs on this zone")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(packs, key = { it.id }) { pack ->
                                ReadOnlyListRow(
                                    icon = Icons.Filled.Lock,
                                    title = pack.hosts.firstOrNull() ?: pack.id,
                                    subtitle = pack.hosts.drop(1).joinToString(", ").ifBlank { null },
                                    detail = listOfNotNull(
                                        pack.type,
                                        pack.certificateAuthority,
                                        pack.validityDays?.let { "$it-day validity" }
                                    ).joinToString(" · ").ifBlank { null },
                                    trailing = {
                                        pack.status?.let {
                                            StatusPill(
                                                it.replaceFirstChar { c -> c.uppercase() },
                                                if (it == "active") MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }

                CertificatesTab.HOSTNAMES -> RefreshableStateContent(
                    state = uiState.hostnames,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh
                ) { hostnames ->
                    if (hostnames.isEmpty()) {
                        EmptyState("No custom hostnames yet.\n\nCustom hostnames are how SSL for SaaS serves your customers' own domains through this zone.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                            items(hostnames, key = { it.id }) { hostname ->
                                DeletableListRow(
                                    icon = Icons.Filled.Dns,
                                    title = hostname.hostname,
                                    monospaceTitle = true,
                                    subtitle = listOfNotNull(hostname.status, hostname.ssl?.status)
                                        .joinToString(" · ").ifBlank { null },
                                    detail = hostname.ssl?.method?.let { "Validation: $it" },
                                    isDeleting = uiState.deletingHostnameId == hostname.id,
                                    deleteContentDescription = "Delete hostname",
                                    confirmTitle = "Delete custom hostname?",
                                    confirmText = "\"${hostname.hostname}\" will stop being served through this zone and its certificate will be removed. This can't be undone.",
                                    onDelete = { viewModel.deleteHostname(hostname) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }

                CertificatesTab.DNSSEC -> DnssecTab(uiState, viewModel)
            }
        }
    }

    uiState.form?.let { form ->
        AddHostnameSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@Composable
private fun DnssecTab(uiState: CertificatesUiState, viewModel: CertificatesViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ToggleRow(
            title = "DNSSEC",
            subtitle = dnssecStatusLabel(uiState.dnssec),
            checked = isDnssecActive(uiState.dnssec),
            isSaving = uiState.isTogglingDnssec,
            onToggle = viewModel::setDnssecEnabled
        )
        uiState.dnssecError?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        val dnssec = uiState.dnssec
        if (dnssec != null && dnssec.ds != null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Add this DS record at your registrar to finish enabling DNSSEC. Cloudflare can't publish it for you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CopyableValue(label = "DS record", value = dnssec.ds)
                dnssec.digest?.let { CopyableValue(label = "Digest", value = it) }
                listOfNotNull(
                    dnssec.keyTag?.let { "Key tag $it" },
                    dnssec.algorithm?.let { "Algorithm $it" },
                    dnssec.digestType?.let { "Digest type $it" }
                ).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text(
                "Turn DNSSEC on to generate the DS record you'll publish at your registrar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/** A long value with a copy button - a DS record is far too long to retype by hand. */
@Composable
private fun CopyableValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CopyIconButton(value = value, label = label)
        }
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHostnameSheet(form: CustomHostnameFormState, onDismiss: () -> Unit, viewModel: CertificatesViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Add custom hostname", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
            OutlinedTextField(
                value = form.hostname,
                onValueChange = { v -> viewModel.updateForm { it.copy(hostname = v) } },
                label = { Text("Hostname") },
                placeholder = { Text("app.customer.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OptionRow(
                title = "Validation method",
                currentValue = form.method.name,
                options = HostnameValidationMethod.entries.map { it.name to it.label },
                isSaving = false,
                onSelect = { v -> viewModel.updateForm { it.copy(method = HostnameValidationMethod.valueOf(v)) } }
            )
            if (form.error != null) {
                Text(
                    form.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            FormActions(
                isSaving = form.isSaving,
                onCancel = onDismiss,
                onSave = viewModel::save,
                saveLabel = "Add",
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}
