package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.DnsRecord
import dev.cfmobile.app.data.remote.dto.DnsRecordWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

class DnsRepository(private val api: CloudflareApi) {
    suspend fun listRecords(zoneId: String): ApiResult<List<DnsRecord>> =
        safeApiCall { api.listDnsRecords(zoneId) }

    suspend fun createRecord(zoneId: String, record: DnsRecordWrite): ApiResult<DnsRecord> =
        safeApiCall { api.createDnsRecord(zoneId, record) }

    suspend fun updateRecord(zoneId: String, recordId: String, record: DnsRecordWrite): ApiResult<DnsRecord> =
        safeApiCall { api.updateDnsRecord(zoneId, recordId, record) }

    suspend fun deleteRecord(zoneId: String, recordId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteDnsRecord(zoneId, recordId) }
}
