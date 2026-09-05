package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.PurgeCacheRequest
import dev.cfmobile.app.data.remote.dto.ZoneSettingPatchInt
import dev.cfmobile.app.data.remote.dto.ZoneSettingPatchString
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** The string-valued zone settings this app exposes, matching Cloudflare's setting IDs. */
enum class StringSetting(val id: String) {
    SSL("ssl"),
    ALWAYS_USE_HTTPS("always_use_https"),
    MIN_TLS_VERSION("min_tls_version"),
    AUTOMATIC_HTTPS_REWRITES("automatic_https_rewrites"),
    CACHE_LEVEL("cache_level"),
    DEVELOPMENT_MODE("development_mode"),
    SECURITY_LEVEL("security_level"),
    BOT_FIGHT_MODE("bot_fight_mode")
}

class ZoneSettingsRepository(private val api: CloudflareApi) {

    suspend fun getSetting(zoneId: String, setting: StringSetting): ApiResult<String> =
        when (val result = safeApiCall { api.getStringSetting(zoneId, setting.id) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.value)
            is ApiResult.Failure -> result
        }

    suspend fun setSetting(zoneId: String, setting: StringSetting, value: String): ApiResult<String> =
        when (val result = safeApiCall { api.patchStringSetting(zoneId, setting.id, ZoneSettingPatchString(value)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.value)
            is ApiResult.Failure -> result
        }

    suspend fun getBrowserCacheTtl(zoneId: String): ApiResult<Int> =
        when (val result = safeApiCall { api.getIntSetting(zoneId, "browser_cache_ttl") }) {
            is ApiResult.Success -> ApiResult.Success(result.data.value)
            is ApiResult.Failure -> result
        }

    suspend fun setBrowserCacheTtl(zoneId: String, seconds: Int): ApiResult<Int> =
        when (val result = safeApiCall { api.patchIntSetting(zoneId, "browser_cache_ttl", ZoneSettingPatchInt(seconds)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.value)
            is ApiResult.Failure -> result
        }

    suspend fun purgeEverything(zoneId: String): ApiResult<Unit> =
        safeApiCallUnit { api.purgeCache(zoneId, PurgeCacheRequest(purgeEverything = true)) }

    suspend fun purgeFiles(zoneId: String, urls: List<String>): ApiResult<Unit> =
        safeApiCallUnit { api.purgeCache(zoneId, PurgeCacheRequest(files = urls)) }
}
