package dev.cfmobile.app.core.capabilities

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CapabilityRegistryTest {

    @Test
    fun `every capability has a unique id`() {
        val ids = CapabilityRegistry.zoneCapabilities.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `every implemented zone capability has a working route`() {
        CapabilityRegistry.implemented()
            .filter { it.scope == CapabilityScope.ZONE }
            .forEach { capability ->
                assertThat(capability.zoneRoute).isNotNull()
                val route = capability.zoneRoute!!("zone123", "example.com")
                assertThat(route).contains("zone123")
                assertThat(route).contains("example.com")
            }
    }

    @Test
    fun `no not-implemented capability exposes a route`() {
        CapabilityRegistry.notYetImplemented().forEach { capability ->
            assertThat(capability.zoneRoute).isNull()
        }
    }

    @Test
    fun `implemented and not-yet-implemented partition the full registry`() {
        val implementedIds = CapabilityRegistry.implemented().map { it.id }.toSet()
        val notImplementedIds = CapabilityRegistry.notYetImplemented().map { it.id }.toSet()

        assertThat(implementedIds intersect notImplementedIds).isEmpty()
        assertThat(implementedIds + notImplementedIds).containsExactlyElementsIn(CapabilityRegistry.zoneCapabilities.map { it.id })
    }

    @Test
    fun `byId finds a known capability and returns null for an unknown one`() {
        assertThat(CapabilityRegistry.byId("dns.records")).isNotNull()
        assertThat(CapabilityRegistry.byId("does.not.exist")).isNull()
    }

    @Test
    fun `page rules is marked deprecated with a migration hint, per PRD section 18`() {
        val pageRules = CapabilityRegistry.byId("page_rules")!!
        assertThat(pageRules.deprecated).isTrue()
        assertThat(pageRules.migrationHint).isNotNull()
    }

    @Test
    fun `waf rulesets is implemented with a route and discloses its managed-rules gap`() {
        val waf = CapabilityRegistry.byId("waf.rulesets")!!
        assertThat(waf.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(waf.zoneRoute).isNotNull()
        assertThat(waf.migrationHint).isNotNull()
    }

    @Test
    fun `rate limiting is implemented with a route`() {
        val rateLimiting = CapabilityRegistry.byId("rate_limiting")!!
        assertThat(rateLimiting.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(rateLimiting.zoneRoute).isNotNull()
    }

    @Test
    fun `transform rules is implemented with a route and discloses its unverified request format and scope gaps`() {
        val transformRules = CapabilityRegistry.byId("transform_rules")!!
        assertThat(transformRules.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(transformRules.zoneRoute).isNotNull()
        assertThat(transformRules.migrationHint).isNotNull()
    }

    @Test
    fun `account members is implemented via an account-scoped route, not a zone-scoped one`() {
        val accountMembers = CapabilityRegistry.byId("account_members")!!
        assertThat(accountMembers.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(accountMembers.zoneRoute).isNull()
        assertThat(accountMembers.accountRoute).isNotNull()
        assertThat(accountMembers.accountRoute!!("acct1")).isEqualTo("account/acct1/members")
    }
}
