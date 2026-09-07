package dev.cfmobile.app.ui.zerotrust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AccessBookmark
import dev.cfmobile.app.data.remote.dto.AccessEmailDomainRule
import dev.cfmobile.app.data.remote.dto.AccessEmailRule
import dev.cfmobile.app.data.remote.dto.AccessGroup
import dev.cfmobile.app.data.remote.dto.AccessMtlsCertificate
import dev.cfmobile.app.data.remote.dto.AccessPolicyIncludeRule
import dev.cfmobile.app.data.remote.dto.AccessTag
import dev.cfmobile.app.data.repository.AccessRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The four things Access keeps beside its applications. */
enum class AccessDirectoryTab(val label: String) {
    GROUPS("Groups"),
    BOOKMARKS("Bookmarks"),
    TAGS("Tags"),
    CERTIFICATES("mTLS")
}

/**
 * A group is a named set of include rules. The form takes the same two rule kinds the
 * application policy form supports - an email domain, or specific addresses - one per line.
 */
data class AccessGroupFormState(
    val name: String = "",
    val emailDomain: String = "",
    val emails: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class AccessBookmarkFormState(
    val name: String = "",
    val domain: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class AccessTagFormState(
    val name: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class AccessDirectoryUiState(
    val tab: AccessDirectoryTab = AccessDirectoryTab.GROUPS,
    val groups: UiState<List<AccessGroup>> = UiState.Loading,
    val bookmarks: UiState<List<AccessBookmark>> = UiState.Loading,
    val tags: UiState<List<AccessTag>> = UiState.Loading,
    val certificates: UiState<List<AccessMtlsCertificate>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val groupForm: AccessGroupFormState? = null,
    val bookmarkForm: AccessBookmarkFormState? = null,
    val tagForm: AccessTagFormState? = null,
    val deletingId: String? = null,
    val error: String? = null
)

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val DOMAIN_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

fun splitLines(raw: String): List<String> =
    raw.split('\n', ',').map { it.trim() }.filter { it.isNotBlank() }.distinct()

fun validateAccessGroupForm(form: AccessGroupFormState): String? {
    if (form.name.isBlank()) return "Group name is required"
    val emails = splitLines(form.emails)
    if (form.emailDomain.isBlank() && emails.isEmpty()) {
        return "Add an email domain or at least one address - a group with no rules matches nobody"
    }
    if (form.emailDomain.isNotBlank() && !form.emailDomain.trim().matches(DOMAIN_REGEX)) {
        return "Enter a domain, e.g. example.com"
    }
    emails.firstOrNull { !it.matches(EMAIL_REGEX) }?.let { return "\"$it\" isn't a valid email address" }
    return null
}

fun buildGroupIncludes(form: AccessGroupFormState): List<AccessPolicyIncludeRule> = buildList {
    form.emailDomain.trim().takeIf { it.isNotBlank() }?.let {
        add(AccessPolicyIncludeRule(emailDomain = AccessEmailDomainRule(domain = it)))
    }
    splitLines(form.emails).forEach { add(AccessPolicyIncludeRule(email = AccessEmailRule(email = it))) }
}

/** "@example.com, plus 2 addresses" - what the group actually admits. */
fun groupSummary(group: AccessGroup): String {
    val domains = group.include.mapNotNull { it.emailDomain?.domain }
    val emails = group.include.count { it.email != null }
    // An include rule this app doesn't model still counts, so the total isn't understated.
    val others = group.include.size - domains.size - emails
    return listOfNotNull(
        domains.joinToString(", ") { "@$it" }.takeIf { it.isNotBlank() },
        emails.takeIf { it > 0 }?.let { "$it address${if (it == 1) "" else "es"}" },
        others.takeIf { it > 0 }?.let { "$it other rule${if (it == 1) "" else "s"}" }
    ).joinToString(" · ").ifBlank { "No include rules - this group matches nobody" }
}

fun validateBookmarkForm(form: AccessBookmarkFormState): String? = when {
    form.name.isBlank() -> "Name is required"
    form.domain.isBlank() -> "Domain is required"
    // Cloudflare wants a bare hostname here, not a URL.
    form.domain.contains("://") -> "Leave off the scheme - just the hostname"
    !form.domain.trim().matches(DOMAIN_REGEX) -> "Enter a hostname, e.g. wiki.example.com"
    else -> null
}

fun certificateSummary(certificate: AccessMtlsCertificate): String = listOfNotNull(
    certificate.associatedHostnames?.size?.takeIf { it > 0 }?.let { "$it hostname${if (it == 1) "" else "s"}" },
    certificate.expiresOn?.let { "expires $it" }
).joinToString(" · ").ifBlank { "Not associated with any hostname" }

class AccessDirectoryViewModel(
    private val accountId: String,
    private val repository: AccessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessDirectoryUiState())
    val uiState: StateFlow<AccessDirectoryUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun selectTab(tab: AccessDirectoryTab) = _uiState.update { it.copy(tab = tab) }

    /** Four lists behind four tabs, loaded sequentially in one coroutine so the shared refresh
     *  has one clear finish. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(
                groups = UiState.Loading,
                bookmarks = UiState.Loading,
                tags = UiState.Loading,
                certificates = UiState.Loading
            )
        }
        viewModelScope.launch {
            when (val groups = repository.listGroups(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(groups = UiState.Data(groups.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(groups = UiState.Error(ErrorClassifier.classify(groups)))
                }
            }
            when (val bookmarks = repository.listBookmarks(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(bookmarks = UiState.Data(bookmarks.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(bookmarks = UiState.Error(ErrorClassifier.classify(bookmarks)))
                }
            }
            when (val tags = repository.listTags(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(tags = UiState.Data(tags.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(tags = UiState.Error(ErrorClassifier.classify(tags)))
                }
            }
            when (val certificates = repository.listMtlsCertificates(accountId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(certificates = UiState.Data(certificates.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(certificates = UiState.Error(ErrorClassifier.classify(certificates)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { state ->
        when (state.tab) {
            AccessDirectoryTab.GROUPS -> state.copy(groupForm = AccessGroupFormState())
            AccessDirectoryTab.BOOKMARKS -> state.copy(bookmarkForm = AccessBookmarkFormState())
            AccessDirectoryTab.TAGS -> state.copy(tagForm = AccessTagFormState())
            // Uploading a root certificate means handling a certificate chain, which this app
            // doesn't do, so the tab has no create action.
            AccessDirectoryTab.CERTIFICATES -> state
        }
    }

    fun closeForms() = _uiState.update { it.copy(groupForm = null, bookmarkForm = null, tagForm = null) }

    fun updateGroupForm(transform: (AccessGroupFormState) -> AccessGroupFormState) =
        _uiState.update { state -> state.groupForm?.let { state.copy(groupForm = transform(it)) } ?: state }

    fun updateBookmarkForm(transform: (AccessBookmarkFormState) -> AccessBookmarkFormState) =
        _uiState.update { state -> state.bookmarkForm?.let { state.copy(bookmarkForm = transform(it)) } ?: state }

    fun updateTagForm(transform: (AccessTagFormState) -> AccessTagFormState) =
        _uiState.update { state -> state.tagForm?.let { state.copy(tagForm = transform(it)) } ?: state }

    fun saveGroup() {
        val form = _uiState.value.groupForm ?: return
        val validationError = validateAccessGroupForm(form)
        if (validationError != null) {
            updateGroupForm { it.copy(error = validationError) }
            return
        }
        updateGroupForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createGroup(accountId, form.name.trim(), buildGroupIncludes(form))) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(groupForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateGroupForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun saveBookmark() {
        val form = _uiState.value.bookmarkForm ?: return
        val validationError = validateBookmarkForm(form)
        if (validationError != null) {
            updateBookmarkForm { it.copy(error = validationError) }
            return
        }
        updateBookmarkForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createBookmark(accountId, form.name.trim(), form.domain.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(bookmarkForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateBookmarkForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun saveTag() {
        val form = _uiState.value.tagForm ?: return
        if (form.name.isBlank()) {
            updateTagForm { it.copy(error = "Tag name is required") }
            return
        }
        updateTagForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createTag(accountId, form.name.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(tagForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateTagForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteGroup(group: AccessGroup) = delete(group.id) { repository.deleteGroup(accountId, group.id) }

    fun deleteBookmark(bookmark: AccessBookmark) =
        delete(bookmark.id) { repository.deleteBookmark(accountId, bookmark.id) }

    fun deleteTag(tag: AccessTag) = delete(tag.name) { repository.deleteTag(accountId, tag.name) }

    fun deleteCertificate(certificate: AccessMtlsCertificate) =
        delete(certificate.id) { repository.deleteMtlsCertificate(accountId, certificate.id) }

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
