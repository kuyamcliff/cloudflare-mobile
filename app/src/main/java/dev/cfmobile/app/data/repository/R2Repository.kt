package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.R2CorsRule
import dev.cfmobile.app.data.remote.dto.R2CustomDomain
import dev.cfmobile.app.data.remote.dto.R2LifecycleRule
import dev.cfmobile.app.data.remote.dto.R2ManagedDomain
import dev.cfmobile.app.data.remote.dto.R2ManagedDomainWrite
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

    suspend fun getCors(accountId: String, bucketName: String): ApiResult<List<R2CorsRule>> =
        when (val result = safeApiCall { api.getR2Cors(accountId, bucketName) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.rules)
            // A bucket with no CORS policy 404s rather than returning an empty list.
            is ApiResult.Failure -> if (result.httpCode == 404) ApiResult.Success(emptyList()) else result
        }

    suspend fun clearCors(accountId: String, bucketName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteR2Cors(accountId, bucketName) }

    suspend fun getLifecycle(accountId: String, bucketName: String): ApiResult<List<R2LifecycleRule>> =
        when (val result = safeApiCall { api.getR2Lifecycle(accountId, bucketName) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.rules)
            is ApiResult.Failure -> if (result.httpCode == 404) ApiResult.Success(emptyList()) else result
        }

    suspend fun listCustomDomains(accountId: String, bucketName: String): ApiResult<List<R2CustomDomain>> =
        when (val result = safeApiCall { api.listR2CustomDomains(accountId, bucketName) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.domains)
            is ApiResult.Failure -> result
        }

    suspend fun deleteCustomDomain(accountId: String, bucketName: String, domain: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteR2CustomDomain(accountId, bucketName, domain) }

    suspend fun getManagedDomain(accountId: String, bucketName: String): ApiResult<R2ManagedDomain> =
        safeApiCall { api.getR2ManagedDomain(accountId, bucketName) }

    /** Turning this on publishes every object in the bucket on its r2.dev URL. */
    suspend fun setManagedDomain(accountId: String, bucketName: String, enabled: Boolean): ApiResult<R2ManagedDomain> =
        safeApiCall { api.updateR2ManagedDomain(accountId, bucketName, R2ManagedDomainWrite(enabled)) }
}
