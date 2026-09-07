package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.StreamCaption
import dev.cfmobile.app.data.remote.dto.StreamLiveInput
import dev.cfmobile.app.data.remote.dto.StreamLiveInputWrite
import dev.cfmobile.app.data.remote.dto.StreamRecording
import dev.cfmobile.app.data.remote.dto.StreamSigningKey
import dev.cfmobile.app.data.remote.dto.StreamVideo
import dev.cfmobile.app.data.remote.dto.StreamWatermark
import dev.cfmobile.app.data.remote.UploadPayload
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/**
 * Stream: video inventory plus uploading one chosen on the device, and the pieces around a
 * video - live inputs, watermark profiles, caption tracks, and the signing keys behind private
 * playback. Cloudflare's basic upload takes the whole file in one request and caps it at
 * 200 MB; the tus resumable protocol, which is what larger files need, isn't implemented.
 *
 * A live input's stream key is a push credential. It is read only from the single-input call
 * that the detail sheet makes, never held in the list, and never persisted.
 */
class StreamRepository(private val api: CloudflareApi) {

    suspend fun listVideos(accountId: String): ApiResult<List<StreamVideo>> =
        safeApiCall { api.listStreamVideos(accountId) }

    suspend fun deleteVideo(accountId: String, videoId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteStreamVideo(accountId, videoId) }

    suspend fun uploadVideo(accountId: String, payload: UploadPayload): ApiResult<StreamVideo> =
        safeApiCall { api.uploadStreamVideo(accountId, payload.toPart()) }

    /** The list omits the stream keys; only [getLiveInput] carries them. */
    suspend fun listLiveInputs(accountId: String): ApiResult<List<StreamLiveInput>> =
        when (val result = safeApiCall { api.listStreamLiveInputs(accountId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.liveInputs)
            is ApiResult.Failure -> result
        }

    suspend fun getLiveInput(accountId: String, inputId: String): ApiResult<StreamLiveInput> =
        safeApiCall { api.getStreamLiveInput(accountId, inputId) }

    suspend fun createLiveInput(accountId: String, name: String, record: Boolean): ApiResult<StreamLiveInput> =
        safeApiCall {
            api.createStreamLiveInput(
                accountId,
                StreamLiveInputWrite(
                    meta = mapOf("name" to name),
                    recording = StreamRecording(mode = if (record) "automatic" else "off")
                )
            )
        }

    suspend fun deleteLiveInput(accountId: String, inputId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteStreamLiveInput(accountId, inputId) }

    suspend fun listWatermarks(accountId: String): ApiResult<List<StreamWatermark>> =
        safeApiCall { api.listStreamWatermarks(accountId) }

    suspend fun deleteWatermark(accountId: String, watermarkId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteStreamWatermark(accountId, watermarkId) }

    suspend fun listCaptions(accountId: String, videoId: String): ApiResult<List<StreamCaption>> =
        safeApiCall { api.listStreamCaptions(accountId, videoId) }

    suspend fun deleteCaption(accountId: String, videoId: String, language: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteStreamCaption(accountId, videoId, language) }

    /** Metadata only: the private half of a signing key exists solely in the create response,
     *  and this app doesn't make that call. */
    suspend fun listSigningKeys(accountId: String): ApiResult<List<StreamSigningKey>> =
        safeApiCall { api.listStreamSigningKeys(accountId) }

    suspend fun deleteSigningKey(accountId: String, keyId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteStreamSigningKey(accountId, keyId) }
}
