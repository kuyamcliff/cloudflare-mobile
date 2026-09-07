package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AddressMap
import dev.cfmobile.app.data.remote.dto.AddressMapUpdate
import dev.cfmobile.app.data.remote.dto.AddressMapWrite
import dev.cfmobile.app.data.remote.dto.AddressingPrefix
import dev.cfmobile.app.data.remote.dto.PrefixBgpStatus
import dev.cfmobile.app.data.remote.dto.PrefixBgpStatusWrite
import dev.cfmobile.app.data.remote.dto.PrefixDescriptionWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/**
 * BYOIP prefixes and address maps. Adding a prefix isn't here: it needs a signed letter of
 * authorization and Cloudflare's own approval before anything can be announced, which is not
 * a phone-shaped flow. What is phone-shaped is the switch this exposes - starting or stopping
 * the BGP announcement of a prefix already approved.
 */
class AddressingRepository(private val api: CloudflareApi) {

    suspend fun listPrefixes(accountId: String): ApiResult<List<AddressingPrefix>> =
        safeApiCall { api.listAddressingPrefixes(accountId) }

    suspend fun setDescription(accountId: String, prefixId: String, description: String): ApiResult<AddressingPrefix> =
        safeApiCall { api.updatePrefixDescription(accountId, prefixId, PrefixDescriptionWrite(description)) }

    suspend fun getBgpStatus(accountId: String, prefixId: String): ApiResult<PrefixBgpStatus> =
        safeApiCall { api.getPrefixBgpStatus(accountId, prefixId) }

    /** Turning this on makes Cloudflare announce the prefix to the internet; turning it off
     *  withdraws it. Either way traffic moves within minutes. */
    suspend fun setAdvertised(accountId: String, prefixId: String, advertised: Boolean): ApiResult<PrefixBgpStatus> =
        safeApiCall { api.setPrefixBgpStatus(accountId, prefixId, PrefixBgpStatusWrite(advertised)) }

    suspend fun listAddressMaps(accountId: String): ApiResult<List<AddressMap>> =
        safeApiCall { api.listAddressMaps(accountId) }

    /** The list response carries no ips or memberships - only this single read does. */
    suspend fun getAddressMap(accountId: String, addressMapId: String): ApiResult<AddressMap> =
        safeApiCall { api.getAddressMap(accountId, addressMapId) }

    suspend fun createAddressMap(accountId: String, description: String, enabled: Boolean): ApiResult<AddressMap> =
        safeApiCall { api.createAddressMap(accountId, AddressMapWrite(description = description, enabled = enabled)) }

    suspend fun setAddressMapEnabled(accountId: String, addressMapId: String, enabled: Boolean): ApiResult<AddressMap> =
        safeApiCall { api.updateAddressMap(accountId, addressMapId, AddressMapUpdate(enabled = enabled)) }

    suspend fun deleteAddressMap(accountId: String, addressMapId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAddressMap(accountId, addressMapId) }
}
