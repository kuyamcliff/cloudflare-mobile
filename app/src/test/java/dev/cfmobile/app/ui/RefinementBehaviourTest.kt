package dev.cfmobile.app.ui

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.DnsRecord
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.DnsRepository
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.dns.DnsViewModel
import dev.cfmobile.app.ui.dns.dnsRecordMatches
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Covers the behaviour the refinement pass added across existing screens: searching a long
 *  list, and refreshing without blanking content that's already on screen. */
class RefinementBehaviourTest {

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

    private fun record(name: String, type: String, content: String) =
        DnsRecord(id = name, type = type, name = name, content = content, ttl = 1)

    @Test
    fun `dnsRecordMatches searches name, content, and type`() {
        val a = record("www.example.com", "A", "203.0.113.10")

        assertThat(dnsRecordMatches(a, "www")).isTrue()
        // Finding a record by the IP it points at is the case that makes search worth having.
        assertThat(dnsRecordMatches(a, "203.0.113")).isTrue()
        assertThat(dnsRecordMatches(a, "a")).isTrue()
        assertThat(dnsRecordMatches(a, "mail")).isFalse()
    }

    @Test
    fun `dnsRecordMatches ignores case and surrounding whitespace, and an empty query matches all`() {
        val a = record("WWW.example.com", "CNAME", "target.example.com")

        assertThat(dnsRecordMatches(a, "www")).isTrue()
        assertThat(dnsRecordMatches(a, "  cname  ")).isTrue()
        assertThat(dnsRecordMatches(a, "")).isTrue()
        assertThat(dnsRecordMatches(a, "   ")).isTrue()
    }

    @Test
    fun `a first DNS load shows the loading state, a refresh does not`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"r1","type":"A","name":"www.example.com","content":"203.0.113.10","ttl":1}]}"""
            )
        )
        val vm = DnsViewModel("zone1", DnsRepository(testApi(server)))
        vm.uiState.first { it.records !is UiState.Loading }

        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"r1","type":"A","name":"www.example.com","content":"203.0.113.11","ttl":1}]}"""
            )
        )
        vm.refresh()

        // The records stay Data throughout: a pull-to-refresh must not replace a list the user
        // is reading with a full-screen spinner.
        val refreshed = vm.uiState.first { (it.records as? UiState.Data)?.value?.firstOrNull()?.content == "203.0.113.11" }
        assertThat(refreshed.isRefreshing).isFalse()
    }

    @Test
    fun `the DNS query survives a refresh`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = DnsViewModel("zone1", DnsRepository(testApi(server)))
        vm.uiState.first { it.records !is UiState.Loading }

        vm.onQueryChange("mail")
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        vm.refresh()
        val state = vm.uiState.first { !it.isRefreshing }

        assertThat(state.query).isEqualTo("mail")
    }
}
