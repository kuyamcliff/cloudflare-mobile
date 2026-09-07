package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.DnsFirewallCluster
import dev.cfmobile.app.data.remote.dto.DnsFirewallClusterWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** DNS Firewall clusters: Cloudflare's resolvers fronting your own authoritative nameservers,
 *  which is a different product from the zone DNS this app otherwise manages. */
class DnsFirewallRepository(private val api: CloudflareApi) {

    suspend fun listClusters(accountId: String): ApiResult<List<DnsFirewallCluster>> =
        safeApiCall { api.listDnsFirewallClusters(accountId) }

    suspend fun createCluster(accountId: String, name: String, upstreamIps: List<String>): ApiResult<DnsFirewallCluster> =
        safeApiCall {
            api.createDnsFirewallCluster(accountId, DnsFirewallClusterWrite(name = name, upstreamIps = upstreamIps))
        }

    suspend fun deleteCluster(accountId: String, clusterId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteDnsFirewallCluster(accountId, clusterId) }
}
