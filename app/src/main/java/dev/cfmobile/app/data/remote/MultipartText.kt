package dev.cfmobile.app.data.remote

/**
 * Pulls the readable content out of a body Cloudflare returns as a file rather than as JSON.
 * A module Worker and a snippet both come back as multipart, where showing the raw body would
 * bury the code under MIME boundaries and headers; a body that isn't multipart is returned
 * unchanged.
 */
fun extractMultipartContent(body: String): String {
    val boundaryLine = body.lineSequence().firstOrNull()?.trim().orEmpty()
    if (!boundaryLine.startsWith("--")) return body
    return body.split(boundaryLine)
        .mapNotNull { part ->
            // Each part is headers, a blank line, then content.
            val separator = part.indexOf("\n\n").takeIf { it >= 0 }
                ?: part.indexOf("\r\n\r\n").takeIf { it >= 0 }
                ?: return@mapNotNull null
            part.substring(separator).trim().takeIf { it.isNotBlank() && it != "--" }
        }
        .joinToString("\n\n")
        .ifBlank { body }
}
