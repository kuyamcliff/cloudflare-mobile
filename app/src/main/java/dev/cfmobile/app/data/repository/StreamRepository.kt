package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.StreamVideo
import dev.cfmobile.app.data.remote.UploadPayload
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Video inventory plus uploading one chosen on the device. Cloudflare's basic upload takes
 *  the whole file in one request and caps it at 200 MB; the tus resumable protocol, which is
 *  what larger files need, isn't implemented. */
class StreamRepository(private val api: CloudflareApi) {

    suspend fun listVideos(accountId: String): ApiResult<List<StreamVideo>> =
        safeApiCall { api.listStreamVideos(accountId) }

    suspend fun deleteVideo(accountId: String, videoId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteStreamVideo(accountId, videoId) }

    suspend fun uploadVideo(accountId: String, payload: UploadPayload): ApiResult<StreamVideo> =
        safeApiCall { api.uploadStreamVideo(accountId, payload.toPart()) }
}
