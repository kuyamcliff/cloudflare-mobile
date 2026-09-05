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

data class LoginUiState(
    val label: String = "",
    val token: String = "",
    val isVerifying: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

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
        _uiState.update { it.copy(isVerifying = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.addToken(state.label, state.token)) {
                is ApiResult.Success -> _uiState.update { it.copy(isVerifying = false, success = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(isVerifying = false, error = result.message) }
            }
        }
    }
}
