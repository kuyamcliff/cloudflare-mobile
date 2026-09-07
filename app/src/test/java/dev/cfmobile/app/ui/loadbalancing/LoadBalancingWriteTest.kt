package dev.cfmobile.app.ui.loadbalancing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoadBalancingWriteTest {

    @Test
    fun `pool validation requires a name`() {
        assertThat(validatePoolForm(PoolFormState(name = "", origins = listOf(OriginFormState(address = "1.1.1.1")))))
            .isEqualTo("Pool name is required")
    }

    @Test
    fun `pool validation requires at least one non-blank origin address`() {
        assertThat(validatePoolForm(PoolFormState(name = "primary", origins = listOf(OriginFormState(address = "")))))
            .isEqualTo("At least one origin address is required")
    }

    @Test
    fun `a valid pool form passes validation`() {
        assertThat(validatePoolForm(PoolFormState(name = "primary", origins = listOf(OriginFormState(address = "1.1.1.1"))))).isNull()
    }

    @Test
    fun `pool write drops blank origin rows and defaults unnamed origins`() {
        val form = PoolFormState(
            name = "primary",
            origins = listOf(OriginFormState(address = "1.1.1.1"), OriginFormState(address = ""), OriginFormState(name = "backup", address = "2.2.2.2"))
        )

        val write = buildPoolWrite(form)

        assertThat(write.name).isEqualTo("primary")
        assertThat(write.origins).hasSize(2)
        assertThat(write.origins[0].name).isEqualTo("origin-1")
        assertThat(write.origins[0].address).isEqualTo("1.1.1.1")
        assertThat(write.origins[1].name).isEqualTo("backup")
    }

    @Test
    fun `load balancer validation requires a hostname and a pool`() {
        assertThat(validateLbForm(LbFormState(hostname = "", poolId = "pool1"))).isEqualTo("Hostname is required")
        assertThat(validateLbForm(LbFormState(hostname = "www.example.com", poolId = null))).isEqualTo("Create a pool first")
        assertThat(validateLbForm(LbFormState(hostname = "www.example.com", poolId = "pool1"))).isNull()
    }

    @Test
    fun `load balancer write uses the same pool for default_pools and fallback_pool`() {
        val write = buildLoadBalancerWrite(LbFormState(hostname = "www.example.com", poolId = "pool1", proxied = false))

        assertThat(write.name).isEqualTo("www.example.com")
        assertThat(write.defaultPools).containsExactly("pool1")
        assertThat(write.fallbackPool).isEqualTo("pool1")
        assertThat(write.proxied).isFalse()
    }
}
