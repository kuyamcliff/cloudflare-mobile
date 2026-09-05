package dev.cfmobile.app.data.remote

import okhttp3.mockwebserver.MockWebServer

fun testApi(server: MockWebServer, token: String? = "test-token"): CloudflareApi =
    NetworkModule.createApi(baseUrl = server.url("/").toString()) { token }

fun testVerifierApi(server: MockWebServer): CloudflareApi =
    NetworkModule.createVerifierApi(baseUrl = server.url("/").toString())
