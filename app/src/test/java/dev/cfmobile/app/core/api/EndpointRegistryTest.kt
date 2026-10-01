package dev.cfmobile.app.core.api

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.core.capabilities.CapabilityProbes
import dev.cfmobile.app.core.capabilities.CapabilityRegistry
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Runs against the real generated asset, so a regenerated registry that breaks the app's
 *  assumptions fails here before it ships. */
class EndpointRegistryTest {
    companion object {
        lateinit var registry: EndpointRegistry

        @BeforeClass @JvmStatic fun load() {
            val asset = listOf("src/main/assets", "app/src/main/assets").map { File(it, EndpointRegistry.ASSET_NAME) }.first { it.exists() }
            registry = asset.inputStream().use { EndpointRegistry.load(it) }
        }
    }

    @Test fun `asset name survives Android asset packaging`() {
        // AAPT strips a ".gz" suffix and stores the file decompressed under the shorter name,
        // so a ".gz" asset would not be found at runtime in the packaged APK.
        assertThat(EndpointRegistry.ASSET_NAME).doesNotContain(".gz")
    }

    @Test fun `shipped registry contains no credential-shaped examples`() {
        val text = listOf("src/main/assets", "app/src/main/assets").map { File(it, EndpointRegistry.ASSET_NAME) }.first { it.exists() }
            .inputStream().use { java.util.zip.GZIPInputStream(it).readBytes().toString(Charsets.UTF_8) }
        assertThat(text).doesNotContainMatch("PRIVATE KEY-----\\\\n[A-Za-z0-9+/]{20}")
    }

    @Test fun `registry is populated with metadata`() {
        assertThat(registry.endpoints.size).isGreaterThan(3000)
        assertThat(registry.schemaRevision).isNotEmpty()
        assertThat(registry.endpoints.count { it.native }).isGreaterThan(200)
    }

    @Test fun `matches concrete paths to operations`() {
        val e = registry.match("GET", "zones/023e105f4ecef8ad9ca31a8372d0c353/dns_records")
        assertThat(e).isNotNull()
        assertThat(e!!.permissions).contains("DNS Read")
        assertThat(e.native).isTrue()
        assertThat(registry.match("DELETE", "zones/abc/dns_records/def")?.isDestructive).isTrue()
        assertThat(registry.match("GET", "not/a/real/path/at/all")).isNull()
    }

    @Test fun `parameters and body examples are available for the explorer`() {
        val list = registry.endpoints.first { it.method == "GET" && it.path == "zones/{zone_id}/dns_records" }
        assertThat(list.pathParams.map { it.name }).containsExactly("zone_id")
        assertThat(list.queryParams.map { it.name }).contains("type")
        val purge = registry.endpoints.first { it.method == "POST" && it.path == "zones/{zone_id}/purge_cache" }
        assertThat(purge.bodyExample).isNotNull()
    }

    @Test fun `every native capability binds to a schema operation`() {
        val ids = CapabilityRegistry.zoneCapabilities.map { it.id }
        val missing = ids.filter { id ->
            val probe = CapabilityProbes.byCapabilityId[id] ?: return@filter true
            registry.endpoints.none { it.method == probe.method && it.path == probe.path }
        }
        assertThat(missing).isEmpty()
    }

    @Test fun `search matches across path summary and permission`() {
        assertThat(registry.search("r2 buckets").map { it.path }).contains("accounts/{account_id}/r2/buckets")
        assertThat(registry.search("Workers R2 Storage Write")).isNotEmpty()
    }
}
