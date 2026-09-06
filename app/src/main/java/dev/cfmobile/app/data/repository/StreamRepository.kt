package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.StreamVideo
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Video inventory: list, inspect, and delete. Uploading video from the device isn't
 *  implemented - that's a media picker plus a resumable upload, a separate surface. */
class StreamRepository(private val api: CloudflareApi) {

    suspend fun listVideos(accountId: String): ApiResult<List<StreamVideo>> =
        safeApiCall { api.listStreamVideos(accountId) }

    suspend fun deleteVideo(accountId: String, videoId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteStreamVideo(accountId, videoId) }
}
