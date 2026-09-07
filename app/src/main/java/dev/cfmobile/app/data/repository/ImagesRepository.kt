package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfImage
import dev.cfmobile.app.data.remote.dto.ImagesStats
import dev.cfmobile.app.data.remote.UploadPayload
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Image inventory, quota, and uploading a picture chosen on the device. Variant
 *  configuration and signed-URL delivery aren't implemented. */
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

    /** Uploads the picked file as multipart. The bytes stream from the content provider as
     *  OkHttp writes the request, so a large picture is never held in memory whole. */
    suspend fun uploadImage(accountId: String, payload: UploadPayload): ApiResult<CfImage> =
        safeApiCall { api.uploadImage(accountId, payload.toPart()) }
}
