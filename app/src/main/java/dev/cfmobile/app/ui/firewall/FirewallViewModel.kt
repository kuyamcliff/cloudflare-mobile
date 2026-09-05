package dev.cfmobile.app.ui.firewall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AccessRule
import dev.cfmobile.app.data.remote.dto.FirewallRule
import dev.cfmobile.app.data.repository.FirewallRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FirewallRuleForm(val expression: String = "", val action: String = "block", val description: String = "", val isSaving: Boolean = false, val error: String? = null)
data class AccessRuleForm(val ip: String = "", val mode: String = "block", val notes: String = "", val isSaving: Boolean = false, val error: String? = null)

data class FirewallUiState(
    val rules: UiState<List<FirewallRule>> = UiState.Loading,
    val accessRules: UiState<List<AccessRule>> = UiState.Loading,
    val ruleForm: FirewallRuleForm? = null,
    val accessRuleForm: AccessRuleForm? = null
)

class FirewallViewModel(
    private val zoneId: String,
    private val repository: FirewallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirewallUiState())
    val uiState: StateFlow<FirewallUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        loadRules()
        loadAccessRules()
    }

    private fun loadRules() {
        _uiState.update { it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            _uiState.update {
                it.copy(rules = when (val r = repository.listRules(zoneId)) {
                    is ApiResult.Success -> UiState.Data(r.data)
                    is ApiResult.Failure -> UiState.Error(r.message)
                })
            }
        }
    }

    private fun loadAccessRules() {
        _uiState.update { it.copy(accessRules = UiState.Loading) }
        viewModelScope.launch {
            _uiState.update {
                it.copy(accessRules = when (val r = repository.listAccessRules(zoneId)) {
                    is ApiResult.Success -> UiState.Data(r.data)
                    is ApiResult.Failure -> UiState.Error(r.message)
                })
            }
        }
    }

    fun openRuleForm() = _uiState.update { it.copy(ruleForm = FirewallRuleForm()) }
    fun closeRuleForm() = _uiState.update { it.copy(ruleForm = null) }
    fun updateRuleForm(transform: (FirewallRuleForm) -> FirewallRuleForm) =
        _uiState.update { state -> state.ruleForm?.let { state.copy(ruleForm = transform(it)) } ?: state }

    fun saveRule() {
        val form = _uiState.value.ruleForm ?: return
        if (form.expression.isBlank()) {
            updateRuleForm { it.copy(error = "Expression is required") }
            return
        }
        updateRuleForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createRule(zoneId, form.expression.trim(), form.action, form.description.trim().ifBlank { null })) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(ruleForm = null) }
                    loadRules()
                }
                is ApiResult.Failure -> updateRuleForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteRule(rule: FirewallRule) {
        viewModelScope.launch {
            repository.deleteRule(zoneId, rule.id)
            loadRules()
        }
    }

    fun openAccessRuleForm() = _uiState.update { it.copy(accessRuleForm = AccessRuleForm()) }
    fun closeAccessRuleForm() = _uiState.update { it.copy(accessRuleForm = null) }
    fun updateAccessRuleForm(transform: (AccessRuleForm) -> AccessRuleForm) =
        _uiState.update { state -> state.accessRuleForm?.let { state.copy(accessRuleForm = transform(it)) } ?: state }

    fun saveAccessRule() {
        val form = _uiState.value.accessRuleForm ?: return
        if (form.ip.isBlank()) {
            updateAccessRuleForm { it.copy(error = "IP address or CIDR range is required") }
            return
        }
        updateAccessRuleForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createAccessRule(zoneId, form.mode, form.ip.trim(), form.notes.trim().ifBlank { null })) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(accessRuleForm = null) }
                    loadAccessRules()
                }
                is ApiResult.Failure -> updateAccessRuleForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteAccessRule(rule: AccessRule) {
        viewModelScope.launch {
            repository.deleteAccessRule(zoneId, rule.id)
            loadAccessRules()
        }
    }
}
