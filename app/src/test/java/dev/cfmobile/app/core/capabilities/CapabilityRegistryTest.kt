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
}
