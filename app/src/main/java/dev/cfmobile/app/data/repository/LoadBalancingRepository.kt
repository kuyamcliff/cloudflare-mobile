package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.LoadBalancer
import dev.cfmobile.app.data.remote.dto.LoadBalancerPool
import dev.cfmobile.app.data.remote.dto.LoadBalancerPoolWrite
import dev.cfmobile.app.data.remote.dto.LoadBalancerWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** PRD §9 "Load balancers, monitors, pools, origins" - monitors (health checks) aren't
 *  implemented; a pool works without one, just without automatic origin failover based on
 *  health. Pools are account-level (shared across zones); load balancers are zone-level and
 *  reference pools by id. */
class LoadBalancingRepository(private val api: CloudflareApi) {

    suspend fun listPools(accountId: String): ApiResult<List<LoadBalancerPool>> =
        safeApiCall { api.listLoadBalancerPools(accountId) }

    suspend fun createPool(accountId: String, pool: LoadBalancerPoolWrite): ApiResult<LoadBalancerPool> =
        safeApiCall { api.createLoadBalancerPool(accountId, pool) }

    suspend fun deletePool(accountId: String, poolId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteLoadBalancerPool(accountId, poolId) }

    suspend fun listLoadBalancers(zoneId: String): ApiResult<List<LoadBalancer>> =
        safeApiCall { api.listLoadBalancers(zoneId) }

    suspend fun createLoadBalancer(zoneId: String, loadBalancer: LoadBalancerWrite): ApiResult<LoadBalancer> =
        safeApiCall { api.createLoadBalancer(zoneId, loadBalancer) }

    suspend fun deleteLoadBalancer(zoneId: String, loadBalancerId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteLoadBalancer(zoneId, loadBalancerId) }
}
