package dev.cfmobile.app.core.command

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.RawResponse
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class ResultModelTest {
    companion object {
        lateinit var registry: EndpointRegistry

        @BeforeClass @JvmStatic fun load() {
            val asset = listOf("src/main/assets", "app/src/main/assets").map { File(it, EndpointRegistry.ASSET_NAME) }.first { it.exists() }
            registry = asset.inputStream().use { EndpointRegistry.load(it) }
        }
    }

    @Test fun `reads the envelope, errors and pagination`() {
        val ok = ResultModel.parse("""{"success":true,"errors":null,"result":[{"id":"a","name":"x"}],"result_info":{"page":1,"total_pages":3,"total_count":41,"per_page":9223372036854775807}}""", 200)
        assertThat(ok.success).isTrue()
        assertThat(ok.items).hasSize(1)
        assertThat(ok.hasMore).isTrue()
        assertThat(ok.totalCount).isEqualTo(41)

        val bad = ResultModel.parse("""{"success":false,"errors":[{"code":10000,"message":"Authentication error"}],"result":null}""", 403)
        assertThat(bad.success).isFalse()
        assertThat(bad.errors).containsExactly("10000: Authentication error")
    }

    @Test fun `unwraps lists nested one level and handles non envelope bodies`() {
        val wrapped = ResultModel.parse("""{"success":true,"result":{"buckets":[{"name":"b1"},{"name":"b2"}]}}""", 200)
        assertThat(wrapped.items!!.map { ResultModel.title(it) }).containsExactly("b1", "b2")
        val raw = ResultModel.parse("addEventListener('fetch', e => {})", 200)
        assertThat(raw.success).isTrue()
        assertThat(raw.items).isNull()
        val cursor = ResultModel.parse("""{"success":true,"result":[],"result_info":{"cursor":"abc"}}""", 200)
        assertThat(cursor.cursor).isEqualTo("abc")
    }

    @Test fun `picks the identifier a placeholder wants`() {
        val record = mapOf("id" to "r1", "name" to "www.example.com", "type" to "A", "content" to "192.0.2.1")
        assertThat(ResultModel.valueFor("dns_record_id", record)).isEqualTo("r1")
        assertThat(ResultModel.valueFor("bucket_name", mapOf("name" to "media", "creation_date" to "x"))).isEqualTo("media")
        assertThat(ResultModel.valueFor("database_id", mapOf("uuid" to "u1", "name" to "db"))).isEqualTo("u1")
        assertThat(ResultModel.valueFor("sitekey", mapOf("sitekey" to "0x4AAA", "name" to "w"))).isEqualTo("0x4AAA")
        assertThat(ResultModel.subtitle(record)).isEqualTo("A · 192.0.2.1")
    }

    @Test fun `follow ups from a list are its item operations`() {
        val f = ResultModel.followUps(registry, "GET", "zones/{zone_id}/dns_records", isList = true)
        assertThat(f.map { it.endpoint.method to it.endpoint.path }).containsAtLeast(
            "GET" to "zones/{zone_id}/dns_records/{dns_record_id}",
            "PATCH" to "zones/{zone_id}/dns_records/{dns_record_id}",
            "DELETE" to "zones/{zone_id}/dns_records/{dns_record_id}"
        )
        assertThat(f.first { it.endpoint.method == "DELETE" }.placeholder).isEqualTo("dns_record_id")
        val edit = f.first { it.endpoint.method == "PATCH" }.endpoint
        assertThat(ResultModel.editBody(edit, mapOf("id" to "r1", "type" to "A", "name" to "a", "content" to "x", "modified_on" to "t")))
            .containsExactly("type", "A", "name", "a", "content", "x")
    }

    @Test fun `run button verbs come from the operation`() {
        val purge = registry.endpoints.first { it.method == "POST" && it.path == "zones/{zone_id}/purge_cache" }
        assertThat(ResultModel.verb(purge, "POST")).isEqualTo("Purge")
        assertThat(ResultModel.verb(null, "GET")).isEqualTo("Run")
        assertThat(ResultModel.verb(null, "DELETE")).isEqualTo("Delete")
    }

    private fun response(code: Int, body: String) = RawResponse(code, emptyList(), "application/json", body, false, body.length.toLong(), 1)

    @Test fun `planner reads every Workers AI answer shape`() {
        val obj = AiPlanner.parseModelResponse(response(200, """{"success":true,"result":{"response":{"operation":2,"note":"n"}}}"""))
        assertThat(obj["operation"]).isEqualTo(2.0)
        val str = AiPlanner.parseModelResponse(response(200, """{"success":true,"result":{"response":"Sure: {\"operation\": 1}"}}"""))
        assertThat(str["operation"]).isEqualTo(1.0)
        val chat = AiPlanner.parseModelResponse(response(200, """{"success":true,"result":{"choices":[{"message":{"content":"{\"operation\":3}"}}]}}"""))
        assertThat(chat["operation"]).isEqualTo(3.0)
        val denied = assertThrows(Exception::class.java) { AiPlanner.parseModelResponse(response(403, """{"success":false,"errors":[{"code":10000,"message":"no"}]}""")) }
        assertThat(denied.message).contains("Workers AI permission")
    }

    @Test fun `saved actions round trip without bodies`() {
        val a = SavedAction("PATCH", "zones/{zone_id}/settings/{setting_id}", "Development mode", mapOf("zone_id" to "z", "setting_id" to "development_mode"), mapOf("q" to "1"), "example.com", 5)
        val back = ActionStore.decode(Json.parse(Json.stringify(ActionStore.encode(a))) as Map<*, *>)
        assertThat(back).isEqualTo(a)
        assertThat(back!!.toDraft().body).isNull()

        val store = ActionStore(null).apply { load("p") }
        store.recordRun(a)
        store.recordRun(a.copy(at = 9))
        assertThat(store.recents.value).hasSize(1)
        store.togglePin(a)
        assertThat(store.isPinned(a)).isTrue()
        store.togglePin(a)
        assertThat(store.isPinned(a)).isFalse()
        val id = store.put(ActionDraft("GET", "zones"))
        assertThat(store.get(id)?.path).isEqualTo("zones")
    }
}
