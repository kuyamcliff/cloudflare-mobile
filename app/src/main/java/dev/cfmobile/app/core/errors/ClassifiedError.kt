package dev.cfmobile.app.core.errors

import dev.cfmobile.app.data.remote.ApiResult

/**
 * A failure, classified for UI purposes. [message] is Cloudflare's own error text whenever
 * [ApiResult.Failure] carried one (see SafeApiCall.kt) - this never invents a friendlier
 * message that could hide what Cloudflare actually said (PRD §35.1 wants Cloudflare's exact
 * terminology, not a generic "Something went wrong").
 */
data class ClassifiedError(
    val type: CfErrorType,
    val message: String,
    val httpCode: Int?,
    val recoveryActions: List<RecoveryAction>
)

object ErrorClassifier {

    private val PLAN_HINT = Regex("(\\bplan\\b|entitle|subscription|not available for|upgrade|enterprise)", RegexOption.IGNORE_CASE)

    /** What happened, in one line (spec 250). Cloudflare's own text stays in [ClassifiedError.message]. */
    fun headline(type: CfErrorType): String = when (type) {
        CfErrorType.UNAUTHORIZED -> "Cloudflare rejected this token."
        CfErrorType.FORBIDDEN -> "Cloudflare denied this request."
        CfErrorType.PLAN_RESTRICTED -> "Not available on this plan."
        CfErrorType.NOT_FOUND -> "Cloudflare could not find this resource."
        CfErrorType.VALIDATION -> "Cloudflare did not accept this request."
        CfErrorType.RATE_LIMITED -> "Cloudflare rate limit reached."
        CfErrorType.SERVER_ERROR -> "Cloudflare returned a server error."
        CfErrorType.NETWORK_FAILURE -> "Could not reach Cloudflare."
        CfErrorType.UNKNOWN -> "The request did not complete."
    }

    /** What it means and what to do now. */
    fun explanation(type: CfErrorType): String = when (type) {
        CfErrorType.UNAUTHORIZED -> "It may have been revoked, expired, or otherwise invalid. The token is still saved on this device until you remove it."
        CfErrorType.FORBIDDEN -> "This token may not have the permission this action needs, or it may not cover this account or zone."
        CfErrorType.PLAN_RESTRICTED -> "Cloudflare reports this feature is limited by the account's plan or entitlements. Changing token permissions will not change that."
        CfErrorType.NOT_FOUND -> "It may have been deleted, or it belongs to an account this token cannot see."
        CfErrorType.VALIDATION -> "Check the values and try again. Cloudflare's reason is shown below."
        CfErrorType.RATE_LIMITED -> "The API has temporarily limited requests from this token. The app waits for the delay Cloudflare asks for before retrying."
        CfErrorType.SERVER_ERROR -> "This is a problem on Cloudflare's side, not with your configuration. Try again shortly."
        CfErrorType.NETWORK_FAILURE -> "Check the connection. Cached data is shown where available."
        CfErrorType.UNKNOWN -> "Try again. If it keeps happening, the details below may help."
    }

    fun classify(failure: ApiResult.Failure): ClassifiedError {
        val code = failure.httpCode
        val message = failure.message
        val isNetworkFailure = code == null &&
            (message.startsWith("Network error", ignoreCase = true) || message.startsWith("Unable to reach", ignoreCase = true))

        val type = when {
            isNetworkFailure -> CfErrorType.NETWORK_FAILURE
            code == 401 -> CfErrorType.UNAUTHORIZED
            code == 403 && PLAN_HINT.containsMatchIn(message) -> CfErrorType.PLAN_RESTRICTED
            code == 403 -> CfErrorType.FORBIDDEN
            code == 404 -> CfErrorType.NOT_FOUND
            code == 429 -> CfErrorType.RATE_LIMITED
            code != null && code in 400..499 -> CfErrorType.VALIDATION
            code != null && code >= 500 -> CfErrorType.SERVER_ERROR
            else -> CfErrorType.UNKNOWN
        }

        val actions = when (type) {
            CfErrorType.UNAUTHORIZED -> listOf(RecoveryAction.REAUTHENTICATE, RecoveryAction.GO_BACK)
            CfErrorType.FORBIDDEN -> listOf(RecoveryAction.OPEN_TOKEN_PERMISSIONS, RecoveryAction.GO_BACK)
            CfErrorType.PLAN_RESTRICTED -> listOf(RecoveryAction.GO_BACK)
            CfErrorType.NOT_FOUND -> listOf(RecoveryAction.REFRESH, RecoveryAction.GO_BACK)
            CfErrorType.VALIDATION -> listOf(RecoveryAction.GO_BACK)
            CfErrorType.RATE_LIMITED -> listOf(RecoveryAction.CONTINUE_WITH_CACHED)
            CfErrorType.SERVER_ERROR -> listOf(RecoveryAction.RETRY)
            CfErrorType.NETWORK_FAILURE -> listOf(RecoveryAction.RETRY, RecoveryAction.CONTINUE_WITH_CACHED)
            CfErrorType.UNKNOWN -> listOf(RecoveryAction.RETRY)
        }

        return ClassifiedError(type = type, message = message, httpCode = code, recoveryActions = actions)
    }
}
