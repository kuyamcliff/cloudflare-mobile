package dev.cfmobile.app.core.api

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CurlCodecTest {
    @Test fun `import strips credentials and keeps method path query and body`() {
        val parsed = CurlCodec.parse(
            """curl -X POST "https://api.cloudflare.com/client/v4/zones/abc/dns_records?proxied=true" \
              -H "Authorization: Bearer REALTOKEN" \
              -H 'X-Auth-Key: legacy' \
              -H "Content-Type: application/json" \
              --data '{"type":"A","name":"www","content":"203.0.113.10"}'"""
        )
        assertThat(parsed.request.method).isEqualTo("POST")
        assertThat(parsed.request.path).isEqualTo("zones/abc/dns_records")
        assertThat(parsed.request.query).containsExactly("proxied" to "true")
        assertThat(parsed.request.body).isEqualTo("""{"type":"A","name":"www","content":"203.0.113.10"}""")
        assertThat(parsed.removedCredentialHeaders).containsExactly("Authorization", "X-Auth-Key")
        assertThat(parsed.request.toString()).doesNotContain("REALTOKEN")
    }

    @Test fun `import refuses other hosts`() {
        assertThrows(IllegalArgumentException::class.java) {
            CurlCodec.parse("curl https://evil.example/client/v4/zones -H 'Authorization: Bearer x'")
        }
    }

    @Test fun `export never contains a real token`() {
        val out = CurlCodec.export("https://api.cloudflare.com/client/v4/zones", "PATCH", listOf("Authorization" to "Bearer real"), """{"a":1}""")
        assertThat(out).contains("Bearer <REDACTED>")
        assertThat(out).doesNotContain("real\"")
        assertThat(out).contains("-X PATCH")
        assertThat(out).contains("--data '{\"a\":1}'")
    }

    @Test fun `export round-trips through import`() {
        val out = CurlCodec.export("https://api.cloudflare.com/client/v4/accounts/a1/r2/buckets?per_page=5", "GET", emptyList(), null)
        val parsed = CurlCodec.parse(out)
        assertThat(parsed.request.path).isEqualTo("accounts/a1/r2/buckets")
        assertThat(parsed.request.query).containsExactly("per_page" to "5")
    }
}
