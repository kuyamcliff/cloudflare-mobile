package dev.cfmobile.app.ui.navigation

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36])
class DeepLinksTest {
    private val acct = "0123456789abcdef0123456789abcdef"

    @Test fun `valid links map to routes`() {
        assertThat(DeepLinks.route(Uri.parse("cloudflarecontrol://zone/$acct"))).isEqualTo(Routes.zoneMenu(acct, acct))
        assertThat(DeepLinks.route(Uri.parse("cloudflarecontrol://r2/$acct/media-assets"))).isEqualTo(Routes.r2Objects(acct, "media-assets", null))
        assertThat(DeepLinks.route(Uri.parse("cloudflarecontrol://worker/$acct/api-worker"))).isEqualTo(Routes.workers(acct))
        assertThat(DeepLinks.route(Uri.parse("cloudflarecontrol://search"))).isEqualTo(Routes.SEARCH)
    }

    @Test fun `malformed identifiers and other schemes are rejected`() {
        assertThat(DeepLinks.route(Uri.parse("cloudflarecontrol://zone/../../etc"))).isNull()
        assertThat(DeepLinks.route(Uri.parse("cloudflarecontrol://zone/abc"))).isNull()
        assertThat(DeepLinks.route(Uri.parse("cloudflarecontrol://r2/$acct/UPPER_case"))).isEqualTo(Routes.r2(acct))
        assertThat(DeepLinks.route(Uri.parse("https://evil.example/zone/$acct"))).isNull()
        assertThat(DeepLinks.route(Uri.parse("cloudflarecontrol://token?value=secret"))).isNull()
    }
}
