package dev.cfmobile.app.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.PagesDeployment
import dev.cfmobile.app.data.remote.dto.PagesDomain
import dev.cfmobile.app.data.remote.dto.PagesProject
import dev.cfmobile.app.data.repository.PagesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagesUiState(
    val projects: UiState<List<PagesProject>> = UiState.Loading,
    val selectedProjectName: String? = null,
    val deployments: UiState<List<PagesDeployment>>? = null,
    /** Non-null while a deploy or retry is in flight, so the sheet can disable both buttons. */
    val deployingProject: String? = null,
    val deployError: String? = null,
    val deployMessage: String? = null,
    val domains: UiState<List<PagesDomain>>? = null,
    val domainForm: PagesDomainFormState? = null,
    val deletingDomain: String? = null
)

data class PagesDomainFormState(
    val name: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

private val DOMAIN_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

fun validatePagesDomain(name: String): String? = when {
    name.isBlank() -> "Domain is required"
    !name.trim().matches(DOMAIN_REGEX) -> "Enter a domain, e.g. www.example.com"
    else -> null
}

/** Cloudflare verifies a Pages domain asynchronously, so a fresh one reads as pending. */
fun pagesDomainStatus(domain: PagesDomain): String = listOfNotNull(
    domain.status?.replaceFirstChar { it.uppercase() },
    domain.verificationData?.errorMessage
).joinToString(" · ").ifBlank { "Pending" }

/** Projects, deployment history, and re-deploying. Editing a project's build configuration
 *  isn't implemented - see CapabilityRegistry's migrationHint. */
class PagesViewModel(
    private val accountId: String,
    private val repository: PagesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PagesUiState())
    val uiState: StateFlow<PagesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(projects = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listProjects(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(projects = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(projects = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun selectProject(project: PagesProject) {
        _uiState.update {
            it.copy(selectedProjectName = project.name, deployments = UiState.Loading, domains = UiState.Loading)
        }
        viewModelScope.launch {
            when (val result = repository.listDeployments(accountId, project.name)) {
                is ApiResult.Success -> _uiState.update { it.copy(deployments = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(deployments = UiState.Error(ErrorClassifier.classify(result))) }
            }
            loadDomains(project.name)
        }
    }

    private suspend fun loadDomains(projectName: String) {
        when (val result = repository.listDomains(accountId, projectName)) {
            is ApiResult.Success -> _uiState.update { state ->
                if (state.selectedProjectName != projectName) state else state.copy(domains = UiState.Data(result.data))
            }
            is ApiResult.Failure -> _uiState.update { state ->
                if (state.selectedProjectName != projectName) state
                else state.copy(domains = UiState.Error(ErrorClassifier.classify(result)))
            }
        }
    }

    fun openDomainForm() = _uiState.update { it.copy(domainForm = PagesDomainFormState()) }

    fun closeDomainForm() = _uiState.update { it.copy(domainForm = null) }

    fun updateDomainForm(transform: (PagesDomainFormState) -> PagesDomainFormState) =
        _uiState.update { state -> state.domainForm?.let { state.copy(domainForm = transform(it)) } ?: state }

    fun saveDomain() {
        val projectName = _uiState.value.selectedProjectName ?: return
        val form = _uiState.value.domainForm ?: return
        val validationError = validatePagesDomain(form.name)
        if (validationError != null) {
            updateDomainForm { it.copy(error = validationError) }
            return
        }
        updateDomainForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.addDomain(accountId, projectName, form.name.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(domainForm = null) }
                    loadDomains(projectName)
                }
                is ApiResult.Failure -> updateDomainForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteDomain(domain: PagesDomain) {
        val projectName = _uiState.value.selectedProjectName ?: return
        _uiState.update { it.copy(deletingDomain = domain.name) }
        viewModelScope.launch {
            repository.deleteDomain(accountId, projectName, domain.name)
            _uiState.update { it.copy(deletingDomain = null) }
            loadDomains(projectName)
        }
    }

    fun closeDeployments() = _uiState.update {
        it.copy(
            selectedProjectName = null,
            deployments = null,
            domains = null,
            domainForm = null,
            deployError = null,
            deployMessage = null
        )
    }

    /** Rebuilds the project's production branch from its connected source. Cloudflare queues
     *  the build, so the new deployment shows up in the refreshed history as "queued" rather
     *  than finished. */
    fun deploy() {
        val projectName = _uiState.value.selectedProjectName ?: return
        startDeploy(projectName) { repository.createDeployment(accountId, projectName) }
    }

    /** Re-runs one failed deployment rather than building the branch afresh. */
    fun retry(deployment: PagesDeployment) {
        val projectName = _uiState.value.selectedProjectName ?: return
        startDeploy(projectName) { repository.retryDeployment(accountId, projectName, deployment.id) }
    }

    private fun startDeploy(projectName: String, request: suspend () -> ApiResult<PagesDeployment>) {
        _uiState.update { it.copy(deployingProject = projectName, deployError = null, deployMessage = null) }
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(deployingProject = null, deployMessage = "Deployment queued", deployError = null)
                    }
                    reloadDeployments(projectName)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(deployingProject = null, deployError = result.message)
                }
            }
        }
    }

    private suspend fun reloadDeployments(projectName: String) {
        when (val result = repository.listDeployments(accountId, projectName)) {
            is ApiResult.Success -> _uiState.update {
                // Guard against the sheet having been closed or switched while the deploy ran.
                if (it.selectedProjectName != projectName) it else it.copy(deployments = UiState.Data(result.data))
            }
            is ApiResult.Failure -> _uiState.update {
                if (it.selectedProjectName != projectName) it
                else it.copy(deployments = UiState.Error(ErrorClassifier.classify(result)))
            }
        }
    }
}
