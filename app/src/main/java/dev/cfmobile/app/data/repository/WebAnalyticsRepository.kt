package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.RumSite
import dev.cfmobile.app.data.remote.dto.RumSiteCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Web Analytics (RUM) sites: the beacon token and the JavaScript snippet that installs it. */
class WebAnalyticsRepository(private val api: CloudflareApi) {

    suspend fun listSites(accountId: String): ApiResult<List<RumSite>> =
        safeApiCall { api.listRumSites(accountId) }

    /** `autoInstall` lets Cloudflare inject the beacon for a proxied zone, so nothing has to be
     *  pasted into the site's HTML. */
    suspend fun createSite(accountId: String, host: String, autoInstall: Boolean): ApiResult<RumSite> =
        safeApiCall { api.createRumSite(accountId, RumSiteCreate(host = host, autoInstall = autoInstall)) }

    suspend fun deleteSite(accountId: String, siteTag: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteRumSite(accountId, siteTag) }
}
