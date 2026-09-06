package dev.cfmobile.app.ui.rules

import dev.cfmobile.app.data.remote.dto.RuleActionParameters
import dev.cfmobile.app.data.remote.dto.RuleOrigin
import dev.cfmobile.app.data.remote.dto.RuleSni
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.repository.RulesetPhaseRepository

const val ORIGIN_PHASE = "http_request_origin"
const val ORIGIN_ACTION = "route"

data class OriginRuleForm(
    override val editingId: String? = null,
    val expression: String = "true",
    val description: String = "",
    val enabled: Boolean = true,
    val originHost: String = "",
    val originPort: String = "",
    val hostHeader: String = "",
    val sni: String = "",
    override val isSaving: Boolean = false,
    override val error: String? = null
) : PhaseRuleForm<OriginRuleForm> {
    override fun withStatus(isSaving: Boolean, error: String?) = copy(isSaving = isSaving, error = error)
}

fun validateOriginForm(form: OriginRuleForm): String? {
    if (form.expression.isBlank()) return "Expression is required"
    val port = form.originPort.trim()
    if (port.isNotBlank() && (port.toIntOrNull() == null || port.toInt() !in 1..65535)) {
        return "Port must be a number between 1 and 65535"
    }
    // An origin rule that overrides nothing is a no-op that still costs a rule slot.
    if (form.originHost.isBlank() && port.isBlank() && form.hostHeader.isBlank() && form.sni.isBlank()) {
        return "Set at least one override: origin host, port, Host header, or SNI"
    }
    return null
}

fun buildOriginRuleWrite(form: OriginRuleForm): RulesetRuleWrite {
    val host = form.originHost.trim().takeIf { it.isNotBlank() }
    val port = form.originPort.trim().toIntOrNull()
    return RulesetRuleWrite(
        action = ORIGIN_ACTION,
        expression = form.expression.trim(),
        description = form.description.trim().ifBlank { null },
        enabled = form.enabled,
        actionParameters = RuleActionParameters(
            // Omit the origin block entirely when neither half is set, rather than sending an
            // empty object Cloudflare would reject.
            origin = if (host != null || port != null) RuleOrigin(host = host, port = port) else null,
            hostHeader = form.hostHeader.trim().takeIf { it.isNotBlank() },
            sni = form.sni.trim().takeIf { it.isNotBlank() }?.let { RuleSni(value = it) }
        )
    )
}

fun originFormOf(rule: RulesetRule): OriginRuleForm {
    val parameters = rule.actionParameters
    return OriginRuleForm(
        editingId = rule.id,
        expression = rule.expression,
        description = rule.description.orEmpty(),
        enabled = rule.enabled,
        originHost = parameters?.origin?.host.orEmpty(),
        originPort = parameters?.origin?.port?.toString().orEmpty(),
        hostHeader = parameters?.hostHeader.orEmpty(),
        sni = parameters?.sni?.value.orEmpty()
    )
}

/** "origin.example.com:8443 · Host: www.example.com", skipping whatever the rule leaves alone. */
fun originSummary(rule: RulesetRule): String {
    val parameters = rule.actionParameters
    val origin = listOfNotNull(parameters?.origin?.host, parameters?.origin?.port?.toString())
        .joinToString(":")
        .takeIf { it.isNotBlank() }
    return listOfNotNull(
        origin,
        parameters?.hostHeader?.let { "Host: $it" },
        parameters?.sni?.value?.let { "SNI: $it" }
    ).joinToString(" · ").ifBlank { rule.expression }
}

class OriginRulesViewModel(
    zoneId: String,
    repository: RulesetPhaseRepository
) : PhaseRulesViewModel<OriginRuleForm>(zoneId, ORIGIN_PHASE, repository) {

    override fun validate(form: OriginRuleForm) = validateOriginForm(form)

    override fun buildWrite(form: OriginRuleForm) = buildOriginRuleWrite(form)

    fun openCreateForm() = showForm(OriginRuleForm())

    fun openEditForm(rule: RulesetRule) = showForm(originFormOf(rule))
}
