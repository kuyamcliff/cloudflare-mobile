package dev.cfmobile.app.core.errors

/**
 * The error taxonomy every screen classifies failures into (PRD §35). This is deliberately
 * coarser than Cloudflare's own numeric error codes - it exists to pick the right recovery
 * UI (retry vs re-authenticate vs "go back"), not to re-explain what Cloudflare already said.
 */
enum class CfErrorType {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    VALIDATION,
    RATE_LIMITED,
    SERVER_ERROR,
    NETWORK_FAILURE,
    UNKNOWN
}

/** What a screen can offer the user in response to a given [CfErrorType]. */
enum class RecoveryAction {
    RETRY,
    REFRESH,
    REAUTHENTICATE,
    OPEN_TOKEN_PERMISSIONS,
    GO_BACK,
    CONTINUE_WITH_CACHED
}
