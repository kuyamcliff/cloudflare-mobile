package dev.cfmobile.app.ui.navigation

import android.net.Uri

/**
 * Maps `cloudflarecontrol://` links to in-app routes (spec 118, 232). Links only navigate:
 * they never carry credentials, never change a profile, and every identifier is checked
 * against Cloudflare's 32-hex-character format before it reaches a route.
 */
object DeepLinks {
    const val SCHEME = "cloudflarecontrol"
    private val ID = Regex("^[a-f0-9]{32}$")
    private val BUCKET = Regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$")
    private val SCRIPT = Regex("^[A-Za-z0-9_-]{1,63}$")

    fun route(uri: Uri?): String? {
        if (uri == null || uri.scheme != SCHEME) return null
        val segments = uri.pathSegments
        return when (uri.host) {
            "search" -> Routes.SEARCH
            "explorer" -> Routes.explorer()
            "transfers" -> Routes.TRANSFERS
            "graphql" -> Routes.GRAPHQL
            "zone" -> segments.getOrNull(0)?.takeIf(ID::matches)?.let { Routes.zoneMenu(it, it) }
            "r2" -> {
                val account = segments.getOrNull(0)?.takeIf(ID::matches)
                val bucket = segments.getOrNull(1)?.takeIf(BUCKET::matches)
                when {
                    account != null && bucket != null -> Routes.r2Objects(account, bucket, null)
                    account != null -> Routes.r2(account)
                    else -> null
                }
            }
            "worker" -> {
                val account = segments.getOrNull(0)?.takeIf(ID::matches)
                if (account != null && segments.getOrNull(1)?.let(SCRIPT::matches) != false) Routes.workers(account) else null
            }
            else -> null
        }
    }
}
