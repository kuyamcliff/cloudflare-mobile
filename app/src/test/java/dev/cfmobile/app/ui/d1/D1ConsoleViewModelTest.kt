package dev.cfmobile.app.ui.d1

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.D1QueryMeta
import dev.cfmobile.app.data.remote.dto.D1QueryResult
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.D1Repository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class D1ConsoleViewModelTest {

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

    private fun viewModel() = D1ConsoleViewModel("acct1", "db1", D1Repository(testApi(server)))

    private suspend fun D1ConsoleViewModel.awaitIdle() = uiState.first { !it.isRunning }

    @Test
    fun `isMutatingSql flags writes and leaves reads alone`() {
        assertThat(isMutatingSql("SELECT * FROM users")).isFalse()
        assertThat(isMutatingSql("  select 1 ")).isFalse()
        assertThat(isMutatingSql("WITH x AS (SELECT 1) SELECT * FROM x")).isFalse()
        assertThat(isMutatingSql("delete from users")).isTrue()
        assertThat(isMutatingSql("  UPDATE users SET a = 1")).isTrue()
        assertThat(isMutatingSql("DROP TABLE users")).isTrue()
        assertThat(isMutatingSql("(INSERT INTO t VALUES (1))")).isTrue()
    }

    @Test
    fun `columnsOf keeps columns a later sparse row introduces`() {
        val result = D1QueryResult(
            success = true,
            results = listOf(mapOf("id" to 1, "name" to "ada"), mapOf("id" to 2, "email" to "x@y"))
        )

        assertThat(columnsOf(result)).containsExactly("id", "name", "email").inOrder()
    }

    @Test
    fun `formatCell distinguishes null from empty and trims whole doubles`() {
        assertThat(formatCell(null)).isEqualTo("NULL")
        assertThat(formatCell("")).isEqualTo("")
        assertThat(formatCell(3L)).isEqualTo("3")
        // A float column still decodes as Double; a whole one shouldn't render as "3.0".
        assertThat(formatCell(3.0)).isEqualTo("3")
        assertThat(formatCell(3.5)).isEqualTo("3.5")
    }

    @Test
    fun `resultSummary reports rows, writes, and duration from what Cloudflare returned`() {
        val read = D1QueryResult(success = true, results = listOf(mapOf("a" to 1)), meta = D1QueryMeta(duration = 1.5))
        // The duration's decimal separator follows the device locale, so only its presence is
        // asserted here.
        assertThat(resultSummary(read)).startsWith("1 row · ")
        assertThat(resultSummary(read)).endsWith("ms")

        val write = D1QueryResult(success = true, results = emptyList(), meta = D1QueryMeta(rowsWritten = 4))
        assertThat(resultSummary(write)).isEqualTo("0 rows · 4 written")

        assertThat(resultSummary(D1QueryResult(success = true))).isEqualTo("OK")
    }

    @Test
    fun `run rejects blank SQL without calling the network`() = runTest {
        val vm = viewModel()

        vm.run()

        assertThat(vm.uiState.value.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a successful run stores the results and the SQL that produced them`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"success":true,"results":[{"id":1}]}]}""")
        )
        val vm = viewModel()

        vm.updateSql("SELECT id FROM t")
        vm.run()
        val state = vm.awaitIdle()

        // Whole numbers decode as Long (see AnyJsonAdapter), which is what keeps an id column
        // from rendering as "1.0".
        assertThat(state.results.single().results?.single()?.get("id")).isEqualTo(1L)
        assertThat(state.ranSql).isEqualTo("SELECT id FROM t")
        assertThat(state.error).isNull()
    }

    @Test
    fun `a failed run surfaces the error and keeps the SQL for editing`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"errors":[{"code":7500,"message":"no such table: t"}],"result":null}""")
        )
        val vm = viewModel()

        vm.updateSql("SELECT * FROM t")
        vm.run()
        val state = vm.awaitIdle()

        assertThat(state.error).contains("no such table")
        assertThat(state.sql).isEqualTo("SELECT * FROM t")
    }

    @Test
    fun `clear drops results without touching the editor`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"success":true,"results":[]}]}"""))
        val vm = viewModel()
        vm.updateSql("SELECT 1")
        vm.run()
        vm.awaitIdle()

        vm.clear()

        assertThat(vm.uiState.value.results).isEmpty()
        assertThat(vm.uiState.value.sql).isEqualTo("SELECT 1")
    }
}
