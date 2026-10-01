package dev.cfmobile.app.core.command

import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.api.RawRequest
import dev.cfmobile.app.ui.navigation.Routes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Names of the account's own resources, so a command can say "sessions" and mean the KV
 * namespace called sessions. Fetched once per account on demand and kept for a few minutes;
 * a list the token can't read is simply absent.
 */
class ResourceIndex(private val client: RawApiClient, private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry(val at: Long, val refs: List<ResourceRef>)

    private val cache = HashMap<String, Entry>()
    private val mutex = Mutex()

    private data class Source(val kind: String, val path: String, val ref: (accountId: String, item: Map<String, Any?>) -> ResourceRef?)

    private val sources = listOf(
        Source("Worker", "accounts/{a}/workers/scripts") { a, i ->
            val name = i["id"] as? String ?: return@Source null
            ResourceRef("Worker", name, name, route = Routes.workerSecrets(a, name))
        },
        Source("KV namespace", "accounts/{a}/storage/kv/namespaces") { a, i ->
            val id = i["id"] as? String ?: return@Source null
            val title = i["title"] as? String ?: id
            ResourceRef("KV namespace", id, title, route = Routes.kvKeys(a, id, title))
        },
        Source("D1 database", "accounts/{a}/d1/database") { a, i ->
            val id = i["uuid"] as? String ?: return@Source null
            val name = i["name"] as? String ?: id
            ResourceRef("D1 database", id, name, route = Routes.d1Console(a, id, name))
        },
        Source("R2 bucket", "accounts/{a}/r2/buckets") { a, i ->
            val name = i["name"] as? String ?: return@Source null
            ResourceRef("R2 bucket", name, name, route = Routes.r2Objects(a, name, i["jurisdiction"] as? String))
        },
        Source("Pages project", "accounts/{a}/pages/projects") { a, i ->
            val name = i["name"] as? String ?: return@Source null
            ResourceRef("Pages project", name, name, draft = ActionDraft("GET", "accounts/{account_id}/pages/projects/{project_name}", "Pages project", mapOf("account_id" to a, "project_name" to name), autoRun = true))
        },
        Source("Queue", "accounts/{a}/queues") { a, i ->
            val id = i["queue_id"] as? String ?: return@Source null
            ResourceRef("Queue", id, i["queue_name"] as? String ?: id, route = Routes.queues(a))
        },
        Source("Tunnel", "accounts/{a}/cfd_tunnel?is_deleted=false") { a, i ->
            val id = i["id"] as? String ?: return@Source null
            ResourceRef("Tunnel", id, i["name"] as? String ?: id, draft = ActionDraft("GET", "accounts/{account_id}/cfd_tunnel/{tunnel_id}", "Tunnel", mapOf("account_id" to a, "tunnel_id" to id), autoRun = true))
        }
    )

    suspend fun resources(accountId: String, force: Boolean = false): List<ResourceRef> {
        mutex.withLock {
            val hit = cache[accountId]
            if (!force && hit != null && clock() - hit.at < TTL_MILLIS) return hit.refs
        }
        val refs = coroutineScope {
            sources.map { s ->
                async {
                    runCatching {
                        val path = s.path.replace("{a}", accountId)
                        val query = path.substringAfter('?', "").split('&').filter { it.contains('=') }.map { it.substringBefore('=') to it.substringAfter('=') }
                        val response = client.execute(RawRequest("GET", path.substringBefore('?'), query = query + ("per_page" to "100")))
                        if (!response.isSuccess) return@runCatching emptyList()
                        ResultModel.parse(response.body, response.statusCode).items.orEmpty().mapNotNull { s.ref(accountId, it) }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        mutex.withLock { cache[accountId] = Entry(clock(), refs) }
        return refs
    }

    fun invalidate() = cache.clear()

    companion object {
        private const val TTL_MILLIS = 5 * 60_000L
    }
}
