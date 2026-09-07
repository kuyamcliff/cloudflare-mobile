package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CloudConnectorParameters
import dev.cfmobile.app.data.remote.dto.CloudConnectorRule
import dev.cfmobile.app.data.remote.dto.SnippetRule
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Endpoint and shape coverage for the zone-level products added together. */
class ZoneProductsRepositoriesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listSnippets hits the zone snippets endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"snippet_name":"add_header","modified_on":"2026-01-01"}]}"""))

        val result = SnippetsRepository(testApi(server)).listSnippets("zone1")

        assertThat((result as ApiResult.Success).data.single().snippetName).isEqualTo("add_header")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/snippets")
    }

    @Test
    fun `getContent returns the snippet body rather than an envelope`() = runBlocking {
        server.enqueue(MockResponse().setBody("export default { fetch() {} }"))

        val result = SnippetsRepository(testApi(server)).getContent("zone1", "add_header")

        assertThat((result as ApiResult.Success).data).isEqualTo("export default { fetch() {} }")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/snippets/add_header/content")
    }

    @Test
    fun `getContent unwraps a multipart body`() = runBlocking {
        val body = listOf(
            "--boundary",
            "Content-Disposition: form-data; name=\"snippet.js\"",
            "",
            "export default {}",
            "--boundary--"
        ).joinToString("\n")
        server.enqueue(MockResponse().setBody(body))

        val result = SnippetsRepository(testApi(server)).getContent("zone1", "add_header")

        assertThat((result as ApiResult.Success).data).contains("export default {}")
        assertThat(result.data).doesNotContain("Content-Disposition")
    }

    @Test
    fun `putRules replaces the zone's whole snippet rule list`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"r1","snippet_name":"a"}]}"""))

        SnippetsRepository(testApi(server)).putRules(
            "zone1",
            listOf(SnippetRule(id = "r1", expression = "true", snippetName = "a"))
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/zones/zone1/snippets/snippet_rules")
        assertThat(request.body.readUtf8()).contains("\"snippet_name\":\"a\"")
    }

    @Test
    fun `listCloudConnectorRules parses the provider and its bucket host`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"c1","expression":"true","provider":"cloudflare_r2","parameters":{"host":"b.r2.cloudflarestorage.com"}}]}"""
            )
        )

        val result = CloudConnectorRepository(testApi(server)).listRules("zone1")

        val rule = (result as ApiResult.Success).data.single()
        assertThat(rule.provider).isEqualTo("cloudflare_r2")
        assertThat(rule.parameters?.host).isEqualTo("b.r2.cloudflarestorage.com")
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/cloud_connector/rules")
    }

    @Test
    fun `putRules sends the rules as a bare array`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        CloudConnectorRepository(testApi(server)).putRules(
            "zone1",
            listOf(
                CloudConnectorRule(
                    expression = "true",
                    provider = "aws_s3",
                    parameters = CloudConnectorParameters(host = "b.s3.amazonaws.com")
                )
            )
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.body.readUtf8()).startsWith("[")
    }

    @Test
    fun `listCustomPages parses the state and required tokens`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"waf_block","description":"WAF Block","state":"default",
                    "required_tokens":["::CLOUDFLARE_ERROR_1000S_BOX::"]}]}"""
            )
        )

        val result = CustomPagesRepository(testApi(server)).listPages("zone1")

        val page = (result as ApiResult.Success).data.single()
        assertThat(page.state).isEqualTo("default")
        assertThat(page.requiredTokens).hasSize(1)
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/custom_pages")
    }

    @Test
    fun `revert sends an explicit null url, which Moshi would otherwise drop`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"waf_block","state":"default"}}"""))

        CustomPagesRepository(testApi(server)).revert("zone1", "waf_block")

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"url\":null")
        assertThat(body).contains("\"state\":\"default\"")
    }

    @Test
    fun `customize sends the hosted page's url`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"waf_block","state":"customized"}}"""))

        CustomPagesRepository(testApi(server)).customize("zone1", "waf_block", "https://example.com/e.html")

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"url\":\"https://example.com/e.html\"")
        assertThat(body).contains("\"state\":\"customized\"")
    }

    @Test
    fun `getZarazConfig parses the tools map and privacy settings`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"zarazVersion":42,
                    "tools":{"t1":{"name":"Google Analytics","type":"component","enabled":true}},
                    "triggers":{"g1":{"name":"Pageview"}},
                    "settings":{"hideIPAddress":true,"autoInjectScript":true}}}"""
            )
        )

        val result = ZarazRepository(testApi(server)).getConfig("zone1")

        val config = (result as ApiResult.Success).data
        assertThat(config.zarazVersion).isEqualTo(42)
        assertThat(config.tools?.get("t1")?.name).isEqualTo("Google Analytics")
        assertThat(config.settings?.hideIPAddress).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/zones/zone1/settings/zaraz/config")
    }
}
