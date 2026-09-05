package dev.cfmobile.app.ui.dns

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DnsRecordWriteTest {

    @Test
    fun `validation requires a name for every type`() {
        val form = DnsFormState(type = "A", name = "", content = "203.0.113.1")
        assertThat(validateDnsForm(form)).isEqualTo("Name is required")
    }

    @Test
    fun `A record requires content`() {
        val form = DnsFormState(type = "A", name = "www", content = "")
        assertThat(validateDnsForm(form)).isEqualTo("Content is required")
    }

    @Test
    fun `SRV record requires a target and a numeric port`() {
        val missingTarget = DnsFormState(type = "SRV", name = "_sip._tcp", srvTarget = "", srvPort = "5060")
        assertThat(validateDnsForm(missingTarget)).isEqualTo("Target is required")

        val badPort = DnsFormState(type = "SRV", name = "_sip._tcp", srvTarget = "sip.example.com", srvPort = "not-a-number")
        assertThat(validateDnsForm(badPort)).isEqualTo("Port must be a number")

        val valid = DnsFormState(type = "SRV", name = "_sip._tcp", srvTarget = "sip.example.com", srvPort = "5060")
        assertThat(validateDnsForm(valid)).isNull()
    }

    @Test
    fun `URI TLSA SSHFP and CERT each require their defining field`() {
        assertThat(validateDnsForm(DnsFormState(type = "URI", name = "@", uriTarget = ""))).isEqualTo("Target URI is required")
        assertThat(validateDnsForm(DnsFormState(type = "TLSA", name = "_443._tcp", tlsaCertificate = ""))).isEqualTo("Certificate association data is required")
        assertThat(validateDnsForm(DnsFormState(type = "SSHFP", name = "@", sshfpFingerprint = ""))).isEqualTo("Fingerprint is required")
        assertThat(validateDnsForm(DnsFormState(type = "CERT", name = "@", certCertificate = ""))).isEqualTo("Certificate data is required")
    }

    @Test
    fun `NAPTR requires at least one of service, regex, or replacement`() {
        val blank = DnsFormState(type = "NAPTR", name = "@", naptrService = "", naptrRegex = "", naptrReplacement = "")
        assertThat(validateDnsForm(blank)).isEqualTo("Service, regex, or replacement is required")

        val withService = blank.copy(naptrService = "SIP+D2U")
        assertThat(validateDnsForm(withService)).isNull()
    }

    @Test
    fun `A record write uses content and proxied, no data object`() {
        val write = buildDnsRecordWrite(DnsFormState(type = "A", name = "www", content = "203.0.113.1", proxied = true), ttl = 1)
        assertThat(write.content).isEqualTo("203.0.113.1")
        assertThat(write.proxied).isTrue()
        assertThat(write.data).isNull()
    }

    @Test
    fun `CNAME is proxiable but TXT is not`() {
        val cname = buildDnsRecordWrite(DnsFormState(type = "CNAME", name = "www", content = "target.example.com", proxied = true), ttl = 1)
        assertThat(cname.proxied).isTrue()

        val txt = buildDnsRecordWrite(DnsFormState(type = "TXT", name = "@", content = "v=spf1 -all", proxied = true), ttl = 1)
        assertThat(txt.proxied).isNull()
    }

    @Test
    fun `MX record write carries top-level priority`() {
        val write = buildDnsRecordWrite(DnsFormState(type = "MX", name = "@", content = "mail.example.com", priority = "20"), ttl = 3600)
        assertThat(write.priority).isEqualTo(20)
        assertThat(write.content).isEqualTo("mail.example.com")
    }

    @Test
    fun `SRV record write puts priority weight port and target inside data, empty content`() {
        val write = buildDnsRecordWrite(
            DnsFormState(type = "SRV", name = "_sip._tcp", srvPriority = "10", srvWeight = "5", srvPort = "5060", srvTarget = "sip.example.com"),
            ttl = 1
        )
        assertThat(write.content).isEmpty()
        assertThat(write.data?.priority).isEqualTo(10)
        assertThat(write.data?.weight).isEqualTo(5)
        assertThat(write.data?.port).isEqualTo(5060)
        assertThat(write.data?.target).isEqualTo("sip.example.com")
    }

    @Test
    fun `URI record write keeps priority top-level and target plus weight inside data`() {
        val write = buildDnsRecordWrite(
            DnsFormState(type = "URI", name = "@", priority = "1", uriWeight = "2", uriTarget = "https://example.com/"),
            ttl = 1
        )
        assertThat(write.priority).isEqualTo(1)
        assertThat(write.data?.content).isEqualTo("https://example.com/")
        assertThat(write.data?.weight).isEqualTo(2)
    }

    @Test
    fun `TLSA record write maps usage selector matching type and certificate into data`() {
        val write = buildDnsRecordWrite(
            DnsFormState(type = "TLSA", name = "_443._tcp", tlsaUsage = "3", tlsaSelector = "1", tlsaMatchingType = "1", tlsaCertificate = "abc123"),
            ttl = 1
        )
        assertThat(write.data?.usage).isEqualTo(3)
        assertThat(write.data?.selector).isEqualTo(1)
        assertThat(write.data?.matchingType).isEqualTo(1)
        assertThat(write.data?.certificate).isEqualTo("abc123")
    }

    @Test
    fun `NAPTR record write maps every field into data`() {
        val write = buildDnsRecordWrite(
            DnsFormState(
                type = "NAPTR", name = "@", naptrOrder = "100", naptrPreference = "10",
                naptrFlags = "U", naptrService = "SIP+D2U", naptrRegex = "!^.*$!sip:info@example.com!", naptrReplacement = "."
            ),
            ttl = 1
        )
        assertThat(write.data?.order).isEqualTo(100)
        assertThat(write.data?.preference).isEqualTo(10)
        assertThat(write.data?.flags).isEqualTo("U")
        assertThat(write.data?.service).isEqualTo("SIP+D2U")
        assertThat(write.data?.replacement).isEqualTo(".")
    }

    @Test
    fun `SSHFP record write maps algorithm type and fingerprint into data`() {
        val write = buildDnsRecordWrite(
            DnsFormState(type = "SSHFP", name = "@", sshfpAlgorithm = "4", sshfpType = "2", sshfpFingerprint = "deadbeef"),
            ttl = 1
        )
        assertThat(write.data?.algorithm).isEqualTo(4)
        assertThat(write.data?.type).isEqualTo(2)
        assertThat(write.data?.fingerprint).isEqualTo("deadbeef")
    }

    @Test
    fun `CERT record write maps algorithm key tag type and certificate into data`() {
        val write = buildDnsRecordWrite(
            DnsFormState(type = "CERT", name = "@", certAlgorithm = "5", certKeyTag = "12345", certType = "1", certCertificate = "base64=="),
            ttl = 1
        )
        assertThat(write.data?.algorithm).isEqualTo(5)
        assertThat(write.data?.keyTag).isEqualTo(12345)
        assertThat(write.data?.type).isEqualTo(1)
        assertThat(write.data?.certificate).isEqualTo("base64==")
    }
}
