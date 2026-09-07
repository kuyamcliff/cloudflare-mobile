package dev.cfmobile.app.ui.zoneproducts

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.CloudConnectorParameters
import dev.cfmobile.app.data.remote.dto.CloudConnectorRule
import dev.cfmobile.app.data.remote.dto.CustomPage
import dev.cfmobile.app.data.remote.dto.SnippetRule
import dev.cfmobile.app.data.remote.dto.ZarazConfig
import dev.cfmobile.app.data.remote.dto.ZarazSettings
import dev.cfmobile.app.data.remote.dto.ZarazTool
import dev.cfmobile.app.data.remote.dto.ZarazTrigger
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.CloudConnectorRepository
import dev.cfmobile.app.data.repository.CustomPagesRepository
import dev.cfmobile.app.data.repository.SnippetsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The zone-level products added together: Snippets, Cloud Connector, error pages, and Zaraz. */
class ZoneProductsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    // ---- Snippets ----

    /** Path-keyed: the screen loads snippets and their rules in one pass. */
    private fun serveSnippets(snippets: String = "[]", rules: String = "[]") {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/snippet_rules") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":$rules}""")
                    path.endsWith("/content") -> MockResponse().setBody("export default {}")
                    path.endsWith("/snippets") ->
                        MockResponse().setBody("""{"success":true,"errors":[],"result":$snippets}""")
                    else -> MockResponse().setBody("""{"success":true,"errors":[],"result":{}}""")
                }
            }
        }
    }

    private suspend fun SnippetsViewModel.awaitLoaded() =
        uiState.first { it.snippets !is UiState.Loading && it.rules !is UiState.Loading }

    @Test
    fun `snippets and their rules load together`() = runTest {
        serveSnippets(
            snippets = """[{"snippet_name":"add_header","modified_on":"2026-01-01"}]""",
            rules = """[{"id":"r1","expression":"true","snippet_name":"add_header","enabled":true}]"""
        )
        val vm = SnippetsViewModel("zone1", SnippetsRepository(testApi(server)))

        val state = vm.awaitLoaded()

        assertThat((state.snippets as UiState.Data).value.single().snippetName).isEqualTo("add_header")
        assertThat((state.rules as UiState.Data).value.single().id).isEqualTo("r1")
    }

    @Test
    fun `opening a snippet fetches its source separately`() = runTest {
        serveSnippets(snippets = """[{"snippet_name":"add_header"}]""")
        val vm = SnippetsViewModel("zone1", SnippetsRepository(testApi(server)))
        val snippet = (vm.awaitLoaded().snippets as UiState.Data).value.single()

        vm.openSource(snippet)
        val state = vm.uiState.first { it.source?.source is UiState.Data }

        assertThat((state.source?.source as UiState.Data).value).isEqualTo("export default {}")
    }

    @Test
    fun `toggling a rule sends the whole list back with only that rule changed`() = runTest {
        serveSnippets(
            rules = """[{"id":"r1","expression":"true","snippet_name":"a","enabled":true},
                        {"id":"r2","expression":"false","snippet_name":"b","enabled":true}]"""
        )
        val vm = SnippetsViewModel("zone1", SnippetsRepository(testApi(server)))
        val rules = (vm.awaitLoaded().rules as UiState.Data).value
        // Cloudflare replaces the zone's whole rule list, so the untouched rule has to be
        // resent or it would be dropped.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"r1","snippet_name":"a","enabled":false},
                    {"id":"r2","snippet_name":"b","enabled":true}]}"""
            )
        }

        vm.setRuleEnabled(rules.first(), false)
        vm.uiState.first { (it.rules as? UiState.Data)?.value?.first()?.enabled == false }

        val body = server.takeRequest().let { first ->
            // The first request captured after swapping the dispatcher is the PUT.
            generateSequence(first) { server.takeRequest() }.first { it.method == "PUT" }
        }.body.readUtf8()
        assertThat(body).contains("\"id\":\"r2\"")
        assertThat(body).contains("\"enabled\":false")
    }

    @Test
    fun `snippetRuleSummary names the snippet a rule runs`() {
        assertThat(snippetRuleSummary(SnippetRule(snippetName = "add_header"))).isEqualTo("add_header")
        assertThat(snippetRuleSummary(SnippetRule())).isEqualTo("No snippet")
    }

    // ---- Cloud Connector ----

    @Test
    fun `a bucket hostname is rejected if it carries a scheme or path`() {
        val form = CloudConnectorFormState(host = "https://bucket.s3.amazonaws.com")
        assertThat(validateCloudConnectorForm(form)).contains("no scheme")
        assertThat(validateCloudConnectorForm(form.copy(host = "bucket.s3.amazonaws.com/prefix"))).contains("no scheme")
        assertThat(validateCloudConnectorForm(form.copy(host = "bucket.s3.amazonaws.com"))).isNull()
        assertThat(validateCloudConnectorForm(form.copy(host = ""))).contains("required")
    }

    @Test
    fun `providerLabel names the known providers and passes anything else through`() {
        assertThat(providerLabel("cloudflare_r2")).isEqualTo("Cloudflare R2")
        assertThat(providerLabel("some_new_provider")).isEqualTo("some_new_provider")
    }

    @Test
    fun `cloudConnectorSummary reads as provider and bucket`() {
        val rule = CloudConnectorRule(
            expression = "true",
            provider = "aws_s3",
            parameters = CloudConnectorParameters(host = "bucket.s3.amazonaws.com")
        )

        assertThat(cloudConnectorSummary(rule)).isEqualTo("Amazon S3 · bucket.s3.amazonaws.com")
    }

    @Test
    fun `editing a rule starts from what Cloudflare stored`() {
        val rule = CloudConnectorRule(
            id = "c1",
            expression = "http.request.uri.path contains \"/assets\"",
            provider = "cloudflare_r2",
            description = "assets",
            enabled = false,
            parameters = CloudConnectorParameters(host = "b.r2.cloudflarestorage.com")
        )

        val form = cloudConnectorFormOf(rule)

        assertThat(form.editingId).isEqualTo("c1")
        assertThat(form.provider).isEqualTo(CloudProvider.R2)
        assertThat(form.host).isEqualTo("b.r2.cloudflarestorage.com")
        assertThat(form.enabled).isFalse()
    }

    @Test
    fun `adding a rule sends the existing rules alongside the new one`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"c1","expression":"true","provider":"aws_s3","parameters":{"host":"a.example.com"}}]}"""
            )
        )
        val vm = CloudConnectorViewModel("zone1", CloudConnectorRepository(testApi(server)))
        vm.uiState.first { it.rules !is UiState.Loading }
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[
                    {"id":"c1","expression":"true","provider":"aws_s3","parameters":{"host":"a.example.com"}},
                    {"id":"c2","expression":"true","provider":"cloudflare_r2","parameters":{"host":"b.example.com"}}]}"""
            )
        )

        vm.openCreateForm()
        vm.updateForm { it.copy(host = "b.example.com") }
        vm.save()
        vm.uiState.first { (it.rules as? UiState.Data)?.value?.size == 2 }

        server.takeRequest()
        val put = server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        val body = put.body.readUtf8()
        assertThat(body).contains("a.example.com")
        assertThat(body).contains("b.example.com")
    }

    @Test
    fun `deleting a rule sends the list without it`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[
                    {"id":"c1","expression":"true","provider":"aws_s3","parameters":{"host":"a.example.com"}},
                    {"id":"c2","expression":"true","provider":"aws_s3","parameters":{"host":"b.example.com"}}]}"""
            )
        )
        val vm = CloudConnectorViewModel("zone1", CloudConnectorRepository(testApi(server)))
        val rules = (vm.uiState.first { it.rules !is UiState.Loading }.rules as UiState.Data).value
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"c2","expression":"true","provider":"aws_s3","parameters":{"host":"b.example.com"}}]}"""
            )
        )

        vm.delete(rules.first())
        vm.uiState.first { (it.rules as? UiState.Data)?.value?.size == 1 }

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).doesNotContain("a.example.com")
        assertThat(body).contains("b.example.com")
    }

    // ---- Custom error pages ----

    @Test
    fun `customPageLabel prefers Cloudflare's description and falls back to the id`() {
        assertThat(customPageLabel(CustomPage(id = "waf_block", description = "WAF Block"))).isEqualTo("WAF Block")
        assertThat(customPageLabel(CustomPage(id = "ip_block"))).isEqualTo("Ip Block")
    }

    @Test
    fun `a custom page URL has to be https`() {
        val page = CustomPage(id = "waf_block")
        assertThat(validateCustomPageForm(CustomPageFormState(page, url = ""))).contains("required")
        assertThat(validateCustomPageForm(CustomPageFormState(page, url = "http://example.com/e.html")))
            .contains("https://")
        assertThat(validateCustomPageForm(CustomPageFormState(page, url = "https://example.com/e.html"))).isNull()
    }

    @Test
    fun `customPageStatus distinguishes a customized page from Cloudflare's own`() {
        assertThat(customPageStatus(CustomPage(id = "a", state = "customized", url = "https://x.com/e.html")))
            .isEqualTo("https://x.com/e.html")
        assertThat(customPageStatus(CustomPage(id = "a", state = "default")))
            .isEqualTo("Cloudflare's default page")
    }

    @Test
    fun `requiredTokensLabel lists the placeholders Cloudflare substitutes`() {
        assertThat(requiredTokensLabel(CustomPage(requiredTokens = listOf("::CLOUDFLARE_ERROR_1000S_BOX::"))))
            .contains("CLOUDFLARE_ERROR")
        assertThat(requiredTokensLabel(CustomPage(requiredTokens = emptyList()))).isNull()
    }

    @Test
    fun `customizing a page sends the url and the customized state`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"waf_block","state":"default"}]}"""))
        val vm = CustomPagesViewModel("zone1", CustomPagesRepository(testApi(server)))
        val page = (vm.uiState.first { it.pages !is UiState.Loading }.pages as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"waf_block","state":"customized"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"waf_block","state":"customized"}]}"""))

        vm.openForm(page)
        vm.updateForm { it.copy(url = "https://example.com/e.html") }
        vm.save()
        vm.uiState.first { it.form == null && !it.isRefreshing }

        server.takeRequest()
        val put = server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.path).isEqualTo("/zones/zone1/custom_pages/waf_block")
        assertThat(put.body.readUtf8()).contains("\"state\":\"customized\"")
    }

    @Test
    fun `reverting a page sends a null url so Cloudflare serves its own`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"waf_block","state":"customized","url":"https://x.com/e.html"}]}"""))
        val vm = CustomPagesViewModel("zone1", CustomPagesRepository(testApi(server)))
        val page = (vm.uiState.first { it.pages !is UiState.Loading }.pages as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"waf_block","state":"default"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"waf_block","state":"default"}]}"""))

        vm.revert(page)
        vm.uiState.first { it.revertingId == null && !it.isRefreshing }

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"state\":\"default\"")
        assertThat(body).contains("\"url\":null")
    }

    // ---- Zaraz ----

    @Test
    fun `zarazTools treats an absent enabled flag as enabled`() {
        val config = ZarazConfig(
            tools = mapOf(
                "t1" to ZarazTool(name = "Google Analytics", type = "component"),
                "t2" to ZarazTool(name = "Ads", type = "component", enabled = false)
            )
        )

        val tools = zarazTools(config)

        // Cloudflare omits `enabled` for a tool that is on.
        assertThat(tools.single { it.id == "t1" }.enabled).isTrue()
        assertThat(tools.single { it.id == "t2" }.enabled).isFalse()
        assertThat(tools.map { it.name }).containsExactly("Ads", "Google Analytics").inOrder()
    }

    @Test
    fun `a tool with no name falls back to its type and then its id`() {
        val config = ZarazConfig(tools = mapOf("t1" to ZarazTool(type = "component"), "t2" to ZarazTool()))

        val tools = zarazTools(config)

        assertThat(tools.single { it.id == "t1" }.name).isEqualTo("component")
        assertThat(tools.single { it.id == "t2" }.name).isEqualTo("t2")
    }

    @Test
    fun `only the privacy settings Cloudflare reported are listed`() {
        val config = ZarazConfig(settings = ZarazSettings(hideIPAddress = true, hideQueryParams = false))

        val settings = zarazPrivacySettings(config)

        assertThat(settings.map { it.label })
            .containsExactly("Hide visitor IP address", "Hide URL query parameters").inOrder()
        assertThat(settings.first().enabled).isTrue()
        assertThat(zarazPrivacySettings(ZarazConfig())).isEmpty()
    }

    @Test
    fun `trigger names are listed alphabetically, skipping unnamed ones`() {
        val config = ZarazConfig(
            triggers = mapOf(
                "a" to ZarazTrigger(name = "Pageview"),
                "b" to ZarazTrigger(name = "Click"),
                "c" to ZarazTrigger()
            )
        )

        assertThat(zarazTriggerNames(config)).containsExactly("Click", "Pageview").inOrder()
    }
}
