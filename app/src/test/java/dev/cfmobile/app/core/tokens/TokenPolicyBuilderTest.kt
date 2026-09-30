package dev.cfmobile.app.core.tokens

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.dto.ApiToken
import dev.cfmobile.app.data.remote.dto.PermissionGroup
import dev.cfmobile.app.data.remote.dto.PermissionGroupRef
import dev.cfmobile.app.data.remote.dto.TokenCondition
import dev.cfmobile.app.data.remote.dto.TokenIpList
import dev.cfmobile.app.data.remote.dto.TokenPolicy
import org.junit.Test
import java.time.Instant

class TokenPolicyBuilderTest {
    private val dnsWrite = PermissionGroup("g1", "DNS Write", scopes = listOf("com.cloudflare.api.account.zone"))
    private val r2Write = PermissionGroup("g2", "Workers R2 Storage Write", scopes = listOf("com.cloudflare.api.account"))
    private val userRead = PermissionGroup("g3", "User Details Read", scopes = listOf("com.cloudflare.api.user"))
    private val now = Instant.parse("2026-09-30T00:00:00Z")

    @Test fun `user token with all zones and specific accounts`() {
        val body = TokenPolicyBuilder.build(
            TokenDraft(name = "ci", selected = listOf(dnsWrite, r2Write, userRead), accounts = AccountSelection.Specific(setOf("a1")), zones = ZoneSelection.All, expiry = ExpiryChoice.D30),
            ownerAccountId = null, userId = "u1", now = now
        )
        assertThat(body.policies).hasSize(3)
        val acct = body.policies.first { it.permissionGroups.single().id == "g2" }
        assertThat(acct.resources).containsExactly("com.cloudflare.api.account.a1", "*")
        val zone = body.policies.first { it.permissionGroups.single().id == "g1" }
        assertThat(zone.resources).containsExactly("com.cloudflare.api.account.zone.*", "*")
        val user = body.policies.first { it.permissionGroups.single().id == "g3" }
        assertThat(user.resources).containsExactly("com.cloudflare.api.user.u1", "*")
        assertThat(body.expiresOn).isEqualTo("2026-10-30T00:00:00Z")
        // Only IDs go to Cloudflare.
        assertThat(body.policies.flatMap { it.permissionGroups }.all { it.name == null }).isTrue()
    }

    @Test fun `all zones in an account nests the zone wildcard`() {
        val body = TokenPolicyBuilder.build(TokenDraft(name = "x", selected = listOf(dnsWrite), zones = ZoneSelection.AllInAccount("a9")), null, null, now)
        assertThat(body.policies.single().resources).containsExactly("com.cloudflare.api.account.a9", mapOf("com.cloudflare.api.account.zone.*" to "*"))
    }

    @Test fun `account-owned tokens are confined to their account`() {
        val body = TokenPolicyBuilder.build(TokenDraft(name = "x", selected = listOf(r2Write, dnsWrite), zones = ZoneSelection.All), ownerAccountId = "own", userId = null, now = now)
        assertThat(body.policies.first { it.permissionGroups.single().id == "g2" }.resources).containsExactly("com.cloudflare.api.account.own", "*")
        assertThat(body.policies.first { it.permissionGroups.single().id == "g1" }.resources).containsExactly("com.cloudflare.api.account.own", mapOf("com.cloudflare.api.account.zone.*" to "*"))
    }

    @Test fun `conditions and validation`() {
        val body = TokenPolicyBuilder.build(TokenDraft(name = "x", selected = listOf(dnsWrite), allowedIps = "203.0.113.0/24, 2001:db8::/32", expiry = ExpiryChoice.NONE), null, null, now)
        assertThat(body.condition?.requestIp?.allowed).containsExactly("203.0.113.0/24", "2001:db8::/32")
        assertThat(body.expiresOn).isNull()

        val problems = TokenPolicyBuilder.validate(TokenDraft(name = "", selected = listOf(userRead), allowedIps = "300.1.1.1, 10.0.0.0/33"), isAccountOwned = true)
        assertThat(problems).contains("Enter a token name")
        assertThat(problems).contains("Account-owned tokens cannot hold user permissions")
        assertThat(problems.filter { it.startsWith("Not a valid IP") }).hasSize(2)
    }

    @Test fun `update keeps policies and changes only editable fields`() {
        val policy = TokenPolicy("p1", "allow", mapOf("com.cloudflare.api.account.*" to "*"), listOf(PermissionGroupRef("g", "X")))
        val existing = ApiToken(id = "t", name = "old", policies = listOf(policy), notBefore = "2026-01-01T00:00:00Z")
        val body = TokenPolicyBuilder.buildUpdate(existing, "new", active = false, expiresOn = null, allowedIps = "", deniedIps = "")
        assertThat(body.policies).containsExactly(policy)
        assertThat(body.status).isEqualTo("disabled")
        assertThat(body.condition).isNull()
        assertThat(body.notBefore).isEqualTo("2026-01-01T00:00:00Z")
    }

    @Test fun `risk flags describe documented attributes`() {
        val broad = ApiToken(
            id = "t", name = "n", expiresOn = null,
            policies = listOf(TokenPolicy(resources = mapOf("com.cloudflare.api.account.*" to "*", "com.cloudflare.api.account.zone.*" to "*"), permissionGroups = listOf(PermissionGroupRef("g", "API Tokens Write"))))
        )
        val labels = TokenRisk.flags(broad, now).map { it.label }
        assertThat(labels).containsAtLeast("No expiration configured", "No IP restriction", "Broad account scope", "All zones", "Can create tokens")

        val tight = ApiToken(id = "t", name = "n", expiresOn = "2026-12-01T00:00:00Z", condition = TokenCondition(TokenIpList(allowed = listOf("203.0.113.1"))),
            policies = listOf(TokenPolicy(resources = mapOf("com.cloudflare.api.account.zone.z1" to "*"), permissionGroups = listOf(PermissionGroupRef("g", "DNS Read")))))
        assertThat(TokenRisk.flags(tight, now)).isEmpty()
    }

    @Test fun `templates resolve against the live catalog and report missing names`() {
        val (found, missing) = TokenTemplates.resolve(TokenTemplates.all.first { it.id == "dns" }, listOf(dnsWrite), isAccountOwned = false)
        assertThat(found).containsExactly(dnsWrite)
        assertThat(missing).containsExactly("Zone Read")
    }
}
