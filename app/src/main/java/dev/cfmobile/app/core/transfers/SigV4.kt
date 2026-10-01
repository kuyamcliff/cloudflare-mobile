package dev.cfmobile.app.core.transfers

import java.net.URLEncoder
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4, as R2's S3-compatible API requires (region `auto`, service `s3`).
 * Pure JVM code with no Android dependency, tested against AWS's published example.
 */
object SigV4 {
    const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
    private val AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    data class Signed(val headers: Map<String, String>)

    /**
     * Returns the headers to add (Authorization, x-amz-date, x-amz-content-sha256). [headers]
     * must include `host` and any other header that should be signed.
     */
    fun sign(
        method: String,
        canonicalPath: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        payloadHash: String,
        accessKeyId: String,
        secretAccessKey: String,
        region: String = "auto",
        service: String = "s3",
        now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
    ): Signed {
        val amzDate = now.format(AMZ_DATE)
        val date = now.format(DATE)
        val all = (headers + mapOf("x-amz-date" to amzDate, "x-amz-content-sha256" to payloadHash))
            .mapKeys { it.key.lowercase() }
            .toSortedMap()
        val canonicalHeaders = all.entries.joinToString("") { "${it.key}:${it.value.trim().replace(Regex("\\s+"), " ")}\n" }
        val signedHeaders = all.keys.joinToString(";")
        val canonicalQuery = query.entries
            .map { encode(it.key) to encode(it.value) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { "${it.first}=${it.second}" }
        val canonicalRequest = listOf(method, canonicalPath, canonicalQuery, canonicalHeaders, signedHeaders, payloadHash).joinToString("\n")
        val scope = "$date/$region/$service/aws4_request"
        val stringToSign = listOf("AWS4-HMAC-SHA256", amzDate, scope, sha256Hex(canonicalRequest.toByteArray())).joinToString("\n")
        val kDate = hmac("AWS4$secretAccessKey".toByteArray(), date)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, service)
        val kSigning = hmac(kService, "aws4_request")
        val signature = hmac(kSigning, stringToSign).toHex()
        val auth = "AWS4-HMAC-SHA256 Credential=$accessKeyId/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
        return Signed(mapOf("Authorization" to auth, "x-amz-date" to amzDate, "x-amz-content-sha256" to payloadHash))
    }

    /** S3 URI encoding: RFC 3986 unreserved characters stay, everything else is %XX. */
    fun encode(value: String, keepSlash: Boolean = false): String {
        val encoded = URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
        return if (keepSlash) encoded.replace("%2F", "/") else encoded
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data.toByteArray())

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
