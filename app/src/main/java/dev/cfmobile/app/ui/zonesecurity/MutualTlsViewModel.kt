package dev.cfmobile.app.ui.zonesecurity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.ClientCertificate
import dev.cfmobile.app.data.remote.dto.TotalTlsSettings
import dev.cfmobile.app.data.repository.MutualTlsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The certificate authorities Total TLS can issue from. */
val TOTAL_TLS_AUTHORITIES = listOf(
    "google" to "Google Trust Services",
    "lets_encrypt" to "Let's Encrypt",
    "ssl_com" to "SSL.com"
)

data class MutualTlsUiState(
    val certificates: UiState<List<ClientCertificate>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val originPullsEnabled: Boolean? = null,
    val totalTls: TotalTlsSettings? = null,
    val isSaving: Boolean = false,
    val revokingId: String? = null,
    val error: String? = null
)

/** A revoked certificate stays in the list, so the row has to say which it is. */
fun certificateStatusLabel(certificate: ClientCertificate): String = listOfNotNull(
    certificate.status?.replaceFirstChar { it.uppercase() },
    certificate.expiresOn?.let { "expires $it" }
).joinToString(" · ").ifBlank { "Active" }

fun isRevoked(certificate: ClientCertificate): Boolean =
    certificate.status?.lowercase() in setOf("revoked", "pending_revocation")

class MutualTlsViewModel(
    private val zoneId: String,
    private val repository: MutualTlsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MutualTlsUiState())
    val uiState: StateFlow<MutualTlsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /**
     * The certificate list is the screen's content; origin pulls and Total TLS are settings
     * shown above it. A failure on either setting leaves the list alone rather than blanking
     * the screen - they're separate products that happen to share a page.
     */
    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(certificates = UiState.Loading) }
        viewModelScope.launch {
            when (val certificates = repository.listClientCertificates(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(certificates = UiState.Data(certificates.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(certificates = UiState.Error(ErrorClassifier.classify(certificates)))
                }
            }
            when (val pulls = repository.getOriginPulls(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(originPullsEnabled = pulls.data.enabled) }
                is ApiResult.Failure -> Unit
            }
            when (val totalTls = repository.getTotalTls(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(totalTls = totalTls.data, isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun setOriginPulls(enabled: Boolean) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.setOriginPulls(zoneId, enabled)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(originPullsEnabled = result.data.enabled, isSaving = false)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun setTotalTls(enabled: Boolean, authority: String? = null) {
        val current = _uiState.value.totalTls
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            // Keep whichever authority is already configured when only the switch moved.
            val ca = authority ?: current?.certificateAuthority ?: TOTAL_TLS_AUTHORITIES.first().first
            when (val result = repository.setTotalTls(zoneId, enabled, ca)) {
                is ApiResult.Success -> _uiState.update { it.copy(totalTls = result.data, isSaving = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun revoke(certificate: ClientCertificate) {
        _uiState.update { it.copy(revokingId = certificate.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.revokeClientCertificate(zoneId, certificate.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(revokingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(revokingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
