package dev.cfmobile.app.core.errors

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import org.junit.Test

class ErrorClassifierTest {

    @Test
    fun `401 classifies as unauthorized with reauthenticate recovery`() {
        val result = ErrorClassifier.classify(ApiResult.Failure("Invalid API Token", 401))

        assertThat(result.type).isEqualTo(CfErrorType.UNAUTHORIZED)
        assertThat(result.recoveryActions).contains(RecoveryAction.REAUTHENTICATE)
        assertThat(result.message).isEqualTo("Invalid API Token")
    }

    @Test
    fun `403 classifies as forbidden and points at token permissions`() {
        val result = ErrorClassifier.classify(ApiResult.Failure("You do not have permission", 403))

        assertThat(result.type).isEqualTo(CfErrorType.FORBIDDEN)
        assertThat(result.recoveryActions).contains(RecoveryAction.OPEN_TOKEN_PERMISSIONS)
    }

    @Test
    fun `404 classifies as not found`() {
        val result = ErrorClassifier.classify(ApiResult.Failure("Zone not found", 404))
        assertThat(result.type).isEqualTo(CfErrorType.NOT_FOUND)
    }

    @Test
    fun `429 classifies as rate limited and never suggests blind retry`() {
        val result = ErrorClassifier.classify(ApiResult.Failure("Rate limited", 429))

        assertThat(result.type).isEqualTo(CfErrorType.RATE_LIMITED)
        assertThat(result.recoveryActions).doesNotContain(RecoveryAction.RETRY)
    }

    @Test
    fun `4xx other than 401,403,404,429 classifies as validation with no retry`() {
        val result = ErrorClassifier.classify(ApiResult.Failure("Invalid DNS record content", 400))

        assertThat(result.type).isEqualTo(CfErrorType.VALIDATION)
        assertThat(result.recoveryActions).doesNotContain(RecoveryAction.RETRY)
    }

    @Test
    fun `5xx classifies as server error and is retryable`() {
        val result = ErrorClassifier.classify(ApiResult.Failure("Internal Server Error", 500))

        assertThat(result.type).isEqualTo(CfErrorType.SERVER_ERROR)
        assertThat(result.recoveryActions).contains(RecoveryAction.RETRY)
    }

    @Test
    fun `network failure with no http code classifies as network failure`() {
        val result = ErrorClassifier.classify(ApiResult.Failure("Unable to reach Cloudflare"))

        assertThat(result.type).isEqualTo(CfErrorType.NETWORK_FAILURE)
        assertThat(result.recoveryActions).contains(RecoveryAction.RETRY)
    }

    @Test
    fun `unrecognized shape falls back to unknown but still offers retry`() {
        val result = ErrorClassifier.classify(ApiResult.Failure("Something odd happened"))

        assertThat(result.type).isEqualTo(CfErrorType.UNKNOWN)
        assertThat(result.recoveryActions).contains(RecoveryAction.RETRY)
    }
}
