package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AccountSubscription
import dev.cfmobile.app.data.remote.safeApiCall

/** Strictly read-only. This app shows what the account is subscribed to and hands off to the
 *  Cloudflare dashboard for any change - subscribing, upgrading, or touching payment details
 *  from a phone is exactly the kind of irreversible, money-moving action that belongs behind
 *  Cloudflare's own confirmation flow (PRD §115's external-platform rule). */
class BillingRepository(private val api: CloudflareApi) {

    suspend fun listSubscriptions(accountId: String): ApiResult<List<AccountSubscription>> =
        safeApiCall { api.listSubscriptions(accountId) }
}
