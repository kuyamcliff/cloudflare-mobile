package dev.cfmobile.app.core.capabilities

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.data.remote.dto.PermissionGroupRef
import dev.cfmobile.app.data.remote.dto.TokenPolicy
import org.junit.Test

class TokenCapabilitiesTest {
    private fun ep(method: String, path: String, vararg perms: String) =
        EndpointDef("$method $path", method, path, "", "", "g", perms.toList(), emptyList(), false, emptyList(), null, null, false)

    private val dnsList = ep("GET", "zones/{zone_id}/dns_records", "DNS Read", "DNS Write")
    private val dnsCreate = ep("POST", "zones/{zone_id}/dns_records", "DNS Write")
    private val r2List = ep("GET", "accounts/{account_id}/r2/buckets", "Workers R2 Storage Read", "Workers R2 Storage Write")

    private fun caps(vararg policies: TokenPolicy) = TokenCapabilities.fromPolicies(null, policies.toList(), 0)

    @Test fun `read-only DNS token can list but not create`() {
        val c = caps(TokenPolicy(resources = mapOf("com.cloudflare.api.account.zone.z1" to "*"), permissionGroups = listOf(PermissionGroupRef("1", "DNS Read"))))
        assertThat(c.evaluate(dnsList, zoneId = "z1")).isEqualTo(CapabilityState.AVAILABLE_READ)
        assertThat(c.evaluate(dnsCreate, zoneId = "z1")).isEqualTo(CapabilityState.TOKEN_RESTRICTED)
        assertThat(c.evaluate(r2List, accountId = "a1")).isEqualTo(CapabilityState.TOKEN_RESTRICTED)
    }

    @Test fun `zone grants are scoped to their zones`() {
        val c = caps(TokenPolicy(resources = mapOf("com.cloudflare.api.account.zone.z1" to "*"), permissionGroups = listOf(PermissionGroupRef("2", "DNS Write"))))
        assertThat(c.evaluate(dnsCreate, zoneId = "z1")).isEqualTo(CapabilityState.AVAILABLE_WRITE)
        assertThat(c.evaluate(dnsCreate, zoneId = "z2", zoneAccountId = "a1")).isEqualTo(CapabilityState.TOKEN_RESTRICTED)
    }

    @Test fun `all zones in an account covers that account's zones only`() {
        val c = caps(TokenPolicy(resources = mapOf("com.cloudflare.api.account.a1" to mapOf("com.cloudflare.api.account.zone.*" to "*")), permissionGroups = listOf(PermissionGroupRef("2", "DNS Write"))))
        assertThat(c.evaluate(dnsCreate, zoneId = "zx", zoneAccountId = "a1")).isEqualTo(CapabilityState.AVAILABLE_WRITE)
        assertThat(c.evaluate(dnsCreate, zoneId = "zy", zoneAccountId = "a2")).isEqualTo(CapabilityState.TOKEN_RESTRICTED)
    }

    @Test fun `deny policies override allows on the same resource`() {
        val c = caps(
            TokenPolicy(resources = mapOf("com.cloudflare.api.account.*" to "*"), permissionGroups = listOf(PermissionGroupRef("3", "Workers R2 Storage Write"))),
            TokenPolicy(effect = "deny", resources = mapOf("com.cloudflare.api.account.a2" to "*"), permissionGroups = listOf(PermissionGroupRef("3", "Workers R2 Storage Write")))
        )
        assertThat(c.evaluate(r2List, accountId = "a1")).isEqualTo(CapabilityState.AVAILABLE_WRITE)
        assertThat(c.evaluate(r2List, accountId = "a2")).isEqualTo(CapabilityState.TOKEN_RESTRICTED)
    }

    @Test fun `without readable policies state comes from observed responses`() {
        var c = TokenCapabilities.observedOnly(null, 0)
        assertThat(c.evaluate(dnsList)).isEqualTo(CapabilityState.UNKNOWN)
        c = c.withObservation(dnsList.id, allowed = false)
        assertThat(c.evaluate(dnsList)).isEqualTo(CapabilityState.TOKEN_RESTRICTED)
        c = c.withObservation(dnsList.id, allowed = true)
        assertThat(c.evaluate(dnsList)).isEqualTo(CapabilityState.AVAILABLE_READ)
    }

    @Test fun `permission names match case and whitespace insensitively`() {
        val c = caps(TokenPolicy(resources = mapOf("com.cloudflare.api.account.zone.*" to "*"), permissionGroups = listOf(PermissionGroupRef("1", "dns  read"))))
        assertThat(c.evaluate(dnsList, zoneId = "z1")).isEqualTo(CapabilityState.AVAILABLE_READ)
    }

    @Test fun `write names are classified`() {
        assertThat(TokenCapabilities.isWriteName("DNS Write")).isTrue()
        assertThat(TokenCapabilities.isWriteName("Zaraz Edit")).isTrue()
        assertThat(TokenCapabilities.isWriteName("Cache Purge")).isTrue()
        assertThat(TokenCapabilities.isWriteName("Analytics Read")).isFalse()
    }
}
