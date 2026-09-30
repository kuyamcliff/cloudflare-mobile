package dev.cfmobile.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the active API token, and only to requests for the Cloudflare API host. A request
 * to any other host (a redirect, a mistyped explorer URL, a pre-signed R2 link) goes out
 * without an Authorization header, and any Authorization header a caller set itself is
 * stripped for non-API hosts too.
 */
class AuthInterceptor(
    private val activeTokenProvider: () -> String?,
    private val hosts: CloudflareHosts = CloudflareHosts()
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!hosts.isApiHost(original.url)) {
            return chain.proceed(original.newBuilder().removeHeader("Authorization").build())
        }
        // An explicit header wins: token verification during onboarding sends the candidate
        // token itself before it has been saved anywhere.
        if (original.header("Authorization") != null) return chain.proceed(original)
        val token = activeTokenProvider()
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}
