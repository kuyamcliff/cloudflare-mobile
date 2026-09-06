package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.WaitingRoom
import dev.cfmobile.app.data.remote.dto.WaitingRoomCreate
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
}
