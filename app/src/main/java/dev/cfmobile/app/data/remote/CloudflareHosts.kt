package dev.cfmobile.app.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The only hosts this app ever sends a credential to. The Cloudflare API token is bound to
 * [apiBase]'s host; R2 S3 credentials are bound to `<account>.r2.cloudflarestorage.com`
 * (optionally with a jurisdiction label such as `eu`). Anything else gets no credential at
 * all, whatever a caller asks for, so a typo in the API Explorer or a crafted deep link can
 * never forward a token to a third party.
 */
class CloudflareHosts(apiBaseUrl: String = NetworkModule.BASE_URL) {

    val apiBase: HttpUrl = requireNotNull(apiBaseUrl.toHttpUrlOrNull()) { "Invalid API base URL" }

    /** True only for the exact scheme, host and port of the configured API base. Tests point
     *  [apiBase] at a local MockWebServer; production is always https://api.cloudflare.com. */
    fun isApiHost(url: HttpUrl): Boolean =
        url.scheme == apiBase.scheme && url.host.equals(apiBase.host, ignoreCase = true) && url.port == apiBase.port

    companion object {
        private val R2_HOST = Regex("^[a-f0-9]{32}(\\.(eu|fedramp))?\\.r2\\.cloudflarestorage\\.com$")

        fun isR2S3Host(url: HttpUrl): Boolean = url.isHttps && R2_HOST.matches(url.host.lowercase())
    }
}
