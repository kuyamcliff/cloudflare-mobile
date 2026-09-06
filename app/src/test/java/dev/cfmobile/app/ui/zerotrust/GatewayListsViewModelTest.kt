package dev.cfmobile.app.ui.zerotrust

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.GatewayList
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.GatewayRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GatewayListsViewModelTest {

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

    private fun viewModel() = GatewayListsViewModel("acct1", GatewayRepository(testApi(server)))

    private suspend fun GatewayListsViewModel.awaitLoaded() = uiState.first { it.lists !is UiState.Loading }

    @Test
    fun `parseListItems drops blanks and duplicates and accepts commas`() {
        assertThat(parseListItems("a.com\n\nb.com\na.com\n")).containsExactly("a.com", "b.com").inOrder()
        assertThat(parseListItems("a.com, b.com")).containsExactly("a.com", "b.com").inOrder()
        assertThat(parseListItems("   ")).isEmpty()
    }

    @Test
    fun `a list needs a name and at least one entry`() {
        assertThat(validateGatewayListForm(GatewayListFormState(items = "a.com"))).contains("name")
        assertThat(validateGatewayListForm(GatewayListFormState(name = "blocked", items = "\n\n"))).contains("at least one")
        assertThat(validateGatewayListForm(GatewayListFormState(name = "blocked", items = "a.com"))).isNull()
    }

    @Test
    fun `gatewayListSubtitle counts entries and names the type`() {
        assertThat(gatewayListSubtitle(GatewayList(type = "DOMAIN", count = 1))).isEqualTo("Domains · 1 entry")
        assertThat(gatewayListSubtitle(GatewayList(type = "IP", count = 4))).isEqualTo("IP addresses · 4 entries")
        // A type this app doesn't know about is shown as Cloudflare named it.
        assertThat(gatewayListSubtitle(GatewayList(type = "NEW_TYPE"))).isEqualTo("NEW_TYPE · 0 entries")
    }

    @Test
    fun `loads lists on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"l1","name":"blocked","type":"DOMAIN","count":2}]}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.lists as UiState.Data).value.single().name).isEqualTo("blocked")
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/gateway/lists")
    }

    @Test
    fun `creating a list sends one item per parsed entry`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"l1","name":"blocked","type":"DOMAIN"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"l1","name":"blocked","type":"DOMAIN","count":2}]}"""))

        vm.openForm()
        vm.updateForm { it.copy(name = "blocked", type = GatewayListType.DOMAIN, items = "a.com\nb.com\na.com") }
        vm.save()
        vm.uiState.first { it.form == null && (it.lists as? UiState.Data)?.value?.isNotEmpty() == true }

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"type\":\"DOMAIN\"")
        assertThat(body).contains("\"value\":\"a.com\"")
        assertThat(body).contains("\"value\":\"b.com\"")
        // The duplicate entry was dropped before the request went out.
        assertThat(body.split("a.com").size - 1).isEqualTo(1)
    }

    @Test
    fun `opening a list fetches its entries separately`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"l1","name":"blocked","type":"DOMAIN","count":1}]}"""))
        val vm = viewModel()
        val list = (vm.awaitLoaded().lists as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"value":"a.com"}]}"""))

        vm.openDetail(list)
        val state = vm.uiState.first { it.detail?.items is UiState.Data }

        assertThat((state.detail?.items as UiState.Data).value.single().value).isEqualTo("a.com")
        server.takeRequest()
        assertThat(server.takeRequest().path).isEqualTo("/accounts/acct1/gateway/lists/l1/items")
    }

    @Test
    fun `deleting a list closes any open detail sheet for it`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"l1","name":"blocked","type":"DOMAIN"}]}"""))
        val vm = viewModel()
        val list = (vm.awaitLoaded().lists as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        vm.openDetail(list)
        vm.uiState.first { it.detail?.items is UiState.Data }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        vm.delete(list)
        val state = vm.uiState.first { it.deletingId == null && (it.lists as? UiState.Data)?.value?.isEmpty() == true }

        assertThat(state.detail).isNull()
    }
}
