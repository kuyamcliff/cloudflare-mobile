package dev.cfmobile.app.ui.rules

import dev.cfmobile.app.data.remote.dto.RedirectFromValue
import dev.cfmobile.app.data.remote.dto.RedirectTargetUrl
import dev.cfmobile.app.data.remote.dto.RuleActionParameters
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.repository.RulesetPhaseRepository

/** Single Redirects live in the dynamic-redirect phase; Bulk Redirects are an account-level
 *  list product and are not this screen. */
const val REDIRECT_PHASE = "http_request_dynamic_redirect"
const val REDIRECT_ACTION = "redirect"

/** The status codes Cloudflare accepts on a dynamic redirect. */
val REDIRECT_STATUS_CODES = listOf(301, 302, 303, 307, 308)

data class RedirectRuleForm(
    override val editingId: String? = null,
    val expression: String = "true",
    val description: String = "",
    val enabled: Boolean = true,
    val target: String = "",
    /** A static URL when false; a Cloudflare expression (e.g. concat(...)) when true. */
    val targetIsExpression: Boolean = false,
    val statusCode: Int = 301,
    val preserveQueryString: Boolean = true,
    override val isSaving: Boolean = false,
    override val error: String? = null
) : PhaseRuleForm<RedirectRuleForm> {
    override fun withStatus(isSaving: Boolean, error: String?) = copy(isSaving = isSaving, error = error)
}

fun validateRedirectForm(form: RedirectRuleForm): String? = when {
    form.expression.isBlank() -> "Expression is required"
    form.target.isBlank() -> "A target URL or expression is required"
    !form.targetIsExpression && !form.target.startsWith("http://") && !form.target.startsWith("https://") ->
        "A static target must be a full URL, starting with https://"
    form.statusCode !in REDIRECT_STATUS_CODES -> "Choose one of Cloudflare's redirect status codes"
    else -> null
}

fun buildRedirectRuleWrite(form: RedirectRuleForm): RulesetRuleWrite = RulesetRuleWrite(
    action = REDIRECT_ACTION,
    expression = form.expression.trim(),
    description = form.description.trim().ifBlank { null },
    enabled = form.enabled,
    actionParameters = RuleActionParameters(
        fromValue = RedirectFromValue(
            statusCode = form.statusCode,
            targetUrl = if (form.targetIsExpression) {
                RedirectTargetUrl(expression = form.target.trim())
            } else {
                RedirectTargetUrl(value = form.target.trim())
            },
            preserveQueryString = form.preserveQueryString
        )
    )
)

/** Reads a stored rule back into the form, so editing starts from what Cloudflare has rather
 *  than from blanks. */
fun redirectFormOf(rule: RulesetRule): RedirectRuleForm {
    val from = rule.actionParameters?.fromValue
    val expression = from?.targetUrl?.expression
    return RedirectRuleForm(
        editingId = rule.id,
        expression = rule.expression,
        description = rule.description.orEmpty(),
        enabled = rule.enabled,
        target = expression ?: from?.targetUrl?.value.orEmpty(),
        targetIsExpression = expression != null,
        statusCode = from?.statusCode ?: 301,
        preserveQueryString = from?.preserveQueryString ?: true
    )
}

/** "301 → https://example.com", the one line worth showing in a list row. */
fun redirectSummary(rule: RulesetRule): String {
    val from = rule.actionParameters?.fromValue ?: return rule.expression
    val target = from.targetUrl.value ?: from.targetUrl.expression ?: "(no target)"
    return "${from.statusCode} → $target"
}

class RedirectRulesViewModel(
    zoneId: String,
    repository: RulesetPhaseRepository
) : PhaseRulesViewModel<RedirectRuleForm>(zoneId, REDIRECT_PHASE, repository) {

    override fun validate(form: RedirectRuleForm) = validateRedirectForm(form)

    override fun buildWrite(form: RedirectRuleForm) = buildRedirectRuleWrite(form)

    fun openCreateForm() = showForm(RedirectRuleForm())

    fun openEditForm(rule: RulesetRule) = showForm(redirectFormOf(rule))
}
