package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.TraceRequest
import dev.cfmobile.app.data.remote.dto.TraceResult
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * The request tracer: Cloudflare replays a request against its own pipeline and reports which
 * rules matched, in order. Nothing is sent to the origin, so a trace is safe to run against
 * production - it answers "which of my rules caught this?" without making the request happen.
 */
class DiagnosticsRepository(private val api: CloudflareApi) {

    suspend fun trace(accountId: String, request: TraceRequest): ApiResult<TraceResult> =
        safeApiCall { api.traceRequest(accountId, request) }
}
