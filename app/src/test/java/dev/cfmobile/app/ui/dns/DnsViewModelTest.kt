package dev.cfmobile.app.ui.dns

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.DnsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DnsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var viewModel: DnsViewModel

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        viewModel = DnsViewModel("zone1", DnsRepository(testApi(server)))
    }

    @After
    fun tearDown() = server.shutdown()

    // The initial list load is a real (if local) network round trip, so it resolves
    // asynchronously - tests await it explicitly instead of racing ahead of it.
    private suspend fun awaitLoaded() = viewModel.uiState.first { it.records !is UiState.Loading }

    @Test
    fun `starts with an empty record list`() = runTest {
        val state = awaitLoaded()

        assertThat(state.records).isInstanceOf(UiState.Data::class.java)
        assertThat((state.records as UiState.Data).value).isEmpty()
    }

    @Test
    fun `openAddForm shows a blank A record form`() = runTest {
        awaitLoaded()
        viewModel.openAddForm()

        val form = viewModel.uiState.value.form
        assertThat(form).isNotNull()
        assertThat(form!!.type).isEqualTo("A")
        assertThat(form.editingId).isNull()
    }

    @Test
    fun `save rejects a blank name or content before calling the network`() = runTest {
        awaitLoaded()
        val requestsBeforeSave = server.requestCount
        viewModel.openAddForm()

        viewModel.save()

        assertThat(viewModel.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBeforeSave)
    }

    @Test
    fun `save creates the record then refreshes and closes the form`() = runTest {
        awaitLoaded()
        viewModel.openAddForm()
        viewModel.updateForm { it.copy(name = "www", content = "203.0.113.5") }

        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"id":"new1","type":"A","name":"www","content":"203.0.113.5","ttl":1}}"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"new1","type":"A","name":"www","content":"203.0.113.5","ttl":1}]}"""
            )
        )

        viewModel.save()
        val state = viewModel.uiState.first { it.form == null && (it.records as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat(state.form).isNull()
        assertThat((state.records as UiState.Data).value).hasSize(1)
    }

    @Test
    fun `editing an existing record pre-fills the form`() = runTest {
        awaitLoaded()
        val record = dev.cfmobile.app.data.remote.dto.DnsRecord(
            id = "rec1", type = "CNAME", name = "www", content = "target.example.com", ttl = 300, proxied = true
        )

        viewModel.openEditForm(record)

        val form = viewModel.uiState.value.form!!
        assertThat(form.editingId).isEqualTo("rec1")
        assertThat(form.type).isEqualTo("CNAME")
        assertThat(form.proxied).isTrue()
    }

    @Test
    fun `editing an SRV record pre-fills its structured data fields`() = runTest {
        awaitLoaded()
        val record = dev.cfmobile.app.data.remote.dto.DnsRecord(
            id = "rec2", type = "SRV", name = "_sip._tcp", ttl = 300,
            data = dev.cfmobile.app.data.remote.dto.DnsRecordData(priority = 10, weight = 5, port = 5060, target = "sip.example.com")
        )

        viewModel.openEditForm(record)

        val form = viewModel.uiState.value.form!!
        assertThat(form.srvPriority).isEqualTo("10")
        assertThat(form.srvWeight).isEqualTo("5")
        assertThat(form.srvPort).isEqualTo("5060")
        assertThat(form.srvTarget).isEqualTo("sip.example.com")
    }

    @Test
    fun `toggling a record's selection enters and exits selection mode`() = runTest {
        awaitLoaded()

        viewModel.toggleSelection("rec1")
        assertThat(viewModel.uiState.value.isSelectionMode).isTrue()
        assertThat(viewModel.uiState.value.selectedIds).containsExactly("rec1")

        viewModel.toggleSelection("rec1")
        assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
    }

    @Test
    fun `clearSelection exits selection mode without deleting anything`() = runTest {
        awaitLoaded()
        viewModel.toggleSelection("rec1")
        viewModel.toggleSelection("rec2")

        viewModel.clearSelection()

        assertThat(viewModel.uiState.value.selectedIds).isEmpty()
        assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
    }

    @Test
    fun `batchDelete clears the selection and refreshes on success`() = runTest {
        awaitLoaded()
        viewModel.toggleSelection("rec1")
        viewModel.toggleSelection("rec2")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"deletes":[{"id":"rec1"},{"id":"rec2"}]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        viewModel.batchDelete()
        val state = viewModel.uiState.first { !it.isBatchDeleting && it.selectedIds.isEmpty() }

        assertThat(state.selectedIds).isEmpty()
    }

    @Test
    fun `batchDelete on an empty selection makes no request`() = runTest {
        awaitLoaded()
        val requestsBefore = server.requestCount

        viewModel.batchDelete()

        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `exportZoneFile surfaces the exported text for the screen to share`() = runTest {
        awaitLoaded()
        server.enqueue(MockResponse().setBody("example.com.\t1\tIN\tA\t203.0.113.1\n"))

        viewModel.exportZoneFile()
        val state = viewModel.uiState.first { it.exportedZoneFile != null }

        assertThat(state.exportedZoneFile).contains("203.0.113.1")
    }

    @Test
    fun `importZoneFile reports how many records were imported and refreshes`() = runTest {
        awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"recs_added":2,"total_records_parsed":2}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"1","type":"A","name":"a","content":"1.1.1.1","ttl":1}]}"""))

        viewModel.importZoneFile("a.example.com.\t1\tIN\tA\t1.1.1.1\n")
        val state = viewModel.uiState.first { it.notice != null }

        assertThat(state.notice).contains("2")
    }
}
