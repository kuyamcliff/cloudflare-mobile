package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.DnsBatchDeleteRef
import dev.cfmobile.app.data.remote.dto.DnsBatchRequest
import dev.cfmobile.app.data.remote.dto.DnsBatchResult
import dev.cfmobile.app.data.remote.dto.DnsImportResult
import dev.cfmobile.app.data.remote.dto.DnsRecord
import dev.cfmobile.app.data.remote.dto.DnsRecordWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class DnsRepository(private val api: CloudflareApi) {
    suspend fun listRecords(zoneId: String): ApiResult<List<DnsRecord>> =
        safeApiCall { api.listDnsRecords(zoneId) }

    suspend fun createRecord(zoneId: String, record: DnsRecordWrite): ApiResult<DnsRecord> =
        safeApiCall { api.createDnsRecord(zoneId, record) }

    suspend fun updateRecord(zoneId: String, recordId: String, record: DnsRecordWrite): ApiResult<DnsRecord> =
        safeApiCall { api.updateDnsRecord(zoneId, recordId, record) }

    suspend fun deleteRecord(zoneId: String, recordId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteDnsRecord(zoneId, recordId) }

    /** One request instead of N sequential deletes, so removing a batch of records is a single
     *  atomic-ish operation from the user's point of view (PRD §9: batch operations). */
    suspend fun batchDeleteRecords(zoneId: String, recordIds: List<String>): ApiResult<DnsBatchResult> =
        safeApiCall { api.batchDnsRecords(zoneId, DnsBatchRequest(deletes = recordIds.map { DnsBatchDeleteRef(it) })) }

    suspend fun exportZoneFile(zoneId: String): ApiResult<String> = try {
        val response = api.exportDnsRecords(zoneId)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body.string())
        } else {
            ApiResult.Failure("Cloudflare couldn't export this zone's DNS records (HTTP ${response.code()})", response.code())
        }
    } catch (e: IOException) {
        ApiResult.Failure(e.message?.let { "Network error: $it" } ?: "Unable to reach Cloudflare")
    }

    suspend fun importZoneFile(zoneId: String, bindFileContent: String, proxied: Boolean): ApiResult<DnsImportResult> {
        val filePart = MultipartBody.Part.createFormData(
            "file", "import.txt", bindFileContent.toRequestBody("text/plain".toMediaTypeOrNull())
        )
        val proxiedPart = MultipartBody.Part.createFormData("proxied", proxied.toString())
        return safeApiCall { api.importDnsRecords(zoneId, filePart, proxiedPart) }
    }
}
