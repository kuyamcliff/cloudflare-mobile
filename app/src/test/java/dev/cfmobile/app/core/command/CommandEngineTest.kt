package dev.cfmobile.app.core.command

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.data.local.NamedRef
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Runs against the real shipped registry so recipe paths and ranking track the schema. */
class CommandEngineTest {
    companion object {
        lateinit var registry: EndpointRegistry
        lateinit var engine: CommandEngine

        @BeforeClass @JvmStatic fun load() {
            val asset = listOf("src/main/assets", "app/src/main/assets").map { File(it, EndpointRegistry.ASSET_NAME) }.first { it.exists() }
            registry = asset.inputStream().use { EndpointRegistry.load(it) }
            engine = CommandEngine(registry)
        }

        val zoneA = NamedRef("zone-a", "example.com", "acc-1")
        val zoneB = NamedRef("zone-b", "shop.dev", "acc-1")
        val ctx = CommandContext(account = NamedRef("acc-1", "Main"), zone = zoneA, zones = listOf(zoneA, zoneB))
    }

    private fun best(input: String, c: CommandContext = ctx) = engine.search(input, c).best

    private fun bestRun(input: String, c: CommandContext = ctx): CommandHit.Run {
        val hit = best(input, c)
        assertThat(hit).isInstanceOf(CommandHit.Run::class.java)
        return hit as CommandHit.Run
    }

    @Test fun `every recipe targets a real operation`() {
        val missing = Recipes.all.filter { engine.endpointFor(it.method, it.path) == null }.map { it.id }
        assertThat(missing).isEmpty()
        assertThat(Recipes.all.map { it.id }).containsNoDuplicates()
    }

    @Test fun `adds an A record from a sentence`() {
        val hit = bestRun("add A record www 192.0.2.10 on shop.dev")
        assertThat(hit.recipe?.id).isEqualTo("dns.create")
        assertThat(hit.draft.pathValues["zone_id"]).isEqualTo("zone-b")
        assertThat(hit.draft.body).containsAtLeast("type", "A", "name", "www.shop.dev", "content", "192.0.2.10")
    }

    @Test fun `article a is not mistaken for an A record`() {
        val hit = bestRun("add a cname record blog pointing to ghs.example.net")
        assertThat(hit.draft.body?.get("type")).isEqualTo("CNAME")
        assertThat(hit.draft.body?.get("name")).isEqualTo("blog.example.com")
    }

    @Test fun `quoted TXT content keeps its case and spaces`() {
        val hit = bestRun("create txt record _dmarc \"v=DMARC1; p=none\" proxied")
        assertThat(hit.draft.body?.get("content")).isEqualTo("v=DMARC1; p=none")
        assertThat(hit.draft.body?.get("name")).isEqualTo("_dmarc.example.com")
        assertThat(hit.draft.body?.get("proxied")).isEqualTo(true)
    }

    @Test fun `purge everything targets the named zone`() {
        val hit = bestRun("purge cache on shop.dev")
        assertThat(hit.recipe?.id).isEqualTo("cache.purge_all")
        assertThat(hit.draft.method).isEqualTo("POST")
        assertThat(hit.draft.pathValues["zone_id"]).isEqualTo("zone-b")
        assertThat(hit.draft.body).containsEntry("purge_everything", true)
    }

    @Test fun `purging a URL never purges everything`() {
        val hit = bestRun("purge https://example.com/app.js")
        assertThat(hit.recipe?.id).isEqualTo("cache.purge_urls")
        assertThat(hit.draft.body?.get("files")).isEqualTo(listOf("https://example.com/app.js"))
    }

    @Test fun `turn off dev mode reads off even with a trailing on`() {
        val hit = bestRun("turn off dev mode on example.com")
        assertThat(hit.recipe?.id).isEqualTo("setting.dev_mode")
        assertThat(hit.draft.pathValues["setting_id"]).isEqualTo("development_mode")
        assertThat(hit.draft.body).containsEntry("value", "off")
    }

    @Test fun `ssl strict sets the encryption mode`() {
        val hit = bestRun("set ssl to strict")
        assertThat(hit.draft.pathValues["setting_id"]).isEqualTo("ssl")
        assertThat(hit.draft.body).containsEntry("value", "strict")
    }

    @Test fun `a bare product name opens its screen`() {
        val hit = best("dns")
        assertThat(hit).isInstanceOf(CommandHit.Go::class.java)
        assertThat((hit as CommandHit.Go).place?.id).isEqualTo("dns.records")
        assertThat(hit.route).contains("zone-a")
    }

    @Test fun `naming another zone opens the screen there`() {
        val hit = best("open dns shop.dev") as CommandHit.Go
        assertThat(hit.route).contains("zone-b")
        assertThat(hit.selectZone).isEqualTo(zoneB)
    }

    @Test fun `a zone name alone opens that zone`() {
        val hit = best("shop.dev") as CommandHit.Go
        assertThat(hit.selectZone).isEqualTo(zoneB)
    }

    @Test fun `blocks an IP and a country with the right target`() {
        val ip = bestRun("block 203.0.113.7")
        assertThat(ip.recipe?.id).isEqualTo("firewall.access_rule")
        assertThat(Json.get(ip.draft.body, "configuration.target")).isEqualTo("ip")
        assertThat(Json.get(ip.draft.body, "configuration.value")).isEqualTo("203.0.113.7")
        assertThat(ip.draft.body?.get("mode")).isEqualTo("block")

        val country = bestRun("challenge visitors from russia")
        assertThat(Json.get(country.draft.body, "configuration.target")).isEqualTo("country")
        assertThat(Json.get(country.draft.body, "configuration.value")).isEqualTo("RU")
        assertThat(country.draft.body?.get("mode")).isEqualTo("managed_challenge")

        val range = bestRun("allow 198.51.100.0/24")
        assertThat(Json.get(range.draft.body, "configuration.target")).isEqualTo("ip_range")
        assertThat(range.draft.body?.get("mode")).isEqualTo("whitelist")
    }

    @Test fun `account resources are created under the working account`() {
        val hit = bestRun("create kv namespace sessions")
        assertThat(hit.recipe?.id).isEqualTo("kv.create")
        assertThat(hit.draft.pathValues["account_id"]).isEqualTo("acc-1")
        assertThat(hit.draft.body).containsEntry("title", "sessions")

        val bucket = bestRun("new r2 bucket media-assets")
        assertThat(bucket.draft.body).containsEntry("name", "media-assets")
    }

    @Test fun `forward email fills matcher and destination`() {
        val hit = bestRun("forward hi@example.com to me@gmail.com")
        assertThat(Json.get(hit.draft.body, "matchers.0.value")).isEqualTo("hi@example.com")
        assertThat(Json.get(hit.draft.body, "actions.0.value.0")).isEqualTo("me@gmail.com")
    }

    @Test fun `create verb does not land on a list`() {
        val hit = bestRun("create worker route")
        assertThat(hit.draft.method).isNotEqualTo("GET")
    }

    @Test fun `assignments go to the right part of the request`() {
        val hit = bestRun("add dns record type=MX name=@ content=mx.example.net priority=10")
        assertThat(hit.draft.body).containsAtLeast("type", "MX", "content", "mx.example.net", "priority", 10L)
    }

    @Test fun `falls back to the operation registry for anything else`() {
        val results = engine.search("list waiting rooms", ctx)
        assertThat(results.operations.map { it.draft.path }).contains("zones/{zone_id}/waiting_rooms")
        val hit = results.operations.first { it.draft.path == "zones/{zone_id}/waiting_rooms" && it.draft.method == "GET" }
        assertThat(hit.draft.pathValues["zone_id"]).isEqualTo("zone-a")
        assertThat(hit.draft.autoRun).isTrue()
    }

    @Test fun `zone recipes need a zone`() {
        val noZone = CommandContext(account = NamedRef("acc-1", "Main"))
        assertThat(engine.search("purge cache", noZone).actions.map { it.recipe?.id }).doesNotContain("cache.purge_all")
    }

    @Test fun `restricted operations are flagged and never the best hit`() {
        val denying = CommandEngine(registry, allowed = { e, _, _ -> e.method == "GET" })
        val results = denying.search("purge cache", ctx)
        assertThat(results.actions.first { it.recipe?.id == "cache.purge_all" }.restricted).isTrue()
        assertThat((results.best as? CommandHit.Run)?.restricted ?: false).isFalse()
    }

    @Test fun `free sentences suggest the planner`() {
        assertThat(engine.search("make my site faster for visitors in asia", ctx).suggestPlanner).isTrue()
        assertThat(engine.search("purge cache", ctx).suggestPlanner).isFalse()
    }

    @Test fun `workers ai prompt keeps the whole question`() {
        val hit = bestRun("ask ai what is a CNAME record")
        assertThat(hit.recipe?.id).isEqualTo("ai.run")
        assertThat(hit.draft.body?.get("prompt")).isEqualTo("what is a CNAME record")
    }

    @Test fun `json helpers set nested paths without mutating input`() {
        val base = mapOf<String, Any?>("a" to mapOf("b" to 1))
        val out = Json.set(base, "a.c.0.d", "x")
        assertThat(Json.get(out, "a.c.0.d")).isEqualTo("x")
        assertThat(Json.get(base, "a.c")).isNull()
        assertThat(Json.stringify(mapOf("n" to 2.0, "s" to listOf(true)))).isEqualTo("{\"n\":2,\"s\":[true]}")
    }
}
