package dev.cfmobile.app.ui.auditlogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AuditLogEntry
import dev.cfmobile.app.data.repository.AuditLogsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuditLogsUiState(
    val entries: UiState<List<AuditLogEntry>> = UiState.Loading
)

/** Read-only - PRD §9 "who changed what, and when." Uses Cloudflare's classic
 *  /accounts/:id/audit_logs endpoint; Cloudflare has since introduced a newer "Unified Audit
 *  Logs" API that may eventually supersede it for some account types, and this request format
 *  hasn't been verified against a live call - see CapabilityRegistry's migrationHint. */
class AuditLogsViewModel(
    private val accountId: String,
    private val repository: AuditLogsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditLogsUiState())
    val uiState: StateFlow<AuditLogsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(entries = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listEntries(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(entries = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(entries = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }
}

fun auditActorLabel(entry: AuditLogEntry): String =
    entry.actor?.email ?: entry.actor?.id ?: "Unknown"

fun auditActionLabel(entry: AuditLogEntry): String {
    val type = entry.action?.type ?: "action"
    return if (entry.action?.result == false) "$type (failed)" else type
}

fun auditResourceLabel(entry: AuditLogEntry): String? {
    val resource = entry.resource ?: return null
    return listOfNotNull(resource.product, resource.type).joinToString(" · ").ifBlank { null }
}
