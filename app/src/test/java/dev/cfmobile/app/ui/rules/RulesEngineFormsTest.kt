package dev.cfmobile.app.ui.rules

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.dto.RedirectFromValue
import dev.cfmobile.app.data.remote.dto.RedirectTargetUrl
import dev.cfmobile.app.data.remote.dto.RuleActionParameters
import dev.cfmobile.app.data.remote.dto.RuleOrigin
import dev.cfmobile.app.data.remote.dto.RuleSni
import dev.cfmobile.app.data.remote.dto.RuleTtl
import dev.cfmobile.app.data.remote.dto.Ruleset
import dev.cfmobile.app.data.remote.dto.RulesetRule
import org.junit.Test

/** The pure half of the rules-engine screens: what each family sends to Cloudflare, what it
 *  reads back into its form, and what it refuses to send at all. */
class RulesEngineFormsTest {

    // ---- Redirect Rules ----

    @Test
    fun `a static redirect target is sent as a value, not an expression`() {
        val write = buildRedirectRuleWrite(
            RedirectRuleForm(expression = "http.request.uri.path eq \"/old\"", target = "https://example.com/new", statusCode = 308)
        )

        val from = write.actionParameters?.fromValue
        assertThat(write.action).isEqualTo("redirect")
        assertThat(from?.statusCode).isEqualTo(308)
        assertThat(from?.targetUrl?.value).isEqualTo("https://example.com/new")
        assertThat(from?.targetUrl?.expression).isNull()
    }

    @Test
    fun `an expression target is sent as an expression, not a value`() {
        val write = buildRedirectRuleWrite(
            RedirectRuleForm(target = "concat(\"https://x.com\", http.request.uri.path)", targetIsExpression = true)
        )

        val target = write.actionParameters?.fromValue?.targetUrl
        assertThat(target?.expression).isNotNull()
        assertThat(target?.value).isNull()
    }

    @Test
    fun `a static redirect target has to be a full URL`() {
        assertThat(validateRedirectForm(RedirectRuleForm(target = "/new"))).contains("full URL")
        assertThat(validateRedirectForm(RedirectRuleForm(target = "https://example.com"))).isNull()
        // An expression target is Cloudflare's to validate, not ours.
        assertThat(validateRedirectForm(RedirectRuleForm(target = "concat(...)", targetIsExpression = true))).isNull()
    }

    @Test
    fun `a redirect rule is refused without a target or with an unsupported status code`() {
        assertThat(validateRedirectForm(RedirectRuleForm(target = ""))).contains("target")
        assertThat(validateRedirectForm(RedirectRuleForm(expression = ""))).contains("Expression")
        assertThat(validateRedirectForm(RedirectRuleForm(target = "https://a.com", statusCode = 200)))
            .contains("status codes")
    }

    @Test
    fun `editing a redirect rule starts from what Cloudflare stored`() {
        val rule = RulesetRule(
            id = "r1",
            action = "redirect",
            expression = "http.host eq \"a.com\"",
            description = "old to new",
            enabled = false,
            actionParameters = RuleActionParameters(
                fromValue = RedirectFromValue(
                    statusCode = 302,
                    targetUrl = RedirectTargetUrl(expression = "concat(\"x\")"),
                    preserveQueryString = false
                )
            )
        )

        val form = redirectFormOf(rule)

        assertThat(form.editingId).isEqualTo("r1")
        assertThat(form.statusCode).isEqualTo(302)
        assertThat(form.targetIsExpression).isTrue()
        assertThat(form.target).isEqualTo("concat(\"x\")")
        assertThat(form.preserveQueryString).isFalse()
        assertThat(form.enabled).isFalse()
    }

    @Test
    fun `redirectSummary reads as the redirect it performs`() {
        val rule = RulesetRule(
            id = "r1",
            actionParameters = RuleActionParameters(
                fromValue = RedirectFromValue(statusCode = 301, targetUrl = RedirectTargetUrl(value = "https://a.com"))
            )
        )

        assertThat(redirectSummary(rule)).isEqualTo("301 → https://a.com")
    }

    // ---- Origin Rules ----

    @Test
    fun `an origin rule omits the origin block when neither host nor port is set`() {
        val write = buildOriginRuleWrite(OriginRuleForm(hostHeader = "www.example.com"))

        assertThat(write.action).isEqualTo("route")
        assertThat(write.actionParameters?.origin).isNull()
        assertThat(write.actionParameters?.hostHeader).isEqualTo("www.example.com")
    }

    @Test
    fun `an origin rule sends host and port together`() {
        val write = buildOriginRuleWrite(OriginRuleForm(originHost = "origin.example.com", originPort = "8443", sni = "sni.example.com"))

        assertThat(write.actionParameters?.origin).isEqualTo(RuleOrigin(host = "origin.example.com", port = 8443))
        assertThat(write.actionParameters?.sni).isEqualTo(RuleSni(value = "sni.example.com"))
    }

    @Test
    fun `an origin rule that overrides nothing is refused`() {
        assertThat(validateOriginForm(OriginRuleForm())).contains("at least one override")
        assertThat(validateOriginForm(OriginRuleForm(originHost = "o.example.com"))).isNull()
    }

    @Test
    fun `an origin port outside the valid range is refused`() {
        assertThat(validateOriginForm(OriginRuleForm(originPort = "0"))).contains("1 and 65535")
        assertThat(validateOriginForm(OriginRuleForm(originPort = "70000"))).contains("1 and 65535")
        assertThat(validateOriginForm(OriginRuleForm(originPort = "not a port"))).contains("1 and 65535")
        assertThat(validateOriginForm(OriginRuleForm(originPort = "443"))).isNull()
    }

    @Test
    fun `originSummary lists only what the rule actually overrides`() {
        val rule = RulesetRule(
            id = "r1",
            actionParameters = RuleActionParameters(origin = RuleOrigin(host = "o.example.com", port = 8443), hostHeader = "www.example.com")
        )

        assertThat(originSummary(rule)).isEqualTo("o.example.com:8443 · Host: www.example.com")
    }

    // ---- Cache Rules ----

    @Test
    fun `a bypass cache rule sends no TTLs`() {
        val write = buildCacheRuleWrite(CacheRuleForm(cache = false, edgeTtlMode = TTL_OVERRIDE_ORIGIN, edgeTtlSeconds = "60"))

        assertThat(write.action).isEqualTo("set_cache_settings")
        assertThat(write.actionParameters?.cache).isFalse()
        assertThat(write.actionParameters?.edgeTtl).isNull()
        assertThat(write.actionParameters?.browserTtl).isNull()
    }

    @Test
    fun `a TTL override carries its seconds and a respect-origin TTL doesn't`() {
        val write = buildCacheRuleWrite(
            CacheRuleForm(edgeTtlMode = TTL_OVERRIDE_ORIGIN, edgeTtlSeconds = "3600", browserTtlMode = TTL_RESPECT_ORIGIN, browserTtlSeconds = "99")
        )

        assertThat(write.actionParameters?.edgeTtl).isEqualTo(RuleTtl(mode = TTL_OVERRIDE_ORIGIN, default = 3600))
        assertThat(write.actionParameters?.browserTtl).isEqualTo(RuleTtl(mode = TTL_RESPECT_ORIGIN, default = null))
    }

    @Test
    fun `an override TTL without seconds is refused, but only when caching`() {
        assertThat(validateCacheForm(CacheRuleForm(edgeTtlMode = TTL_OVERRIDE_ORIGIN))).contains("Edge")
        assertThat(validateCacheForm(CacheRuleForm(browserTtlMode = TTL_OVERRIDE_ORIGIN, edgeTtlSeconds = ""))).contains("Browser")
        // TTLs are irrelevant when the rule bypasses the cache.
        assertThat(validateCacheForm(CacheRuleForm(cache = false, edgeTtlMode = TTL_OVERRIDE_ORIGIN))).isNull()
    }

    @Test
    fun `cacheSummary says what the rule does to the cache`() {
        val bypass = RulesetRule(id = "r1", actionParameters = RuleActionParameters(cache = false))
        val override = RulesetRule(
            id = "r2",
            actionParameters = RuleActionParameters(cache = true, edgeTtl = RuleTtl(mode = TTL_OVERRIDE_ORIGIN, default = 120))
        )

        assertThat(cacheSummary(bypass)).isEqualTo("Bypass cache")
        assertThat(cacheSummary(override)).isEqualTo("Cache · edge TTL 120s")
    }

    @Test
    fun `editing a cache rule starts from what Cloudflare stored`() {
        val rule = RulesetRule(
            id = "c1",
            expression = "true",
            actionParameters = RuleActionParameters(
                cache = true,
                edgeTtl = RuleTtl(mode = TTL_OVERRIDE_ORIGIN, default = 7200),
                browserTtl = RuleTtl(mode = TTL_BYPASS)
            )
        )

        val form = cacheFormOf(rule)

        assertThat(form.edgeTtlMode).isEqualTo(TTL_OVERRIDE_ORIGIN)
        assertThat(form.edgeTtlSeconds).isEqualTo("7200")
        assertThat(form.browserTtlMode).isEqualTo(TTL_BYPASS)
        assertThat(form.browserTtlSeconds).isEmpty()
    }

    // ---- Managed WAF ----

    private fun managedRuleset(id: String, name: String) =
        Ruleset(id = id, name = name, kind = "managed", phase = MANAGED_WAF_PHASE)

    @Test
    fun `buildManagedItems joins deployments to the catalogue and lists deployed ones first`() {
        val entrypoint = Ruleset(
            id = "ep",
            rules = listOf(
                RulesetRule(id = "rule1", action = "execute", expression = "true", enabled = true, actionParameters = RuleActionParameters(id = "ms2"))
            )
        )
        val catalogue = listOf(managedRuleset("ms1", "OWASP Core Ruleset"), managedRuleset("ms2", "Cloudflare Managed Ruleset"))

        val items = buildManagedItems(entrypoint, catalogue)

        assertThat(items.map { it.name })
            .containsExactly("Cloudflare Managed Ruleset", "OWASP Core Ruleset").inOrder()
        assertThat(items.first().isDeployed).isTrue()
        assertThat(items.first().deploymentRuleId).isEqualTo("rule1")
        assertThat(items.last().isDeployed).isFalse()
    }

    @Test
    fun `a ruleset deployed but missing from the catalogue is still listed`() {
        val entrypoint = Ruleset(
            id = "ep",
            rules = listOf(
                RulesetRule(id = "rule1", action = "execute", enabled = true, actionParameters = RuleActionParameters(id = "unknown"))
            )
        )

        val items = buildManagedItems(entrypoint, catalogue = emptyList())

        assertThat(items).hasSize(1)
        assertThat(items.single().rulesetId).isEqualTo("unknown")
        assertThat(items.single().isDeployed).isTrue()
    }

    @Test
    fun `rulesets from another phase or kind are not offered as managed WAF`() {
        val catalogue = listOf(
            managedRuleset("ms1", "Managed"),
            Ruleset(id = "z1", name = "My custom rules", kind = "zone", phase = MANAGED_WAF_PHASE),
            Ruleset(id = "m2", name = "DDoS managed", kind = "managed", phase = "ddos_l7")
        )

        val items = buildManagedItems(entrypoint = null, catalogue = catalogue)

        assertThat(items.map { it.rulesetId }).containsExactly("ms1")
    }

    @Test
    fun `deploymentLabel distinguishes not deployed, disabled, and scoped`() {
        assertThat(deploymentLabel(ManagedRulesetItem("m", "M", null))).isEqualTo("Not deployed")
        assertThat(deploymentLabel(ManagedRulesetItem("m", "M", null, deploymentRuleId = "r", enabled = false)))
            .isEqualTo("Deployed but disabled")
        assertThat(deploymentLabel(ManagedRulesetItem("m", "M", null, deploymentRuleId = "r", enabled = true, expression = "true")))
            .isEqualTo("Active on all traffic")
        assertThat(deploymentLabel(ManagedRulesetItem("m", "M", null, deploymentRuleId = "r", enabled = true, expression = "http.host eq \"a\"")))
            .contains("Active on: http.host")
    }
}
