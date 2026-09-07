package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.QueueConsumer
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

    /** The Workers pulling from this queue, with the batching settings that shape how they're
     *  called. Adding a consumer is part of a Worker's own configuration, not something this
     *  app can write. */
    suspend fun listConsumers(accountId: String, queueId: String): ApiResult<List<QueueConsumer>> =
        safeApiCall { api.listQueueConsumers(accountId, queueId) }

    suspend fun deleteConsumer(accountId: String, queueId: String, consumerId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteQueueConsumer(accountId, queueId, consumerId) }
}
