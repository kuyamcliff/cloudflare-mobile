package dev.cfmobile.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Real steps, reported as they happen (spec 248, 249). No percentages. */
enum class ConnectStep(val label: String) {
    VERIFYING("Verifying token"),
    DISCOVERING("Discovering available controls"),
    DONE("Preparing your dashboard")
}

data class LoginUiState(
    val label: String = "",
    val token: String = "",
    val isVerifying: Boolean = false,
    val step: ConnectStep? = null,
    val error: String? = null,
    val success: Boolean = false
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val discover: suspend () -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onLabelChange(value: String) = _uiState.update { it.copy(label = value, error = null) }
    fun onTokenChange(value: String) = _uiState.update { it.copy(token = value, error = null) }

    fun submit() {
        val state = _uiState.value
        if (state.token.isBlank()) {
            _uiState.update { it.copy(error = "Enter an API token") }
            return
        }
        _uiState.update { it.copy(isVerifying = true, error = null, step = ConnectStep.VERIFYING) }
        viewModelScope.launch {
            when (val result = authRepository.addToken(state.label, state.token)) {
                is ApiResult.Success -> {
                    // The secret now lives only in the encrypted store; drop it from UI state.
                    _uiState.update { it.copy(token = "", step = ConnectStep.DISCOVERING) }
                    discover()
                    _uiState.update { it.copy(isVerifying = false, step = ConnectStep.DONE, success = true) }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isVerifying = false, step = null, error = result.message) }
            }
        }
    }
}
