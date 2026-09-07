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

    fun classify(failure: ApiResult.Failure): ClassifiedError {
        val code = failure.httpCode
        val message = failure.message
        val isNetworkFailure = code == null &&
            (message.startsWith("Network error", ignoreCase = true) || message.startsWith("Unable to reach", ignoreCase = true))

        val type = when {
            isNetworkFailure -> CfErrorType.NETWORK_FAILURE
            code == 401 -> CfErrorType.UNAUTHORIZED
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
