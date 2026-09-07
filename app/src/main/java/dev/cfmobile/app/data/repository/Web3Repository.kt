package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.Web3Hostname
import dev.cfmobile.app.data.remote.dto.Web3HostnameWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Web3 gateway hostnames: an IPFS or Ethereum gateway served on your own domain. */
class Web3Repository(private val api: CloudflareApi) {

    suspend fun listHostnames(zoneId: String): ApiResult<List<Web3Hostname>> =
        safeApiCall { api.listWeb3Hostnames(zoneId) }

    suspend fun createHostname(
        zoneId: String,
        name: String,
        target: String,
        description: String?,
        dnslink: String?
    ): ApiResult<Web3Hostname> =
        safeApiCall {
            api.createWeb3Hostname(
                zoneId,
                Web3HostnameWrite(name = name, target = target, description = description, dnslink = dnslink)
            )
        }

    suspend fun deleteHostname(zoneId: String, identifier: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteWeb3Hostname(zoneId, identifier) }
}
