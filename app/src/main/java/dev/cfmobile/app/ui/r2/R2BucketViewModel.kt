package dev.cfmobile.app.ui.r2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.R2CorsRule
import dev.cfmobile.app.data.remote.dto.R2CustomDomain
import dev.cfmobile.app.data.remote.dto.R2LifecycleRule
import dev.cfmobile.app.data.remote.dto.R2ManagedDomain
import dev.cfmobile.app.data.repository.R2Repository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class R2BucketUiState(
    val customDomains: UiState<List<R2CustomDomain>> = UiState.Loading,
    val managedDomain: R2ManagedDomain? = null,
    val corsRules: List<R2CorsRule> = emptyList(),
    val lifecycleRules: List<R2LifecycleRule> = emptyList(),
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val deletingDomain: String? = null,
    val error: String? = null
)

/** "https://cdn.example.com · SSL active", from whatever Cloudflare reported. */
fun customDomainStatus(domain: R2CustomDomain): String = listOfNotNull(
    if (domain.enabled) "Enabled" else "Disabled",
    domain.status?.ownership?.let { "ownership $it" },
    domain.status?.ssl?.let { "SSL $it" }
).joinToString(" · ")

/** A CORS rule as one line: which origins may call the bucket, with which methods. */
fun corsSummary(rule: R2CorsRule): String {
    val origins = rule.allowed.origins.joinToString(", ").ifBlank { "no origins" }
    val methods = rule.allowed.methods.joinToString(", ").ifBlank { "no methods" }
    return "$origins → $methods"
}

/** "Delete after 30 days", or the prefix it applies to. */
fun lifecycleSummary(rule: R2LifecycleRule): String {
    val prefix = rule.conditions?.prefix?.takeIf { it.isNotBlank() }?.let { "prefix $it" }
    val age = rule.deleteTransition?.condition?.maxAge?.let { seconds ->
        val days = seconds / 86_400
        if (days > 0) "delete after $days day${if (days == 1) "" else "s"}" else "delete after ${seconds}s"
    }
    val date = rule.deleteTransition?.condition?.date?.let { "delete on $it" }
    return listOfNotNull(prefix, age ?: date, if (rule.enabled) null else "disabled")
        .joinToString(" · ")
        .ifBlank { rule.id }
}

class R2BucketViewModel(
    private val accountId: String,
    private val bucketName: String,
    private val repository: R2Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(R2BucketUiState())
    val uiState: StateFlow<R2BucketUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /**
     * Four endpoints, loaded sequentially in one coroutine. CORS and lifecycle answer 404 on a
     * bucket that has never had one, which the repository already maps to an empty list, so
     * only a real failure ends up as an error here.
     */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true) else it.copy(customDomains = UiState.Loading)
        }
        viewModelScope.launch {
            when (val domains = repository.listCustomDomains(accountId, bucketName)) {
                is ApiResult.Success -> _uiState.update { it.copy(customDomains = UiState.Data(domains.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(customDomains = UiState.Error(ErrorClassifier.classify(domains)))
                }
            }
            when (val managed = repository.getManagedDomain(accountId, bucketName)) {
                is ApiResult.Success -> _uiState.update { it.copy(managedDomain = managed.data) }
                is ApiResult.Failure -> Unit
            }
            when (val cors = repository.getCors(accountId, bucketName)) {
                is ApiResult.Success -> _uiState.update { it.copy(corsRules = cors.data) }
                is ApiResult.Failure -> Unit
            }
            when (val lifecycle = repository.getLifecycle(accountId, bucketName)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(lifecycleRules = lifecycle.data, isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Turning the r2.dev URL on publishes every object in the bucket, so the screen confirms
     *  before calling this. */
    fun setManagedDomain(enabled: Boolean) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.setManagedDomain(accountId, bucketName, enabled)) {
                is ApiResult.Success -> _uiState.update { it.copy(managedDomain = result.data, isSaving = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteCustomDomain(domain: R2CustomDomain) {
        _uiState.update { it.copy(deletingDomain = domain.domain, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteCustomDomain(accountId, bucketName, domain.domain)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingDomain = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingDomain = null, error = result.message) }
            }
        }
    }

    /** Clearing CORS removes the whole policy - Cloudflare has no per-rule delete here. */
    fun clearCors() {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.clearCors(accountId, bucketName)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
