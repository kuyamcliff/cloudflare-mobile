package dev.cfmobile.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the currently-active API token to every request. The token never leaves the
 *  device except as this header sent straight to api.cloudflare.com. */
class AuthInterceptor(private val activeTokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = activeTokenProvider()
        val request = chain.request().let { original ->
            if (token.isNullOrBlank()) {
                original
            } else {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
        }
        return chain.proceed(request)
    }
}
