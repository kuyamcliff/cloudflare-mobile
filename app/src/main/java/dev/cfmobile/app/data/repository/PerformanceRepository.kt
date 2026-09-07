package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.ArgoSetting
import dev.cfmobile.app.data.remote.dto.ArgoSettingWrite
import dev.cfmobile.app.data.remote.dto.CacheSetting
import dev.cfmobile.app.data.remote.dto.CacheSettingWrite
import dev.cfmobile.app.data.remote.dto.ManagedHeaders
import dev.cfmobile.app.data.remote.dto.UrlNormalization
import dev.cfmobile.app.data.remote.dto.UrlNormalizationWrite
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * The routing and caching controls that sit outside `/zones/{id}/settings` and so can't use the
 * declarative zone-settings machinery: Argo, the three tiered-cache toggles, Cache Reserve,
 * managed header transforms, and URL normalization. Each has its own endpoint and its own
 * response shape, which is exactly why they need a repository rather than a settings spec.
 */
class PerformanceRepository(private val api: CloudflareApi) {

    suspend fun getSmartRouting(zoneId: String): ApiResult<ArgoSetting> =
        safeApiCall { api.getArgoSmartRouting(zoneId) }

    suspend fun setSmartRouting(zoneId: String, value: String): ApiResult<ArgoSetting> =
        safeApiCall { api.updateArgoSmartRouting(zoneId, ArgoSettingWrite(value)) }

    suspend fun getTieredCaching(zoneId: String): ApiResult<ArgoSetting> =
        safeApiCall { api.getTieredCaching(zoneId) }

    suspend fun setTieredCaching(zoneId: String, value: String): ApiResult<ArgoSetting> =
        safeApiCall { api.updateTieredCaching(zoneId, ArgoSettingWrite(value)) }

    suspend fun getCacheReserve(zoneId: String): ApiResult<CacheSetting> =
        safeApiCall { api.getCacheReserve(zoneId) }

    suspend fun setCacheReserve(zoneId: String, value: String): ApiResult<CacheSetting> =
        safeApiCall { api.updateCacheReserve(zoneId, CacheSettingWrite(value)) }

    suspend fun getRegionalTieredCache(zoneId: String): ApiResult<CacheSetting> =
        safeApiCall { api.getRegionalTieredCache(zoneId) }

    suspend fun setRegionalTieredCache(zoneId: String, value: String): ApiResult<CacheSetting> =
        safeApiCall { api.updateRegionalTieredCache(zoneId, CacheSettingWrite(value)) }

    suspend fun getSmartTieredCache(zoneId: String): ApiResult<CacheSetting> =
        safeApiCall { api.getSmartTieredCache(zoneId) }

    suspend fun setSmartTieredCache(zoneId: String, value: String): ApiResult<CacheSetting> =
        safeApiCall { api.updateSmartTieredCache(zoneId, CacheSettingWrite(value)) }

    suspend fun getManagedHeaders(zoneId: String): ApiResult<ManagedHeaders> =
        safeApiCall { api.getManagedHeaders(zoneId) }

    /** Cloudflare replaces both header lists on write, so callers send the whole document back
     *  with one header's `enabled` changed rather than a single field. */
    suspend fun setManagedHeaders(zoneId: String, headers: ManagedHeaders): ApiResult<ManagedHeaders> =
        safeApiCall { api.updateManagedHeaders(zoneId, headers) }

    suspend fun getUrlNormalization(zoneId: String): ApiResult<UrlNormalization> =
        safeApiCall { api.getUrlNormalization(zoneId) }

    suspend fun setUrlNormalization(zoneId: String, type: String, scope: String): ApiResult<UrlNormalization> =
        safeApiCall { api.updateUrlNormalization(zoneId, UrlNormalizationWrite(type = type, scope = scope)) }
}
