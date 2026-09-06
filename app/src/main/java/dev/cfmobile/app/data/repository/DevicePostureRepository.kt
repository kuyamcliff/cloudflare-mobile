package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.EnrolledDevice
import dev.cfmobile.app.data.remote.dto.PostureRule
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Enrolled WARP devices and the posture rules evaluated against them, plus revoking a
 *  device's registration. Editing a posture rule isn't implemented - the rule types each carry
 *  their own configuration shape. */
class DevicePostureRepository(private val api: CloudflareApi) {

    suspend fun listDevices(accountId: String): ApiResult<List<EnrolledDevice>> =
        safeApiCall { api.listEnrolledDevices(accountId) }

    suspend fun listPostureRules(accountId: String): ApiResult<List<PostureRule>> =
        safeApiCall { api.listPostureRules(accountId) }

    /** Revokes one device's registration. The user has to re-enrol before that device can
     *  reach anything behind Zero Trust again. */
    suspend fun revokeDevice(accountId: String, deviceId: String): ApiResult<Unit> =
        safeApiCallUnit { api.revokeDevices(accountId, listOf(deviceId)) }
}
