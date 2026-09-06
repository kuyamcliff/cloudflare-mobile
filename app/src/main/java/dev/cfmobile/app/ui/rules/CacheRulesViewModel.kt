package dev.cfmobile.app.ui.rules

import dev.cfmobile.app.data.remote.dto.RuleActionParameters
import dev.cfmobile.app.data.remote.dto.RuleTtl
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.repository.RulesetPhaseRepository

const val CACHE_PHASE = "http_request_cache_settings"
const val CACHE_ACTION = "set_cache_settings"

const val TTL_RESPECT_ORIGIN = "respect_origin"
const val TTL_OVERRIDE_ORIGIN = "override_origin"
const val TTL_BYPASS = "bypass_by_default"

/** Cloudflare's TTL modes, with labels that say what each actually does. */
val TTL_MODES = listOf(
    TTL_RESPECT_ORIGIN to "Respect origin headers",
    TTL_OVERRIDE_ORIGIN to "Override origin",
    TTL_BYPASS to "Bypass cache by default"
)

data class CacheRuleForm(
    override val editingId: String? = null,
    val expression: String = "true",
    val description: String = "",
    val enabled: Boolean = true,
    val cache: Boolean = true,
    val edgeTtlMode: String = TTL_RESPECT_ORIGIN,
    val edgeTtlSeconds: String = "",
    val browserTtlMode: String = TTL_RESPECT_ORIGIN,
    val browserTtlSeconds: String = "",
    override val isSaving: Boolean = false,
    override val error: String? = null
) : PhaseRuleForm<CacheRuleForm> {
    override fun withStatus(isSaving: Boolean, error: String?) = copy(isSaving = isSaving, error = error)
}

private fun ttlSecondsError(mode: String, seconds: String, label: String): String? {
    if (mode != TTL_OVERRIDE_ORIGIN) return null
    val value = seconds.trim().toIntOrNull()
    return if (value == null || value < 0) "$label TTL needs a number of seconds to override with" else null
}

fun validateCacheForm(form: CacheRuleForm): String? = when {
    form.expression.isBlank() -> "Expression is required"
    // TTLs only mean anything for content Cloudflare is caching.
    !form.cache -> null
    else -> ttlSecondsError(form.edgeTtlMode, form.edgeTtlSeconds, "Edge")
        ?: ttlSecondsError(form.browserTtlMode, form.browserTtlSeconds, "Browser")
}

private fun ttlOf(mode: String, seconds: String): RuleTtl =
    RuleTtl(mode = mode, default = seconds.trim().toIntOrNull().takeIf { mode == TTL_OVERRIDE_ORIGIN })

fun buildCacheRuleWrite(form: CacheRuleForm): RulesetRuleWrite = RulesetRuleWrite(
    action = CACHE_ACTION,
    expression = form.expression.trim(),
    description = form.description.trim().ifBlank { null },
    enabled = form.enabled,
    actionParameters = RuleActionParameters(
        cache = form.cache,
        // A rule that bypasses the cache has no TTLs to set.
        edgeTtl = if (form.cache) ttlOf(form.edgeTtlMode, form.edgeTtlSeconds) else null,
        browserTtl = if (form.cache) ttlOf(form.browserTtlMode, form.browserTtlSeconds) else null
    )
)

fun cacheFormOf(rule: RulesetRule): CacheRuleForm {
    val parameters = rule.actionParameters
    return CacheRuleForm(
        editingId = rule.id,
        expression = rule.expression,
        description = rule.description.orEmpty(),
        enabled = rule.enabled,
        cache = parameters?.cache ?: true,
        edgeTtlMode = parameters?.edgeTtl?.mode ?: TTL_RESPECT_ORIGIN,
        edgeTtlSeconds = parameters?.edgeTtl?.default?.toString().orEmpty(),
        browserTtlMode = parameters?.browserTtl?.mode ?: TTL_RESPECT_ORIGIN,
        browserTtlSeconds = parameters?.browserTtl?.default?.toString().orEmpty()
    )
}

fun cacheSummary(rule: RulesetRule): String {
    val parameters = rule.actionParameters
    if (parameters?.cache == false) return "Bypass cache"
    val edge = parameters?.edgeTtl
    return when (edge?.mode) {
        TTL_OVERRIDE_ORIGIN -> "Cache · edge TTL ${edge.default ?: 0}s"
        TTL_BYPASS -> "Cache · edge bypass by default"
        else -> "Cache · respect origin TTL"
    }
}

class CacheRulesViewModel(
    zoneId: String,
    repository: RulesetPhaseRepository
) : PhaseRulesViewModel<CacheRuleForm>(zoneId, CACHE_PHASE, repository) {

    override fun validate(form: CacheRuleForm) = validateCacheForm(form)

    override fun buildWrite(form: CacheRuleForm) = buildCacheRuleWrite(form)

    fun openCreateForm() = showForm(CacheRuleForm())

    fun openEditForm(rule: RulesetRule) = showForm(cacheFormOf(rule))
}
