package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.WaitingRoom
import dev.cfmobile.app.data.remote.dto.WaitingRoomCreate
import dev.cfmobile.app.data.remote.dto.WaitingRoomEvent
import dev.cfmobile.app.data.remote.dto.WaitingRoomEventWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Waiting rooms queue visitors when a page would otherwise overwhelm the origin. */
class WaitingRoomRepository(private val api: CloudflareApi) {

    suspend fun listRooms(zoneId: String): ApiResult<List<WaitingRoom>> =
        safeApiCall { api.listWaitingRooms(zoneId) }

    suspend fun createRoom(
        zoneId: String,
        name: String,
        host: String,
        path: String,
        newUsersPerMinute: Int,
        totalActiveUsers: Int
    ): ApiResult<WaitingRoom> = safeApiCall {
        api.createWaitingRoom(
            zoneId,
            WaitingRoomCreate(
                name = name,
                host = host,
                path = path,
                newUsersPerMinute = newUsersPerMinute,
                totalActiveUsers = totalActiveUsers
            )
        )
    }

    suspend fun deleteRoom(zoneId: String, roomId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteWaitingRoom(zoneId, roomId) }

    /** Events are scheduled windows that override a room's thresholds for a sale or a drop. */
    suspend fun listEvents(zoneId: String, roomId: String): ApiResult<List<WaitingRoomEvent>> =
        safeApiCall { api.listWaitingRoomEvents(zoneId, roomId) }

    suspend fun createEvent(zoneId: String, roomId: String, event: WaitingRoomEventWrite): ApiResult<WaitingRoomEvent> =
        safeApiCall { api.createWaitingRoomEvent(zoneId, roomId, event) }

    /** Cloudflare answers with the deleted event rather than an empty result. */
    suspend fun deleteEvent(zoneId: String, roomId: String, eventId: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteWaitingRoomEvent(zoneId, roomId, eventId) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
}
