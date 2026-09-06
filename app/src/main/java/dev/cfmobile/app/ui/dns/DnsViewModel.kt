package dev.cfmobile.app.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.DnsRecord
import dev.cfmobile.app.data.remote.dto.DnsRecordData
import dev.cfmobile.app.data.remote.dto.DnsRecordWrite
import dev.cfmobile.app.data.repository.DnsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val PROXIABLE_TYPES = setOf("A", "AAAA", "CNAME")

/** Types whose value can't be expressed as a plain `content` string and instead use Cloudflare's
 *  structured `data` object (PRD §9: full record type coverage, not just the common few). */
val DATA_DRIVEN_TYPES = setOf("SRV", "URI", "TLSA", "NAPTR", "SSHFP", "CERT")

val DNS_RECORD_TYPES = listOf(
    "A", "AAAA", "CNAME", "TXT", "MX", "NS", "PTR", "SPF", "CAA",
    "SRV", "URI", "TLSA", "NAPTR", "SSHFP", "CERT"
)

data class DnsFormState(
    val editingId: String? = null,
    val type: String = "A",
    val name: String = "",
    val content: String = "",
    val ttl: String = "1",
    val proxied: Boolean = false,
    val priority: String = "10", // MX, URI
    val srvPriority: String = "10",
    val srvWeight: String = "1",
    val srvPort: String = "",
    val srvTarget: String = "",
    val uriWeight: String = "1",
    val uriTarget: String = "",
    val tlsaUsage: String = "0",
    val tlsaSelector: String = "0",
    val tlsaMatchingType: String = "0",
    val tlsaCertificate: String = "",
    val naptrOrder: String = "10",
    val naptrPreference: String = "10",
    val naptrFlags: String = "",
    val naptrService: String = "",
    val naptrRegex: String = "",
    val naptrReplacement: String = "",
    val sshfpAlgorithm: String = "1",
    val sshfpType: String = "1",
    val sshfpFingerprint: String = "",
    val certAlgorithm: String = "",
    val certKeyTag: String = "",
    val certType: String = "",
    val certCertificate: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class DnsUiState(
    val records: UiState<List<DnsRecord>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val query: String = "",
    val form: DnsFormState? = null,
    val deletingId: String? = null,
    val selectedIds: Set<String> = emptySet(),
    val isBatchDeleting: Boolean = false,
    val exportedZoneFile: String? = null,
    val notice: String? = null
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

/** Checks only what each record type actually requires - a blank "content" is fine for a
 *  SRV/URI/TLSA/NAPTR/SSHFP/CERT record since those never use that field (PRD §9). */
fun validateDnsForm(form: DnsFormState): String? {
    if (form.name.isBlank()) return "Name is required"
    return when (form.type) {
        "SRV" -> when {
            form.srvTarget.isBlank() -> "Target is required"
            form.srvPort.toIntOrNull() == null -> "Port must be a number"
            else -> null
        }
        "URI" -> if (form.uriTarget.isBlank()) "Target URI is required" else null
        "TLSA" -> if (form.tlsaCertificate.isBlank()) "Certificate association data is required" else null
        "NAPTR" -> if (form.naptrService.isBlank() && form.naptrRegex.isBlank() && form.naptrReplacement.isBlank()) {
            "Service, regex, or replacement is required"
        } else null
        "SSHFP" -> if (form.sshfpFingerprint.isBlank()) "Fingerprint is required" else null
        "CERT" -> if (form.certCertificate.isBlank()) "Certificate data is required" else null
        else -> if (form.content.isBlank()) "Content is required" else null
    }
}

/** Pure so the payload for every record type - especially the structured `data`-driven ones -
 *  is directly testable without a ViewModel or network. See DnsViewModelTest. */
fun buildDnsRecordWrite(form: DnsFormState, ttl: Int): DnsRecordWrite {
    val name = form.name.trim()
    return when (form.type) {
        "MX" -> DnsRecordWrite(
            type = form.type, name = name, content = form.content.trim(), ttl = ttl,
            priority = form.priority.toIntOrNull() ?: 10
        )
        "SRV" -> DnsRecordWrite(
            type = form.type, name = name, content = "", ttl = ttl,
            data = DnsRecordData(
                priority = form.srvPriority.toIntOrNull() ?: 10,
                weight = form.srvWeight.toIntOrNull() ?: 1,
                port = form.srvPort.toIntOrNull(),
                target = form.srvTarget.trim()
            )
        )
        "URI" -> DnsRecordWrite(
            type = form.type, name = name, content = "", ttl = ttl,
            priority = form.priority.toIntOrNull() ?: 10,
            data = DnsRecordData(content = form.uriTarget.trim(), weight = form.uriWeight.toIntOrNull() ?: 1)
        )
        "TLSA" -> DnsRecordWrite(
            type = form.type, name = name, content = "", ttl = ttl,
            data = DnsRecordData(
                usage = form.tlsaUsage.toIntOrNull() ?: 0,
                selector = form.tlsaSelector.toIntOrNull() ?: 0,
                matchingType = form.tlsaMatchingType.toIntOrNull() ?: 0,
                certificate = form.tlsaCertificate.trim()
            )
        )
        "NAPTR" -> DnsRecordWrite(
            type = form.type, name = name, content = "", ttl = ttl,
            data = DnsRecordData(
                order = form.naptrOrder.toIntOrNull() ?: 10,
                preference = form.naptrPreference.toIntOrNull() ?: 10,
                flags = form.naptrFlags.trim(),
                service = form.naptrService.trim(),
                regex = form.naptrRegex.trim(),
                replacement = form.naptrReplacement.trim()
            )
        )
        "SSHFP" -> DnsRecordWrite(
            type = form.type, name = name, content = "", ttl = ttl,
            data = DnsRecordData(
                algorithm = form.sshfpAlgorithm.toIntOrNull() ?: 1,
                type = form.sshfpType.toIntOrNull() ?: 1,
                fingerprint = form.sshfpFingerprint.trim()
            )
        )
        "CERT" -> DnsRecordWrite(
            type = form.type, name = name, content = "", ttl = ttl,
            data = DnsRecordData(
                algorithm = form.certAlgorithm.toIntOrNull(),
                keyTag = form.certKeyTag.toIntOrNull(),
                type = form.certType.toIntOrNull(),
                certificate = form.certCertificate.trim()
            )
        )
        else -> DnsRecordWrite(
            type = form.type, name = name, content = form.content.trim(), ttl = ttl,
            proxied = if (form.type in PROXIABLE_TYPES) form.proxied else null
        )
    }
}

/** Which fields a DNS search matches on. Extracted from the screen so the behaviour people
 *  actually rely on - finding a record by its target IP, not just its name - is unit-tested
 *  rather than living only inside a composable. */
fun dnsRecordMatches(record: DnsRecord, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return record.name.contains(q, ignoreCase = true) ||
        record.content.contains(q, ignoreCase = true) ||
        record.type.contains(q, ignoreCase = true)
}

class DnsViewModel(
    private val zoneId: String,
    private val repository: DnsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DnsUiState())
    val uiState: StateFlow<DnsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /** [isRefresh] keeps the current records on screen during a pull-to-refresh instead of
     *  replacing a full list with a spinner. */
    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(records = UiState.Loading) }
        viewModelScope.launch {
            val result = repository.listRecords(zoneId)
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    records = when (result) {
                        is ApiResult.Success -> UiState.Data(result.data)
                        is ApiResult.Failure -> UiState.Error(ErrorClassifier.classify(result))
                    }
                )
            }
        }
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun openAddForm() = _uiState.update { it.copy(form = DnsFormState()) }

    fun openEditForm(record: DnsRecord) {
        val d = record.data
        _uiState.update {
            it.copy(
                form = DnsFormState(
                    editingId = record.id,
                    type = record.type,
                    name = record.name,
                    content = record.content,
                    ttl = record.ttl.toString(),
                    proxied = record.proxied ?: false,
                    priority = (record.priority ?: 10).toString(),
                    srvPriority = (d?.priority ?: 10).toString(),
                    srvWeight = (d?.weight ?: 1).toString(),
                    srvPort = d?.port?.toString() ?: "",
                    srvTarget = d?.target ?: "",
                    uriWeight = (d?.weight ?: 1).toString(),
                    uriTarget = d?.content ?: "",
                    tlsaUsage = (d?.usage ?: 0).toString(),
                    tlsaSelector = (d?.selector ?: 0).toString(),
                    tlsaMatchingType = (d?.matchingType ?: 0).toString(),
                    tlsaCertificate = d?.certificate ?: "",
                    naptrOrder = (d?.order ?: 10).toString(),
                    naptrPreference = (d?.preference ?: 10).toString(),
                    naptrFlags = d?.flags ?: "",
                    naptrService = d?.service ?: "",
                    naptrRegex = d?.regex ?: "",
                    naptrReplacement = d?.replacement ?: "",
                    sshfpAlgorithm = (d?.algorithm ?: 1).toString(),
                    sshfpType = (d?.type ?: 1).toString(),
                    sshfpFingerprint = d?.fingerprint ?: "",
                    certAlgorithm = d?.algorithm?.toString() ?: "",
                    certKeyTag = d?.keyTag?.toString() ?: "",
                    certType = d?.type?.toString() ?: "",
                    certCertificate = d?.certificate ?: ""
                )
            )
        }
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (DnsFormState) -> DnsFormState) {
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }
    }

    fun save() {
        val form = _uiState.value.form ?: return
        val ttl = form.ttl.toIntOrNull() ?: 1
        val validationError = validateDnsForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }

        val write = buildDnsRecordWrite(form, ttl)

        viewModelScope.launch {
            val result = if (form.editingId != null) {
                repository.updateRecord(zoneId, form.editingId, write)
            } else {
                repository.createRecord(zoneId, write)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    refresh()
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(record: DnsRecord) {
        _uiState.update { it.copy(deletingId = record.id) }
        viewModelScope.launch {
            repository.deleteRecord(zoneId, record.id)
            _uiState.update { it.copy(deletingId = null) }
            refresh()
        }
    }

    fun toggleSelection(recordId: String) = _uiState.update {
        val selection = if (recordId in it.selectedIds) it.selectedIds - recordId else it.selectedIds + recordId
        it.copy(selectedIds = selection)
    }

    fun clearSelection() = _uiState.update { it.copy(selectedIds = emptySet()) }

    /** Cloudflare's batch endpoint (PRD §9) so removing many records is one request instead of
     *  N sequential deletes that could partially fail and leave the zone in a mixed state. */
    fun batchDelete() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(isBatchDeleting = true) }
        viewModelScope.launch {
            val result = repository.batchDeleteRecords(zoneId, ids)
            _uiState.update { it.copy(isBatchDeleting = false, selectedIds = emptySet()) }
            when (result) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _uiState.update { it.copy(notice = result.message) }
            }
        }
    }

    /** PRD §9 zone-file export - the result is plain BIND text handed to the screen to share
     *  through Android's share sheet rather than this ViewModel touching storage directly. */
    fun exportZoneFile() {
        viewModelScope.launch {
            when (val result = repository.exportZoneFile(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(exportedZoneFile = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(notice = result.message) }
            }
        }
    }

    fun exportedZoneFileConsumed() = _uiState.update { it.copy(exportedZoneFile = null) }

    fun importZoneFile(bindFileContent: String) {
        viewModelScope.launch {
            when (val result = repository.importZoneFile(zoneId, bindFileContent, proxied = false)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(notice = "Imported ${result.data.recsAdded} of ${result.data.totalRecordsParsed} records")
                    }
                    refresh()
                }
                is ApiResult.Failure -> _uiState.update { it.copy(notice = result.message) }
            }
        }
    }

    fun noticeConsumed() = _uiState.update { it.copy(notice = null) }
}
