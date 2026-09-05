package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.R2Bucket
import dev.cfmobile.app.data.remote.dto.R2BucketCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** PRD §9 R2: bucket management only (list/create/delete) - browsing or uploading objects
 *  inside a bucket is a separate, much larger surface (a file browser/uploader) and isn't
 *  implemented here. */
class R2Repository(private val api: CloudflareApi) {

    suspend fun listBuckets(accountId: String): ApiResult<List<R2Bucket>> =
        when (val result = safeApiCall { api.listR2Buckets(accountId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.buckets)
            is ApiResult.Failure -> result
        }

    suspend fun createBucket(accountId: String, name: String): ApiResult<R2Bucket> =
        safeApiCall { api.createR2Bucket(accountId, R2BucketCreate(name = name)) }

    suspend fun deleteBucket(accountId: String, bucketName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteR2Bucket(accountId, bucketName) }
}
