package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfImage
import dev.cfmobile.app.data.remote.dto.ImagesStats
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Image inventory and quota. Uploading from the device gallery isn't implemented - that
 *  needs a picker and media permissions, a separate surface. */
class ImagesRepository(private val api: CloudflareApi) {

    /** Unwraps the nested "images" array, the same shape quirk R2's bucket list has. */
    suspend fun listImages(accountId: String): ApiResult<List<CfImage>> =
        when (val result = safeApiCall { api.listImages(accountId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.images)
            is ApiResult.Failure -> result
        }

    suspend fun getStats(accountId: String): ApiResult<ImagesStats> =
        safeApiCall { api.getImagesStats(accountId) }

    suspend fun deleteImage(accountId: String, imageId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteImage(accountId, imageId) }
}
