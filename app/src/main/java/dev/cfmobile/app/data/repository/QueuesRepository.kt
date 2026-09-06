package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfQueue
import dev.cfmobile.app.data.remote.dto.QueueCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Queue management (list/create/delete). Producing or consuming messages happens in a Worker,
 *  not from a phone, so there's no message browser here. */
class QueuesRepository(private val api: CloudflareApi) {

    suspend fun listQueues(accountId: String): ApiResult<List<CfQueue>> =
        safeApiCall { api.listQueues(accountId) }

    suspend fun createQueue(accountId: String, name: String): ApiResult<CfQueue> =
        safeApiCall { api.createQueue(accountId, QueueCreate(queueName = name)) }

    suspend fun deleteQueue(accountId: String, queueId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteQueue(accountId, queueId) }
}
