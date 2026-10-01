package dev.cfmobile.app.e2e

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.api.RawRequest
import dev.cfmobile.app.core.api.RawResponse
import dev.cfmobile.app.core.capabilities.CapabilityRepository
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.core.capabilities.PolicySource
import dev.cfmobile.app.core.command.ActionDraft
import dev.cfmobile.app.core.command.AiPlanner
import dev.cfmobile.app.core.command.CommandContext
import dev.cfmobile.app.core.command.CommandEngine
import dev.cfmobile.app.core.command.CommandHit
import dev.cfmobile.app.core.command.Json
import dev.cfmobile.app.core.command.ResourceIndex
import dev.cfmobile.app.core.command.ResultModel
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.data.local.WorkingContext
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.CloudflareHosts
import dev.cfmobile.app.data.remote.NetworkModule
import dev.cfmobile.app.data.repository.D1Repository
import dev.cfmobile.app.data.repository.DnsRepository
import dev.cfmobile.app.data.repository.KvRepository
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.ui.action.ActionViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.primaryConstructor

/**
 * End-to-end against the real Cloudflare API with a real token, through the app's own network
 * stack, repositories, command engine and action screen logic.
 *
 * Skipped unless CF_E2E_TOKEN is set. Never commit a token: pass it in the environment.
 *
 * Safety: reads are unrestricted; writes only touch resources this suite creates (named
 * cfctl-e2e-*) and deletes again, plus zone writes that set a value to what it already is.
 * Live DNS records, zone settings and existing resources are never changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LiveCloudflareE2ETest {

    companion object {
        private val token: String? = System.getenv("CF_E2E_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
        private val report = StringBuilder()
        private val stamp = System.currentTimeMillis().toString(36)

        @AfterClass @JvmStatic fun writeReport() {
            if (token == null || report.isEmpty()) return
            val dir = listOf(File("build"), File("app/build")).first { it.exists() }.resolve("e2e").apply { mkdirs() }
            File(dir, "report.md").writeText("# Live end-to-end report\n\n$report")
        }
    }

    private lateinit var client: okhttp3.OkHttpClient
    private lateinit var api: CloudflareApi
    private lateinit var raw: RawApiClient
    private val hosts = CloudflareHosts()

    private val registry: EndpointRegistry by lazy {
        val asset = listOf("src/main/assets", "app/src/main/assets").map { File(it, EndpointRegistry.ASSET_NAME) }.first { it.exists() }
        asset.inputStream().use { EndpointRegistry.load(it) }
    }

    private val viewModels = ArrayList<ActionViewModel>()

    @org.junit.After fun clearViewModels() = viewModels.forEach { it.viewModelScope.cancel() }

    private lateinit var accountId: String
    private lateinit var accountName: String
    private lateinit var zones: List<NamedRef>

    @Before fun setUp() {
        assumeTrue("CF_E2E_TOKEN not set; live tests skipped", token != null)
        // The CI container shares an egress IP that Cloudflare's edge sometimes throttles (429,
        // code 971) while the token's own budget is untouched. Outermost and test-only: retry
        // those briefly so the app's real stack underneath sees normal traffic.
        val edgeRetry = okhttp3.Interceptor { chain ->
            var response = chain.proceed(chain.request())
            var attempt = 0
            while (response.code == 429 && attempt < 40) {
                response.close()
                Thread.sleep(minOf(5_000L, 1500L + attempt * 500L) + (Math.random() * 1000).toLong())
                attempt++
                response = chain.proceed(chain.request())
            }
            response
        }
        client = NetworkModule.createClient(NetworkModule.Options(hosts, maxRetries = 0), activeTokenProvider = { token })
            .newBuilder().apply { interceptors().add(0, edgeRetry) }.build()
        api = NetworkModule.createRetrofitApi(client, NetworkModule.BASE_URL)
        raw = RawApiClient(client, hosts)
        runBlocking {
            val response = get("accounts")
            val accounts = parse(response).items.orEmpty()
            assertWithMessage("token sees no account: ${response.statusCode} ${response.body.take(300)}").that(accounts).isNotEmpty()
            accountId = accounts.first()["id"] as String
            accountName = accounts.first()["name"] as String
            zones = parse(get("zones", "per_page" to "50")).items.orEmpty().map { NamedRef(it["id"] as String, it["name"] as String, accountId) }
        }
    }

    private fun section(title: String) = report.append("\n## ").append(title).append("\n\n")
    private fun line(text: String) = report.append(text).append('\n')

    private var lastCall = 0L
    /** Keeps well under Cloudflare's 1,200 requests per five minutes. */
    private suspend fun throttle() {
        val wait = 260 - (System.currentTimeMillis() - lastCall)
        if (wait > 0) delay(wait)
        lastCall = System.currentTimeMillis()
    }

    private suspend fun get(path: String, vararg query: Pair<String, String>): RawResponse {
        throttle()
        return raw.execute(RawRequest("GET", path, query = query.toList()))
    }

    private suspend fun send(method: String, path: String, body: Any? = null, query: List<Pair<String, String>> = emptyList()): RawResponse {
        throttle()
        return raw.execute(RawRequest(method, path, query = query, body = body?.let { Json.stringify(it) }))
    }

    private fun parse(r: RawResponse) = ResultModel.parse(r.body, r.statusCode)

    private fun ctx(zone: NamedRef? = zones.firstOrNull()) = CommandContext(NamedRef(accountId, accountName), zone, zones)

    private fun resolve(template: String, values: Map<String, String>) =
        template.split('/').joinToString("/") { s -> if (s.startsWith("{")) values[s.trim('{', '}')] ?: s else s }

    // ---------------------------------------------------------------------------------------

    @Test fun `a01 token verifies and discovery reads its policies`() = runBlocking {
        section("Token and discovery")
        val verify = parse(get("user/tokens/verify"))
        assertThat(verify.success).isTrue()
        val caps = CapabilityRepository(
            api = api, registryProvider = { registry },
            cache = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("e2e-caps", 0),
            scope = CoroutineScope(Dispatchers.IO), activeProfileId = { "e2e" }
        )
        caps.discover()
        val c = caps.state.value.capabilities
        assertThat(c).isNotNull()
        assertThat(c!!.source).isEqualTo(PolicySource.TOKEN_POLICIES)
        val writable = registry.endpoints.count { e ->
            val zone = if (e.scope == dev.cfmobile.app.core.api.EndpointScope.ZONE) zones.firstOrNull()?.id else null
            c.evaluate(e, accountId, zone, accountId) == CapabilityState.AVAILABLE_WRITE
        }
        val readable = registry.endpoints.count { e ->
            val zone = if (e.scope == dev.cfmobile.app.core.api.EndpointScope.ZONE) zones.firstOrNull()?.id else null
            c.evaluate(e, accountId, zone, accountId) == CapabilityState.AVAILABLE_READ
        }
        line("- Token status: active; policies read from the token itself")
        line("- Accounts visible: 1; zones visible: ${zones.size}")
        line("- Operations the token's policies allow: $readable reads, $writable writes, of ${registry.endpoints.size} in the schema")
        assertThat(writable + readable).isGreaterThan(1000)
    }

    @Test fun `a02 every native repository read parses real responses`() = runBlocking {
        section("Native repository reads (Moshi DTOs against real data)")
        val zoneId = zones.firstOrNull()?.id
        val dir = listOf("src/main/java", "app/src/main/java").map { File(it, "dev/cfmobile/app/data/repository") }.first { it.exists() }
        val classes = dir.listFiles().orEmpty().map { it.nameWithoutExtension }.filter { it.endsWith("Repository") && it != "AuthRepository" }.sorted()
        val prefixes = listOf("list", "get", "fetch", "load")
        val skip = setOf("getValue", "getScriptSource", "getAnalytics")
        var ok = 0
        val parseBugs = ArrayList<String>()
        val refused = ArrayList<String>()
        for (name in classes) {
            val kClass = runCatching { Class.forName("dev.cfmobile.app.data.repository.$name").kotlin }.getOrNull() ?: continue
            val ctor = kClass.primaryConstructor ?: continue
            if (ctor.parameters.size != 1 || ctor.parameters[0].type.classifier != CloudflareApi::class) continue
            val repo = ctor.call(api)
            for (fn in kClass.declaredMemberFunctions.sortedBy { it.name }) {
                if (!fn.isSuspend || fn.name in skip || prefixes.none { fn.name.startsWith(it) }) continue
                val args = HashMap<KParameter, Any?>()
                var resolvable = true
                for (p in fn.parameters) {
                    when {
                        p.kind == KParameter.Kind.INSTANCE -> args[p] = repo
                        p.name == "accountId" && p.type.classifier == String::class -> args[p] = accountId
                        p.name == "zoneId" && p.type.classifier == String::class && zoneId != null -> args[p] = zoneId
                        p.isOptional -> Unit
                        else -> resolvable = false
                    }
                }
                if (!resolvable) continue
                throttle()
                val result = runCatching { fn.callSuspendBy(args) }.getOrElse { e -> ApiResult.Failure("Unexpected error: ${e.javaClass.simpleName} ${e.message}") }
                val label = "$name.${fn.name}"
                when (result) {
                    is ApiResult.Success<*> -> ok++
                    is ApiResult.Failure -> when {
                        result.message.startsWith("Unexpected error") -> parseBugs += "$label: ${result.message.take(220)}"
                        else -> refused += "$label: ${result.httpCode ?: "-"} ${result.message.take(140)}"
                    }
                    else -> ok++
                }
            }
        }
        line("- Reads succeeded and parsed: $ok")
        line("- Refused by Cloudflare (plan, entitlement or product not enabled): ${refused.size}")
        refused.forEach { line("  - $it") }
        line("- Parse failures: ${parseBugs.size}")
        parseBugs.forEach { line("  - $it") }
        assertWithMessage("DTOs that failed to parse real responses:\n" + parseBugs.joinToString("\n")).that(parseBugs).isEmpty()
        // Cloudflare also answers 401 for products the account isn't entitled to (Magic WAN,
        // Argo), so a 401 alone isn't an auth failure; a rejected token would fail every read.
        assertThat(refused.count { it.contains("Invalid API Token", ignoreCase = true) || it.contains("(code 9109)") }).isEqualTo(0)
        assertThat(ok).isGreaterThan(60)
    }

    @Test fun `a03 every account and zone read in the schema is reachable and browsable`() = runBlocking {
        section("Schema-wide GET sweep (generic action screen path)")
        val zone = zones.firstOrNull()
        val values = mapOf("account_id" to accountId, "account_identifier" to accountId) + (zone?.let { mapOf("zone_id" to it.id, "zone_identifier" to it.id) } ?: emptyMap())
        val ops = registry.endpoints.filter { e ->
            e.method == "GET" && e.placeholderNames().all { it in values } && !e.path.startsWith("radar/")
        } + registry.endpoints.filter { it.method == "GET" && it.path.startsWith("radar/") && it.placeholderNames().isEmpty() }.take(15)
        val caps = CapabilityRepository(
            api = api, registryProvider = { registry },
            cache = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("e2e-caps-sweep", 0),
            scope = CoroutineScope(Dispatchers.IO), activeProfileId = { "e2e" }
        ).also { it.discover() }.state.value.capabilities
        val refusedButAllowed = ArrayList<String>()
        val statuses = HashMap<Int, Int>()
        val serverErrors = ArrayList<String>()
        val unparsed = ArrayList<String>()
        val drill = ArrayList<Pair<EndpointDef, Map<String, Any?>>>()
        for (e in ops) {
            val r = runCatching { get(resolve(e.path, values)) }.getOrNull()
            if (r == null) { statuses.merge(-1, 1, Int::plus); continue }
            statuses.merge(r.statusCode, 1, Int::plus)
            if (r.statusCode >= 500) serverErrors += "${e.method} ${e.path} -> ${r.statusCode}"
            val p = runCatching { parse(r) }.getOrNull()
            if ((r.statusCode == 401 || r.statusCode == 403) && caps?.evaluate(e, accountId, zone?.id, accountId)?.let { it == CapabilityState.AVAILABLE_READ || it == CapabilityState.AVAILABLE_WRITE } == true) {
                refusedButAllowed += "${e.path} -> ${r.statusCode} ${p?.errors?.firstOrNull().orEmpty().take(90)}"
            }
            if (p == null) unparsed += e.path
            if (r.isSuccess && p?.items?.isNotEmpty() == true) drill += e to p.items!!.first()
        }
        // One level down: open the first item of each list the way a tap in the results does.
        var drilled = 0
        var drillOk = 0
        val drillFail = ArrayList<String>()
        for ((list, item) in drill.take(220)) {
            val follow = ResultModel.followUps(registry, "GET", list.path, isList = true)
                .firstOrNull { it.endpoint.method == "GET" && it.endpoint.path.count { c -> c == '/' } == list.path.count { c -> c == '/' } + 1 } ?: continue
            val id = follow.placeholder?.let { ResultModel.valueFor(it, item) } ?: continue
            drilled++
            val r = runCatching { get(resolve(follow.endpoint.path, values + (follow.placeholder to id))) }.getOrNull() ?: continue
            if (r.isSuccess) drillOk++ else drillFail += "${follow.endpoint.path} -> ${r.statusCode} ${parse(r).errors.firstOrNull().orEmpty().take(100)}"
        }
        line("- Operations called: ${ops.size}")
        line("- Status codes: " + statuses.toSortedMap().entries.joinToString(", ") { "${it.key}: ${it.value}" })
        line("- Server errors: ${serverErrors.size}")
        serverErrors.forEach { line("  - $it") }
        line("- Refused although the token's policies include the permission (product not enabled, plan or entitlement): ${refusedButAllowed.size}")
        refusedButAllowed.sorted().forEach { line("  - $it") }
        line("- Drill-down from a list item to its detail: $drillOk of $drilled succeeded")
        drillFail.take(40).forEach { line("  - $it") }
        assertThat(unparsed).isEmpty()
        assertThat((statuses[200] ?: 0)).isGreaterThan(ops.size / 3)
        assertWithMessage("Most item drill-downs should resolve the right identifier").that(drillOk * 10).isAtLeast(drilled * 6)
    }

    /** Runs a draft through the real action screen view model, as a tap on Run would. */
    private fun runDraft(draft: ActionDraft): RunOutcome {
        val vm = ActionViewModel(
            registryProvider = { registry }, client = raw,
            tokenState = { _, _, _ -> CapabilityState.UNKNOWN },
            workingContext = { WorkingContext(account = NamedRef(accountId, accountName), zone = zones.firstOrNull()) },
            zones = { zones }, accounts = { listOf(NamedRef(accountId, accountName)) },
            onRan = {}, isPinned = { false }, togglePin = {},
            draft = draft.copy(autoRun = false), endpointId = null
        )
        idleUntil { !vm.ui.value.loading }
        val problem = vm.validate()
        if (problem != null) return RunOutcome(null, problem, vm)
        runBlocking { throttle() }
        vm.run()
        idleUntil(60_000) { !vm.ui.value.running && (vm.ui.value.result != null || vm.ui.value.failure != null) }
        viewModels += vm
        return RunOutcome(vm.ui.value.result?.response, vm.ui.value.failure, vm)
    }

    private data class RunOutcome(val response: RawResponse?, val failure: String?, val vm: ActionViewModel) {
        val ok get() = response?.isSuccess == true
        @Suppress("UNCHECKED_CAST")
        val result: Map<String, Any?>? get() = (Json.parseOrNull(response?.body.orEmpty()) as? Map<String, Any?>)?.get("result") as? Map<String, Any?>
        fun describe() = failure ?: "${response?.statusCode} ${response?.body?.take(300)}"
    }

    private fun idleUntil(timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (condition()) return
            Thread.sleep(25)
        }
        throw AssertionError("Timed out")
    }

    private fun command(text: String, zone: NamedRef? = zones.firstOrNull()): CommandHit.Run {
        val hit = CommandEngine(registry).search(text, ctx(zone)).best
        assertWithMessage("\"$text\" should resolve to an action, got $hit").that(hit).isInstanceOf(CommandHit.Run::class.java)
        return hit as CommandHit.Run
    }

    @Test fun `a04 create, use and delete resources through typed commands`() = runBlocking {
        section("Resource lifecycles via typed commands and the action screen")
        val name = "cfctl-e2e-$stamp"
        val results = ArrayList<String>()
        fun record(step: String, ok: Boolean, detail: String = "") { results += "${if (ok) "pass" else "FAIL"} $step${if (detail.isNotBlank()) " ($detail)" else ""}" }

        cleanupLeftovers()

        // KV: command -> create; native repository -> write, read, list; follow-up -> delete.
        val kvCreate = runDraft(command("create kv namespace $name").draft)
        record("create KV namespace by command", kvCreate.ok, kvCreate.describe().takeIf { !kvCreate.ok }.orEmpty())
        val nsId = kvCreate.result?.get("id") as? String
        if (nsId != null) {
            val kv = KvRepository(api)
            val put = kv.putValue(accountId, nsId, "hello", "world")
            val read = kv.getValue(accountId, nsId, "hello")
            val keys = kv.listKeys(accountId, nsId)
            record(
                "KV write, read and list through the native screen's repository",
                put is ApiResult.Success && (read as? ApiResult.Success)?.data == "world" && (keys as? ApiResult.Success)?.data?.any { it.name == "hello" } == true,
                "put=$put read=$read keys=$keys"
            )
            val del = runDraft(ActionDraft("DELETE", "accounts/{account_id}/storage/kv/namespaces/{namespace_id}", pathValues = mapOf("account_id" to accountId, "namespace_id" to nsId)))
            record("delete KV namespace from the action screen", del.ok, del.describe().takeIf { !del.ok }.orEmpty())
        }

        // D1: command -> create; native console query; delete.
        val d1Create = runDraft(command("create d1 database $name").draft)
        record("create D1 database by command", d1Create.ok, d1Create.describe().takeIf { !d1Create.ok }.orEmpty())
        val dbId = d1Create.result?.get("uuid") as? String
        if (dbId != null) {
            val d1 = D1Repository(api)
            val q = d1.query(accountId, dbId, "CREATE TABLE t (x TEXT); INSERT INTO t VALUES ('ok'); SELECT x FROM t;")
            record("D1 console runs SQL", q is ApiResult.Success, (q as? ApiResult.Failure)?.message.orEmpty())
            val del = runDraft(ActionDraft("DELETE", "accounts/{account_id}/d1/database/{database_id}", pathValues = mapOf("account_id" to accountId, "database_id" to dbId)))
            record("delete D1 database", del.ok, del.describe().takeIf { !del.ok }.orEmpty())
        }

        // R2: needs R2 to be enabled on the account; a refusal is reported, not hidden.
        val r2Create = runDraft(command("create r2 bucket $name").draft)
        if (r2Create.ok) {
            record("create R2 bucket by command", true)
            val del = runDraft(ActionDraft("DELETE", "accounts/{account_id}/r2/buckets/{bucket_name}", pathValues = mapOf("account_id" to accountId, "bucket_name" to name)))
            record("delete R2 bucket", del.ok, del.describe().takeIf { !del.ok }.orEmpty())
        } else {
            results += "skip R2 bucket: Cloudflare refused (${parse(r2Create.response ?: RawResponse(0, emptyList(), null, "", false, 0, 0)).errors.firstOrNull() ?: r2Create.failure})"
        }

        // Queue.
        val qCreate = runDraft(command("create queue $name").draft)
        record("create queue by command", qCreate.ok, qCreate.describe().takeIf { !qCreate.ok }.orEmpty())
        (qCreate.result?.get("queue_id") as? String)?.let { qid ->
            val del = runDraft(ActionDraft("DELETE", "accounts/{account_id}/queues/{queue_id}", pathValues = mapOf("account_id" to accountId, "queue_id" to qid)))
            record("delete queue", del.ok, del.describe().takeIf { !del.ok }.orEmpty())
        }

        // Tunnel.
        val tCreate = runDraft(command("create tunnel $name").draft)
        record("create tunnel by command", tCreate.ok, tCreate.describe().takeIf { !tCreate.ok }.orEmpty())
        (tCreate.result?.get("id") as? String)?.let { tid ->
            val del = runDraft(ActionDraft("DELETE", "accounts/{account_id}/cfd_tunnel/{tunnel_id}", pathValues = mapOf("account_id" to accountId, "tunnel_id" to tid)))
            record("delete tunnel", del.ok, del.describe().takeIf { !del.ok }.orEmpty())
        }

        // Account IP list.
        val listName = "cfctl_e2e_$stamp"
        val lCreate = runDraft(command("create ip list $listName").draft)
        record("create IP list by command", lCreate.ok, lCreate.describe().takeIf { !lCreate.ok }.orEmpty())
        (lCreate.result?.get("id") as? String)?.let { lid ->
            val del = runDraft(ActionDraft("DELETE", "accounts/{account_id}/rules/lists/{list_id}", pathValues = mapOf("account_id" to accountId, "list_id" to lid)))
            record("delete IP list", del.ok, del.describe().takeIf { !del.ok }.orEmpty())
        }

        // Turnstile widget, scoped to the first zone's hostname.
        zones.firstOrNull()?.let { z ->
            val wCreate = runDraft(command("create turnstile widget $name for www.${z.name}").draft)
            record("create Turnstile widget by command", wCreate.ok, wCreate.describe().takeIf { !wCreate.ok }.orEmpty())
            (wCreate.result?.get("sitekey") as? String)?.let { key ->
                val del = runDraft(ActionDraft("DELETE", "accounts/{account_id}/challenges/widgets/{sitekey}", pathValues = mapOf("account_id" to accountId, "sitekey" to key)))
                record("delete Turnstile widget", del.ok, del.describe().takeIf { !del.ok }.orEmpty())
            }
        }

        // Access service token.
        val sCreate = runDraft(command("create service token $name").draft)
        record("create Access service token by command", sCreate.ok, sCreate.describe().takeIf { !sCreate.ok }.orEmpty())
        (sCreate.result?.get("id") as? String)?.let { sid ->
            val del = runDraft(ActionDraft("DELETE", "accounts/{account_id}/access/service_tokens/{service_token_id}", pathValues = mapOf("account_id" to accountId, "service_token_id" to sid)))
            record("delete Access service token", del.ok, del.describe().takeIf { !del.ok }.orEmpty())
        }

        // DNS: a TXT record under a throwaway label, created, edited via a follow-up, deleted.
        zones.firstOrNull()?.let { z ->
            val label = "_cfctl-e2e-$stamp"
            val create = runDraft(command("add txt record $label \"cfctl e2e $stamp\" on ${z.name}", z).draft)
            record("add DNS TXT record by command", create.ok, create.describe().takeIf { !create.ok }.orEmpty())
            val recordId = create.result?.get("id") as? String
            if (recordId != null) {
                val native = DnsRepository(api).listRecords(z.id)
                record("native DNS screen sees the new record", (native as? ApiResult.Success)?.data?.any { it.id == recordId } == true)
                val list = runDraft(command("list dns records type=TXT", z).draft.copy(query = mapOf("type" to "TXT", "name" to "$label.${z.name}")))
                val item = list.vm.ui.value.result?.items?.firstOrNull { it["id"] == recordId }
                record("list result contains the record", item != null)
                val edit = item?.let { list.vm.followUps(isList = true).firstOrNull { it.endpoint.method == "PATCH" } }
                if (edit != null) {
                    val editDraft = list.vm.followUpDraft(edit, item).let { d -> d.copy(body = (d.body ?: emptyMap()) + ("content" to "cfctl e2e edited")) }
                    val patched = runDraft(editDraft)
                    record("edit the record through a result follow-up", patched.ok && patched.result?.get("content")?.toString()?.contains("edited") == true, patched.describe().takeIf { !patched.ok }.orEmpty())
                }
                val del = runDraft(ActionDraft("DELETE", "zones/{zone_id}/dns_records/{dns_record_id}", pathValues = mapOf("zone_id" to z.id, "dns_record_id" to recordId)))
                record("delete the DNS record", del.ok, del.describe().takeIf { !del.ok }.orEmpty())
            }
        }

        results.forEach { line("- $it") }
        assertWithMessage(results.joinToString("\n")).that(results.none { it.startsWith("FAIL") }).isTrue()
    }

    /** Removes anything an interrupted earlier run left behind. */
    private suspend fun cleanupLeftovers() {
        suspend fun sweep(listPath: String, deletePath: String, idKey: String, nameKey: String, query: List<Pair<String, String>> = emptyList()) {
            val items = parse(get(listPath.replace("{a}", accountId), *query.toTypedArray())).items.orEmpty()
            items.filter { (it[nameKey] as? String)?.startsWith("cfctl") == true }.forEach { item ->
                send("DELETE", deletePath.replace("{a}", accountId).replace("{id}", item[idKey].toString()))
            }
        }
        sweep("accounts/{a}/storage/kv/namespaces", "accounts/{a}/storage/kv/namespaces/{id}", "id", "title")
        sweep("accounts/{a}/d1/database", "accounts/{a}/d1/database/{id}", "uuid", "name")
        sweep("accounts/{a}/queues", "accounts/{a}/queues/{id}", "queue_id", "queue_name")
        sweep("accounts/{a}/cfd_tunnel", "accounts/{a}/cfd_tunnel/{id}", "id", "name", listOf("is_deleted" to "false"))
        sweep("accounts/{a}/rules/lists", "accounts/{a}/rules/lists/{id}", "id", "name")
        sweep("accounts/{a}/challenges/widgets", "accounts/{a}/challenges/widgets/{id}", "sitekey", "name")
        sweep("accounts/{a}/access/service_tokens", "accounts/{a}/access/service_tokens/{id}", "id", "name")
        for (z in zones) {
            parse(get("zones/${z.id}/dns_records", "type" to "TXT", "per_page" to "100")).items.orEmpty()
                .filter { (it["name"] as? String)?.startsWith("_cfctl-e2e-") == true }
                .forEach { send("DELETE", "zones/${z.id}/dns_records/${it["id"]}") }
        }
    }

    @Test fun `a05 zone writes round trip without changing anything`() = runBlocking {
        section("Zone write paths (no-op values)")
        val z = zones.firstOrNull() ?: return@runBlocking
        val settings = ZoneSettingsRepository(api)
        val out = ArrayList<String>()
        for (setting in listOf("development_mode", "always_use_https", "security_level", "ssl")) {
            val current = (settings.getSetting(z.id, setting) as? ApiResult.Success)?.data ?: continue
            val hit = CommandEngine(registry).search("set ${setting.replace('_', ' ')} to $current on ${z.name}", ctx(z)).best
            val draft = ActionDraft("PATCH", "zones/{zone_id}/settings/{setting_id}", pathValues = mapOf("zone_id" to z.id, "setting_id" to setting), body = mapOf("value" to current))
            val r = runDraft(draft)
            val after = (settings.getSetting(z.id, setting) as? ApiResult.Success)?.data
            out += "${if (r.ok && after == current) "pass" else "FAIL"} $setting re-saved as \"$current\" (command matched ${hit?.title ?: "nothing"})"
        }
        val caps = CapabilityRepository(
            api = api, registryProvider = { registry },
            cache = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("e2e-caps-purge", 0),
            scope = CoroutineScope(Dispatchers.IO), activeProfileId = { "e2e" }
        ).also { it.discover() }.state.value.capabilities!!
        val purgeOp = registry.endpoints.first { it.method == "POST" && it.path == "zones/{zone_id}/purge_cache" }
        val purgeState = caps.evaluate(purgeOp, accountId, z.id, accountId)
        val flagged = CommandEngine(registry, allowed = { e, a, zid -> caps.evaluate(e, a, zid, a).let { s -> if (s == CapabilityState.TOKEN_RESTRICTED) false else null } })
            .search("purge https://${z.name}/x.txt", ctx(z)).actions.firstOrNull { it.recipe?.id == "cache.purge_urls" }?.restricted
        val purge = runDraft(command("purge https://${z.name}/cfctl-e2e-$stamp-not-a-real-file.txt", z).draft)
        val consistent = if (purgeState == CapabilityState.TOKEN_RESTRICTED) !purge.ok && flagged == true else purge.ok
        out += "${if (consistent) "pass" else "FAIL"} purge a single URL: policies say $purgeState, Cloudflare answered ${purge.response?.statusCode}, command flagged as not allowed: $flagged"
        out.forEach { line("- $it") }
        assertWithMessage(out.joinToString("\n")).that(out.none { it.startsWith("FAIL") }).isTrue()
    }

    @Test fun `a06 workers ai answers prompts and plans requests`() = runBlocking {
        section("Workers AI")
        val ask = runDraft(command("ask ai reply with the single word pong").draft)
        line("- ask ai: ${if (ask.ok) "pass" else "FAIL " + ask.describe()}")
        assertThat(ask.ok).isTrue()

        val planner = AiPlanner(raw, { registry })
        val engine = CommandEngine(registry)
        val z = zones.first()
        val cases = listOf(
            "show me every kv namespace in my account" to { e: EndpointDef -> e.path == "accounts/{account_id}/storage/kv/namespaces" && e.method == "GET" },
            "block all visitors from north korea on ${z.name}" to { e: EndpointDef -> e.method == "POST" && (e.path.contains("access_rules") || e.path.contains("rulesets")) },
            "which dns records does ${z.name} have" to { e: EndpointDef -> e.method == "GET" && e.path.contains("dns_records") }
        )
        var passed = 0
        for ((text, expect) in cases) {
            throttle()
            val local = engine.search(text, ctx(z))
            val outcome = planner.plan(text, ctx(z), local.actions + local.operations)
            val ok = outcome is AiPlanner.Outcome.Planned && expect(outcome.endpoint)
            if (ok) passed++
            line("- \"$text\" -> " + when (outcome) {
                is AiPlanner.Outcome.Planned -> "${outcome.endpoint.method} ${outcome.endpoint.path} ${Json.stringify(outcome.draft.body ?: emptyMap<String, Any?>()).take(120)}"
                is AiPlanner.Outcome.Failed -> "failed: ${outcome.message}"
            } + if (ok) "" else "  (unexpected)")
        }
        assertThat(passed).isAtLeast(2)
    }

    @Test fun `a07 command search resolves real names and every hit builds a valid request`() = runBlocking {
        section("Command search on live data")
        val resources = ResourceIndex(raw).resources(accountId)
        val c = ctx().copy(resources = resources)
        val engine = CommandEngine(registry)
        val phrases = listOf(
            "dns", "purge cache", "turn on dev mode", "ssl strict", "list workers", "audit log", "waiting rooms",
            "create kv namespace demo", "block 203.0.113.9", "zone details", "r2 buckets", "tunnels", "billing",
            "page rules", "rulesets", "logpush jobs", "notification policies", "email routing rules", "api tokens"
        ) + zones.map { it.name } + resources.take(5).map { it.name }
        var built = 0
        val failures = ArrayList<String>()
        for (p in phrases) {
            val r = engine.search(p, c)
            val best = r.best
            if (best == null) { failures += "\"$p\": no result"; continue }
            if (best is CommandHit.Run) {
                val req = RawRequest(best.draft.method, resolve(best.draft.path, best.draft.pathValues), best.draft.query.toList(), body = best.draft.body?.let(Json::stringify))
                val placeholders = Regex("\\{[^}]+}").findAll(req.path).count()
                if (placeholders == 0) runCatching { raw.buildRequest(req) }.onSuccess { built++ }.onFailure { failures += "\"$p\": ${it.message}" } else built++
            } else built++
        }
        line("- Account resources indexed for search: ${resources.size} (${resources.groupBy { it.kind }.map { "${it.key}: ${it.value.size}" }.joinToString(", ")})")
        line("- Phrases resolved: $built of ${phrases.size}")
        failures.forEach { line("  - $it") }
        assertThat(failures).isEmpty()
    }
}
