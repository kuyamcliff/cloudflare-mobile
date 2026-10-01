package dev.cfmobile.app.core.transfers

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SigV4Test {
    /** AWS's documented "GET Object" example (S3 API reference, sig-v4-header-based-auth). */
    @Test fun `matches AWS published GET object signature`() {
        val signed = SigV4.sign(
            method = "GET",
            canonicalPath = "/test.txt",
            query = emptyMap(),
            headers = mapOf("host" to "examplebucket.s3.amazonaws.com", "range" to "bytes=0-9"),
            payloadHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            now = ZonedDateTime.of(2013, 5, 24, 0, 0, 0, 0, ZoneOffset.UTC)
        )
        assertThat(signed.headers["Authorization"]).isEqualTo(
            "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"
        )
    }

    @Test fun `encoding keeps unreserved characters and optionally slashes`() {
        assertThat(SigV4.encode("a b/c~d*e")).isEqualTo("a%20b%2Fc~d%2Ae")
        assertThat(SigV4.encode("photos/2026 trip/a+b.jpg", keepSlash = true)).isEqualTo("photos/2026%20trip/a%2Bb.jpg")
    }
}
