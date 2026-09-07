package dev.cfmobile.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AiGateway
import dev.cfmobile.app.data.remote.dto.AiGatewayWrite
import dev.cfmobile.app.data.remote.dto.CallsApp
import dev.cfmobile.app.data.remote.dto.Pipeline
import dev.cfmobile.app.data.remote.dto.SecretStore
import dev.cfmobile.app.data.repository.AiGatewayRepository
import dev.cfmobile.app.data.repository.CallsRepository
import dev.cfmobile.app.data.repository.PipelinesRepository
import dev.cfmobile.app.data.repository.SecretsStoreRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlatformTab(val label: String) {
    AI_GATEWAY("AI Gateway"),
    CALLS("Calls"),
    PIPELINES("Pipelines"),
    SECRETS("Secrets Store")
}

data class AiGatewayFormState(
    val id: String = "",
    val cacheTtl: String = "0",
    val collectLogs: Boolean = true,
    val rateLimitLimit: String = "0",
    val rateLimitInterval: String = "0",
    val isSaving: Boolean = false,
    val error: String? = null
)

/** A name-only form, shared by Calls apps and secret stores. */
data class PlatformNameFormState(
    val name: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

/**
 * A Calls app's secret, shown exactly once. Like an Access service token, Cloudflare returns
 * it only in the create response; it is held for this dialog and never persisted.
 */
data class NewCallsApp(
    val name: String,
    val appId: String,
    val secret: String
)

data class PlatformUiState(
    val tab: PlatformTab = PlatformTab.AI_GATEWAY,
    val gateways: UiState<List<AiGateway>> = UiState.Loading,
    val callsApps: UiState<List<CallsApp>> = UiState.Loading,
    val pipelines: UiState<List<Pipeline>> = UiState.Loading,
    val stores: UiState<List<SecretStore>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val gatewayForm: AiGatewayFormState? = null,
    val nameForm: PlatformNameFormState? = null,
    val newCallsApp: NewCallsApp? = null,
    val deletingId: String? = null,
    val error: String? = null
)

/** Cloudflare's gateway ids are URL path segments, so they're restricted like a slug. */
private val GATEWAY_ID_REGEX = Regex("^[a-z0-9][a-z0-9-]*$")

fun validateGatewayForm(form: AiGatewayFormState): String? {
    if (form.id.isBlank()) return "Gateway name is required"
    if (!form.id.trim().matches(GATEWAY_ID_REGEX)) {
        return "Use lowercase letters, digits, and hyphens - the name becomes part of the gateway's URL"
    }
    val ttl = form.cacheTtl.trim().toIntOrNull()
    if (ttl == null || ttl < 0) return "Cache TTL must be zero or more seconds"
    val limit = form.rateLimitLimit.trim().toIntOrNull()
    if (limit == null || limit < 0) return "Rate limit must be zero or more requests"
    val interval = form.rateLimitInterval.trim().toIntOrNull()
    if (interval == null || interval < 0) return "Rate limit interval must be zero or more seconds"
    // A limit without a window would never reset, so Cloudflare rejects it.
    if (limit > 0 && interval == 0) return "A rate limit needs an interval to reset over"
    return null
}

fun buildGatewayWrite(form: AiGatewayFormState): AiGatewayWrite = AiGatewayWrite(
    id = form.id.trim(),
    cacheTtl = form.cacheTtl.trim().toInt(),
    collectLogs = form.collectLogs,
    rateLimitingLimit = form.rateLimitLimit.trim().toInt(),
    rateLimitingInterval = form.rateLimitInterval.trim().toInt()
)

/** "caches 60s · 100 req/60s · logging on" - what the gateway actually does. */
fun gatewaySummary(gateway: AiGateway): String = listOfNotNull(
    gateway.cacheTtl?.takeIf { it > 0 }?.let { "caches ${it}s" } ?: "no caching",
    gateway.rateLimitingLimit?.takeIf { it > 0 }?.let { limit ->
        "$limit req/${gateway.rateLimitingInterval ?: 0}s"
    },
    if (gateway.collectLogs == false) "logging off" else "logging on"
).joinToString(" · ")

class PlatformViewModel(
    private val accountId: String,
    private val aiGatewayRepository: AiGatewayRepository,
    private val callsRepository: CallsRepository,
    private val pipelinesRepository: PipelinesRepository,
    private val secretsStoreRepository: SecretsStoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlatformUiState())
    val uiState: StateFlow<PlatformUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun selectTab(tab: PlatformTab) = _uiState.update { it.copy(tab = tab) }

    /** Four products behind four tabs, loaded sequentially in one coroutine. Each is a
     *  separate beta-ish API, so one failing leaves the others usable. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(
                gateways = UiState.Loading,
                callsApps = UiState.Loading,
                pipelines = UiState.Loading,
                stores = UiState.Loading
            )
        }
        viewModelScope.launch {
            when (val gateways = aiGatewayRepository.listGateways(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(gateways = UiState.Data(gateways.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(gateways = UiState.Error(ErrorClassifier.classify(gateways)))
                }
            }
            when (val apps = callsRepository.listApps(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(callsApps = UiState.Data(apps.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(callsApps = UiState.Error(ErrorClassifier.classify(apps)))
                }
            }
            when (val pipelines = pipelinesRepository.listPipelines(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(pipelines = UiState.Data(pipelines.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(pipelines = UiState.Error(ErrorClassifier.classify(pipelines)))
                }
            }
            when (val stores = secretsStoreRepository.listStores(accountId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(stores = UiState.Data(stores.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(stores = UiState.Error(ErrorClassifier.classify(stores)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { state ->
        when (state.tab) {
            PlatformTab.AI_GATEWAY -> state.copy(gatewayForm = AiGatewayFormState())
            PlatformTab.CALLS, PlatformTab.SECRETS -> state.copy(nameForm = PlatformNameFormState())
            // Creating a pipeline needs a source, a destination bucket, and its credentials.
            PlatformTab.PIPELINES -> state
        }
    }

    fun closeForms() = _uiState.update { it.copy(gatewayForm = null, nameForm = null) }

    fun dismissNewCallsApp() = _uiState.update { it.copy(newCallsApp = null) }

    fun updateGatewayForm(transform: (AiGatewayFormState) -> AiGatewayFormState) =
        _uiState.update { state -> state.gatewayForm?.let { state.copy(gatewayForm = transform(it)) } ?: state }

    fun updateNameForm(transform: (PlatformNameFormState) -> PlatformNameFormState) =
        _uiState.update { state -> state.nameForm?.let { state.copy(nameForm = transform(it)) } ?: state }

    fun saveGateway() {
        val form = _uiState.value.gatewayForm ?: return
        val validationError = validateGatewayForm(form)
        if (validationError != null) {
            updateGatewayForm { it.copy(error = validationError) }
            return
        }
        updateGatewayForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = aiGatewayRepository.createGateway(accountId, buildGatewayWrite(form))) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(gatewayForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateGatewayForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun saveName() {
        val form = _uiState.value.nameForm ?: return
        val tab = _uiState.value.tab
        if (form.name.isBlank()) {
            updateNameForm { it.copy(error = "Name is required") }
            return
        }
        updateNameForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (tab) {
                PlatformTab.CALLS -> when (val result = callsRepository.createApp(accountId, form.name.trim())) {
                    is ApiResult.Success -> {
                        val app = result.data
                        _uiState.update {
                            it.copy(
                                nameForm = null,
                                // Only surfaced when Cloudflare actually returned a secret -
                                // this is the only chance to copy it.
                                newCallsApp = app.secret?.let { secret ->
                                    NewCallsApp(name = app.name ?: form.name.trim(), appId = app.uid, secret = secret)
                                }
                            )
                        }
                        load(isRefresh = true)
                    }
                    is ApiResult.Failure -> updateNameForm { it.copy(isSaving = false, error = result.message) }
                }
                PlatformTab.SECRETS -> when (val result = secretsStoreRepository.createStore(accountId, form.name.trim())) {
                    is ApiResult.Success -> {
                        _uiState.update { it.copy(nameForm = null) }
                        load(isRefresh = true)
                    }
                    is ApiResult.Failure -> updateNameForm { it.copy(isSaving = false, error = result.message) }
                }
                else -> _uiState.update { it.copy(nameForm = null) }
            }
        }
    }

    fun deleteGateway(gateway: AiGateway) =
        delete(gateway.id) { aiGatewayRepository.deleteGateway(accountId, gateway.id) }

    fun deleteCallsApp(app: CallsApp) = delete(app.uid) { callsRepository.deleteApp(accountId, app.uid) }

    fun deletePipeline(pipeline: Pipeline) =
        delete(pipeline.id) { pipelinesRepository.deletePipeline(accountId, pipeline.name) }

    fun deleteStore(store: SecretStore) =
        delete(store.id) { secretsStoreRepository.deleteStore(accountId, store.id) }

    private fun delete(id: String, request: suspend () -> ApiResult<Unit>) {
        _uiState.update { it.copy(deletingId = id, error = null) }
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
