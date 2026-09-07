package dev.cfmobile.app.ui.rules

import dev.cfmobile.app.data.remote.dto.RuleActionParameters
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.repository.RulesetPhaseRepository

const val CONFIG_PHASE = "http_config_settings"
const val CONFIG_ACTION = "set_config"

/**
 * The zone settings a Config Rule can override per request. Each is a zone-wide setting
 * elsewhere in the app; a Config Rule turns one of them on or off for matching traffic only.
 *
 * [read] and [write] pair each setting with its own field in action_parameters, because
 * Cloudflare writes them as direct keys rather than a map.
 */
enum class ConfigSetting(
    val label: String,
    val subtitle: String,
    val read: (RuleActionParameters) -> Boolean?,
    val write: (RuleActionParameters, Boolean) -> RuleActionParameters
) {
    EMAIL_OBFUSCATION(
        "Email obfuscation",
        "Hide email addresses in the HTML from scrapers",
        { it.emailObfuscation },
        { p, v -> p.copy(emailObfuscation = v) }
    ),
    HOTLINK_PROTECTION(
        "Hotlink protection",
        "Block other sites embedding this zone's images",
        { it.hotlinkProtection },
        { p, v -> p.copy(hotlinkProtection = v) }
    ),
    MIRAGE(
        "Mirage",
        "Optimise images for slow mobile connections",
        { it.mirage },
        { p, v -> p.copy(mirage = v) }
    ),
    ROCKET_LOADER(
        "Rocket Loader",
        "Defer JavaScript so pages paint sooner",
        { it.rocketLoader },
        { p, v -> p.copy(rocketLoader = v) }
    ),
    AUTOMATIC_HTTPS_REWRITES(
        "Automatic HTTPS rewrites",
        "Rewrite http:// links in the HTML to https://",
        { it.automaticHttpsRewrites },
        { p, v -> p.copy(automaticHttpsRewrites = v) }
    ),
    BROWSER_INTEGRITY_CHECK(
        "Browser Integrity Check",
        "Block requests with suspicious headers",
        { it.bic },
        { p, v -> p.copy(bic = v) }
    ),
    DISABLE_APPS(
        "Disable Apps",
        "Skip Cloudflare Apps on matching requests",
        { it.disableApps },
        { p, v -> p.copy(disableApps = v) }
    ),
    DISABLE_ZARAZ(
        "Disable Zaraz",
        "Skip Zaraz on matching requests",
        { it.disableZaraz },
        { p, v -> p.copy(disableZaraz = v) }
    ),
    DISABLE_RUM(
        "Disable Web Analytics",
        "Skip the RUM beacon on matching requests",
        { it.disableRum },
        { p, v -> p.copy(disableRum = v) }
    )
}

data class ConfigRuleForm(
    override val editingId: String? = null,
    val expression: String = "true",
    val description: String = "",
    val enabled: Boolean = true,
    val setting: ConfigSetting = ConfigSetting.EMAIL_OBFUSCATION,
    val settingEnabled: Boolean = true,
    override val isSaving: Boolean = false,
    override val error: String? = null
) : PhaseRuleForm<ConfigRuleForm> {
    override fun withStatus(isSaving: Boolean, error: String?) = copy(isSaving = isSaving, error = error)
}

fun validateConfigForm(form: ConfigRuleForm): String? =
    if (form.expression.isBlank()) "Expression is required" else null

/** One rule sets one setting here. Cloudflare allows several in a single rule, but a form
 *  that edits one at a time is the honest fit for a phone - see the migrationHint. */
fun buildConfigRuleWrite(form: ConfigRuleForm): RulesetRuleWrite = RulesetRuleWrite(
    action = CONFIG_ACTION,
    expression = form.expression.trim(),
    description = form.description.trim().ifBlank { null },
    enabled = form.enabled,
    actionParameters = form.setting.write(RuleActionParameters(), form.settingEnabled)
)

/** Finds whichever setting the stored rule populated. */
fun configSettingOf(parameters: RuleActionParameters?): Pair<ConfigSetting, Boolean>? {
    if (parameters == null) return null
    return ConfigSetting.entries.firstNotNullOfOrNull { setting ->
        setting.read(parameters)?.let { setting to it }
    }
}

fun configFormOf(rule: RulesetRule): ConfigRuleForm {
    val found = configSettingOf(rule.actionParameters)
    return ConfigRuleForm(
        editingId = rule.id,
        expression = rule.expression,
        description = rule.description.orEmpty(),
        enabled = rule.enabled,
        setting = found?.first ?: ConfigSetting.EMAIL_OBFUSCATION,
        settingEnabled = found?.second ?: true
    )
}

/** "Rocket Loader off", the one line worth showing on the row. */
fun configSummary(rule: RulesetRule): String {
    val (setting, enabled) = configSettingOf(rule.actionParameters) ?: return rule.expression
    return "${setting.label} ${if (enabled) "on" else "off"}"
}

class ConfigRulesViewModel(
    zoneId: String,
    repository: RulesetPhaseRepository
) : PhaseRulesViewModel<ConfigRuleForm>(zoneId, CONFIG_PHASE, repository) {

    override fun validate(form: ConfigRuleForm) = validateConfigForm(form)

    override fun buildWrite(form: ConfigRuleForm) = buildConfigRuleWrite(form)

    fun openCreateForm() = showForm(ConfigRuleForm())

    fun openEditForm(rule: RulesetRule) = showForm(configFormOf(rule))
}
