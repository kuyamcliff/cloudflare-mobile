package dev.cfmobile.app.ui.ratelimit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RateLimitRuleWriteTest {

    @Test
    fun `validation requires an expression`() {
        assertThat(validateRateLimitForm(RateLimitRuleForm(expression = ""))).isEqualTo("Expression is required")
    }

    @Test
    fun `validation rejects a zero or negative requests-per-period`() {
        assertThat(validateRateLimitForm(RateLimitRuleForm(expression = "true", requestsPerPeriod = "0")))
            .isEqualTo("Requests per period must be a positive number")
        assertThat(validateRateLimitForm(RateLimitRuleForm(expression = "true", requestsPerPeriod = "not-a-number")))
            .isEqualTo("Requests per period must be a positive number")
    }

    @Test
    fun `validation rejects a non-numeric mitigation timeout but allows a blank one`() {
        assertThat(validateRateLimitForm(RateLimitRuleForm(expression = "true", mitigationTimeout = "abc")))
            .isEqualTo("Mitigation timeout must be a number")
        assertThat(validateRateLimitForm(RateLimitRuleForm(expression = "true", mitigationTimeout = ""))).isNull()
    }

    @Test
    fun `a valid form passes validation`() {
        assertThat(validateRateLimitForm(RateLimitRuleForm(expression = "true", requestsPerPeriod = "100"))).isNull()
    }

    @Test
    fun `write carries the characteristic, period, threshold, and optional mitigation timeout`() {
        val form = RateLimitRuleForm(
            expression = "http.request.uri.path eq \"/login\"",
            action = "challenge",
            period = 3600,
            requestsPerPeriod = "10",
            mitigationTimeout = "300"
        )

        val write = buildRateLimitRuleWrite(form)

        assertThat(write.action).isEqualTo("challenge")
        assertThat(write.ratelimit?.characteristics).containsExactly("ip.src")
        assertThat(write.ratelimit?.period).isEqualTo(3600)
        assertThat(write.ratelimit?.requestsPerPeriod).isEqualTo(10)
        assertThat(write.ratelimit?.mitigationTimeout).isEqualTo(300)
    }

    @Test
    fun `a blank mitigation timeout is omitted rather than defaulted`() {
        val write = buildRateLimitRuleWrite(RateLimitRuleForm(expression = "true", mitigationTimeout = ""))
        assertThat(write.ratelimit?.mitigationTimeout).isNull()
    }
}
