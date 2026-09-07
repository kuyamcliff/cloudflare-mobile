package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfImage
import dev.cfmobile.app.data.remote.dto.ImageSigningKey
import dev.cfmobile.app.data.remote.dto.ImageVariant
import dev.cfmobile.app.data.remote.dto.ImageVariantOptions
import dev.cfmobile.app.data.remote.dto.ImageVariantWrite
import dev.cfmobile.app.data.remote.dto.ImagesStats
import dev.cfmobile.app.data.remote.UploadPayload
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/**
 * Image inventory, quota, uploading a picture chosen on the device, and the named variants a
 * delivery URL can ask for. Signing keys are listed by name only - a key's value is what signs
 * a private image's URL, so this app never reads one.
 */
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

    /** Cloudflare returns variants as an object keyed by id, so this flattens it to a list
     *  and fills in each variant's own id from its key. */
    suspend fun listVariants(accountId: String): ApiResult<List<ImageVariant>> =
        when (val result = safeApiCall { api.listImageVariants(accountId) }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.variants.map { (id, variant) -> variant.copy(id = variant.id.ifBlank { id }) }
            )
            is ApiResult.Failure -> result
        }

    suspend fun createVariant(
        accountId: String,
        id: String,
        options: ImageVariantOptions,
        neverRequireSignedUrls: Boolean
    ): ApiResult<ImageVariant?> =
        when (val result = safeApiCall {
            api.createImageVariant(accountId, ImageVariantWrite(id, options, neverRequireSignedUrls))
        }) {
            is ApiResult.Success -> ApiResult.Success(result.data.variant)
            is ApiResult.Failure -> result
        }

    suspend fun deleteVariant(accountId: String, variantId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteImageVariant(accountId, variantId) }

    /** Names only - a key's value signs private delivery URLs and is never displayed. */
    suspend fun listSigningKeys(accountId: String): ApiResult<List<ImageSigningKey>> =
        when (val result = safeApiCall { api.listImageSigningKeys(accountId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.keys.map { it.copy(value = null) })
            is ApiResult.Failure -> result
        }
}
