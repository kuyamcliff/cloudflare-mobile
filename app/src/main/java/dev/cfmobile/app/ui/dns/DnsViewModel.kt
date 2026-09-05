package dev.cfmobile.app.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.DnsRecord
import dev.cfmobile.app.data.remote.dto.DnsRecordWrite
import dev.cfmobile.app.data.repository.DnsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val PROXIABLE_TYPES = setOf("A", "AAAA", "CNAME")
val DNS_RECORD_TYPES = listOf("A", "AAAA", "CNAME", "TXT", "MX", "NS", "CAA")

data class DnsFormState(
    val editingId: String? = null,
    val type: String = "A",
    val name: String = "",
    val content: String = "",
    val ttl: String = "1",
    val proxied: Boolean = false,
    val priority: String = "10",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class DnsUiState(
    val records: UiState<List<DnsRecord>> = UiState.Loading,
    val form: DnsFormState? = null,
    val deletingId: String? = null
)

class DnsViewModel(
    private val zoneId: String,
    private val repository: DnsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DnsUiState())
    val uiState: StateFlow<DnsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(records = UiState.Loading) }
        viewModelScope.launch {
            val result = repository.listRecords(zoneId)
            _uiState.update {
                it.copy(
                    records = when (result) {
                        is ApiResult.Success -> UiState.Data(result.data)
                        is ApiResult.Failure -> UiState.Error(result.message)
                    }
                )
            }
        }
    }

    fun openAddForm() = _uiState.update { it.copy(form = DnsFormState()) }

    fun openEditForm(record: DnsRecord) = _uiState.update {
        it.copy(
            form = DnsFormState(
                editingId = record.id,
                type = record.type,
                name = record.name,
                content = record.content,
                ttl = record.ttl.toString(),
                proxied = record.proxied ?: false,
                priority = (record.priority ?: 10).toString()
            )
        )
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (DnsFormState) -> DnsFormState) {
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }
    }

    fun save() {
        val form = _uiState.value.form ?: return
        val ttl = form.ttl.toIntOrNull() ?: 1
        if (form.name.isBlank() || form.content.isBlank()) {
            updateForm { it.copy(error = "Name and content are required") }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }

        val write = DnsRecordWrite(
            type = form.type,
            name = form.name.trim(),
            content = form.content.trim(),
            ttl = ttl,
            proxied = if (form.type in PROXIABLE_TYPES) form.proxied else null,
            priority = if (form.type == "MX") form.priority.toIntOrNull() ?: 10 else null
        )

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
}
