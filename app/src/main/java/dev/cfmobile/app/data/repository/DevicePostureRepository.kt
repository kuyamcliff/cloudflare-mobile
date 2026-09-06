package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.EnrolledDevice
import dev.cfmobile.app.data.remote.dto.PostureRule
import dev.cfmobile.app.data.remote.safeApiCall

/** Read-only: enrolled WARP devices and the posture rules evaluated against them. Revoking a
 *  device or editing a posture rule isn't implemented - both are high-blast-radius changes
 *  that deserve a full screen's worth of context. */
class DevicePostureRepository(private val api: CloudflareApi) {

    suspend fun listDevices(accountId: String): ApiResult<List<EnrolledDevice>> =
        safeApiCall { api.listEnrolledDevices(accountId) }

    suspend fun listPostureRules(accountId: String): ApiResult<List<PostureRule>> =
        safeApiCall { api.listPostureRules(accountId) }
}
