package dev.cfmobile.app.data.remote

import okhttp3.mockwebserver.MockWebServer

/** Retries are disabled here so each test sees exactly the response it enqueued; the retry
 *  policy itself is covered by RetryPolicyInterceptorTest. */

fun testApi(server: MockWebServer, token: String? = "test-token"): CloudflareApi =
    NetworkModule.createApi(baseUrl = server.url("/").toString(), maxRetries = 0) { token }

fun testVerifierApi(server: MockWebServer): CloudflareApi =
    NetworkModule.createVerifierApi(baseUrl = server.url("/").toString(), maxRetries = 0)
