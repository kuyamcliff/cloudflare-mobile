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

    @Test
    fun `audit logs is implemented via an account-scoped route`() {
        val auditLogs = CapabilityRegistry.byId("audit_logs")!!
        assertThat(auditLogs.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(auditLogs.accountRoute).isNotNull()
        assertThat(auditLogs.accountRoute!!("acct1")).isEqualTo("account/acct1/auditlogs")
        assertThat(auditLogs.migrationHint).isNotNull()
    }

    @Test
    fun `load balancing is implemented and discloses its monitor and single-pool gaps`() {
        val loadBalancing = CapabilityRegistry.byId("load_balancing")!!
        assertThat(loadBalancing.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(loadBalancing.accountRoute).isNotNull()
        assertThat(loadBalancing.accountRoute!!("acct1")).isEqualTo("account/acct1/loadbalancing")
        assertThat(loadBalancing.migrationHint).isNotNull()
    }

    @Test
    fun `bot management is implemented via a zone-scoped route and discloses the Super Bot Fight Mode gap`() {
        val botManagement = CapabilityRegistry.byId("bot_management")!!
        assertThat(botManagement.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(botManagement.zoneRoute).isNotNull()
        assertThat(botManagement.migrationHint).isNotNull()
    }

    @Test
    fun `r2 is implemented for bucket management only`() {
        val r2 = CapabilityRegistry.byId("r2")!!
        assertThat(r2.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(r2.accountRoute).isNotNull()
        assertThat(r2.accountRoute!!("acct1")).isEqualTo("account/acct1/r2")
        assertThat(r2.migrationHint).isNotNull()
    }

    @Test
    fun `kv is implemented for namespace management only`() {
        val kv = CapabilityRegistry.byId("kv")!!
        assertThat(kv.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(kv.accountRoute).isNotNull()
        assertThat(kv.accountRoute!!("acct1")).isEqualTo("account/acct1/kv")
        assertThat(kv.migrationHint).isNotNull()
    }

    @Test
    fun `d1 is implemented for database management only`() {
        val d1 = CapabilityRegistry.byId("d1")!!
        assertThat(d1.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(d1.accountRoute).isNotNull()
        assertThat(d1.accountRoute!!("acct1")).isEqualTo("account/acct1/d1")
        assertThat(d1.migrationHint).isNotNull()
    }

    @Test
    fun `workers is implemented for list-view-delete only`() {
        val workers = CapabilityRegistry.byId("workers")!!
        assertThat(workers.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(workers.accountRoute).isNotNull()
        assertThat(workers.accountRoute!!("acct1")).isEqualTo("account/acct1/workers")
        assertThat(workers.migrationHint).isNotNull()
    }

    @Test
    fun `pages is implemented read-mostly for projects and deployment history`() {
        val pages = CapabilityRegistry.byId("pages")!!
        assertThat(pages.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(pages.accountRoute).isNotNull()
        assertThat(pages.accountRoute!!("acct1")).isEqualTo("account/acct1/pages")
        assertThat(pages.migrationHint).isNotNull()
    }

    @Test
    fun `access is implemented for common email-based policy cases only`() {
        val access = CapabilityRegistry.byId("access")!!
        assertThat(access.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(access.accountRoute).isNotNull()
        assertThat(access.accountRoute!!("acct1")).isEqualTo("account/acct1/access")
        assertThat(access.migrationHint).isNotNull()
    }

    @Test
    fun `gateway is implemented for DNS policies only`() {
        val gateway = CapabilityRegistry.byId("gateway")!!
        assertThat(gateway.status).isEqualTo(CapabilityStatus.IMPLEMENTED)
        assertThat(gateway.accountRoute).isNotNull()
        assertThat(gateway.accountRoute!!("acct1")).isEqualTo("account/acct1/gateway")
        assertThat(gateway.migrationHint).isNotNull()
    }
}
