package dev.cfmobile.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PermissionGroupRef(
    val id: String = "",
    val name: String? = null
)

/**
 * One policy on an API token. [resources] maps a resource identifier (for example
 * `com.cloudflare.api.account.<id>` or `com.cloudflare.api.account.zone.*`) to `"*"`, or,
 * for "all zones in an account", to a nested map of zone resources.
 */
@JsonClass(generateAdapter = true)
data class TokenPolicy(
    val id: String? = null,
    val effect: String = "allow",
    val resources: Map<String, Any> = emptyMap(),
    @Json(name = "permission_groups") val permissionGroups: List<PermissionGroupRef> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TokenIpList(
    @Json(name = "in") val allowed: List<String>? = null,
    @Json(name = "not_in") val denied: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class TokenCondition(
    @Json(name = "request_ip") val requestIp: TokenIpList? = null
)

/** A permission group as Cloudflare lists it for token creation. IDs are the stable key;
 *  names can change cosmetically (spec 9, 124). */
@JsonClass(generateAdapter = true)
data class PermissionGroup(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val scopes: List<String> = emptyList()
)

/** Body for creating or updating a token. `status` is only sent on update. */
@JsonClass(generateAdapter = true)
data class TokenWrite(
    val name: String,
    val policies: List<TokenPolicy>,
    @Json(name = "not_before") val notBefore: String? = null,
    @Json(name = "expires_on") val expiresOn: String? = null,
    val condition: TokenCondition? = null,
    val status: String? = null
)

/** Create responses are the one place Cloudflare returns a token secret ([value]). */
@JsonClass(generateAdapter = true)
data class CreatedToken(
    val id: String = "",
    val name: String = "",
    val status: String? = null,
    val value: String? = null,
    @Json(name = "expires_on") val expiresOn: String? = null
)

@JsonClass(generateAdapter = true)
data class EmptyBody(val placeholder: String? = null)
