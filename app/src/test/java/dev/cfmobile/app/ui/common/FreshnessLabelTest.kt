package dev.cfmobile.app.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FreshnessLabelTest {

    @Test
    fun `just now for under 5 seconds`() {
        assertThat(formatFreshness(lastUpdatedAtMillis = 1_000, nowMillis = 1_000)).isEqualTo("Updated just now")
        assertThat(formatFreshness(lastUpdatedAtMillis = 1_000, nowMillis = 5_999)).isEqualTo("Updated just now")
    }

    @Test
    fun `seconds ago between 5 and 60 seconds`() {
        assertThat(formatFreshness(lastUpdatedAtMillis = 0, nowMillis = 45_000)).isEqualTo("Updated 45s ago")
    }

    @Test
    fun `minutes ago between 1 minute and 1 hour`() {
        assertThat(formatFreshness(lastUpdatedAtMillis = 0, nowMillis = 5 * 60_000)).isEqualTo("Updated 5m ago")
    }

    @Test
    fun `hours ago between 1 hour and 1 day`() {
        assertThat(formatFreshness(lastUpdatedAtMillis = 0, nowMillis = 3 * 3_600_000L)).isEqualTo("Updated 3h ago")
    }

    @Test
    fun `days ago beyond 1 day`() {
        assertThat(formatFreshness(lastUpdatedAtMillis = 0, nowMillis = 2 * 86_400_000L)).isEqualTo("Updated 2d ago")
    }

    @Test
    fun `clock skew never produces a negative age`() {
        assertThat(formatFreshness(lastUpdatedAtMillis = 10_000, nowMillis = 0)).isEqualTo("Updated just now")
    }
}
