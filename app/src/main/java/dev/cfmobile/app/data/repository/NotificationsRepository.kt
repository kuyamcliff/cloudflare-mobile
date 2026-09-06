package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.NotificationPolicy
import dev.cfmobile.app.data.remote.dto.NotificationPolicyUpdate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Alerting policies: read them, silence or re-enable one, or delete it. Creating a policy
 *  isn't implemented - see CapabilityRegistry's migrationHint. */
class NotificationsRepository(private val api: CloudflareApi) {

    suspend fun listPolicies(accountId: String): ApiResult<List<NotificationPolicy>> =
        safeApiCall { api.listNotificationPolicies(accountId) }

    /** Cloudflare merges the PATCH into the stored policy, so sending only `enabled` leaves
     *  the alert type and its destinations untouched. */
    suspend fun setEnabled(accountId: String, policyId: String, enabled: Boolean): ApiResult<NotificationPolicy> =
        safeApiCall { api.updateNotificationPolicy(accountId, policyId, NotificationPolicyUpdate(enabled)) }

    suspend fun deletePolicy(accountId: String, policyId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteNotificationPolicy(accountId, policyId) }
}
