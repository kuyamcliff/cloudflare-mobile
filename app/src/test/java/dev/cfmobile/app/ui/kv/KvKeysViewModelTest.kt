package dev.cfmobile.app.ui.kv

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.KvKey
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.KvRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class KvKeysViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    /** "METHOD /path" for every request the fake namespace served. Exposed as a flow so a test
     *  can await a request having happened instead of racing the ViewModel's coroutines. */
    private val requestLog = MutableStateFlow<List<String>>(emptyList())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    /**
     * A fake namespace that actually holds keys: writes and deletes change what the next key
     * listing returns, which is what makes the rename assertions meaningful. Keyed by path
     * rather than queued in order, since the screen fires several different requests.
     */
    private fun serveNamespace(vararg initialKeys: String, value: String = "stored value") {
        val keys = LinkedHashSet(initialKeys.toList())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val keyName = path.substringAfter("/values/", "")
                val response = synchronized(keys) {
                    when {
                        path.endsWith("/keys") -> MockResponse().setBody(
                            """{"success":true,"errors":[],"result":[${keys.joinToString(",") { """{"name":"$it"}""" }}]}"""
                        )
                        request.method == "GET" -> MockResponse().setBody(value)
                        request.method == "PUT" -> {
                            keys.add(keyName)
                            MockResponse().setBody("""{"success":true,"errors":[],"result":{}}""")
                        }
                        request.method == "DELETE" -> {
                            keys.remove(keyName)
                            MockResponse().setBody("""{"success":true,"errors":[],"result":{}}""")
                        }
                        else -> MockResponse().setResponseCode(405)
                    }
                }
                requestLog.update { it + "${request.method} $path" }
                return response
            }
        }
    }

    private fun viewModel() = KvKeysViewModel("acct1", "ns1", KvRepository(testApi(server)))

    private suspend fun KvKeysViewModel.awaitLoaded() = uiState.first { it.keys !is UiState.Loading }

    private suspend fun KvKeysViewModel.awaitKeys(vararg names: String) =
        uiState.first { (it.keys as? UiState.Data)?.value?.map { key -> key.name } == names.toList() }

    @Test
    fun `validateKvKey rejects a blank or oversized key`() {
        assertThat(validateKvKey("")).isEqualTo("Key name is required")
        assertThat(validateKvKey("a".repeat(513))).contains("512")
        assertThat(validateKvKey("session:1")).isNull()
    }

    @Test
    fun `kvExpiryLabel is null for a key that never expires`() {
        assertThat(kvExpiryLabel(KvKey(name = "k"))).isNull()
        assertThat(kvExpiryLabel(KvKey(name = "k", expiration = 42))).contains("42")
    }

    @Test
    fun `loads the namespace's keys on init`() = runTest {
        serveNamespace("a", "b")

        val state = viewModel().awaitLoaded()

        assertThat((state.keys as UiState.Data).value.map { it.name }).containsExactly("a", "b")
    }

    @Test
    fun `opening a key for editing fetches its value`() = runTest {
        serveNamespace("a", value = "the value")
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openEditForm(KvKey(name = "a"))
        val state = vm.uiState.first { it.form?.isLoadingValue == false }

        assertThat(state.form?.value).isEqualTo("the value")
        assertThat(state.form?.isEditing).isTrue()
    }

    @Test
    fun `save rejects a blank key before calling the network`() = runTest {
        serveNamespace("a")
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.openCreateForm()
        vm.save()

        assertThat(vm.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `adding a key writes the value under that name`() = runTest {
        serveNamespace()
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openCreateForm()
        vm.updateForm { it.copy(key = "fresh", value = "v") }
        vm.save()
        vm.awaitKeys("fresh")

        assertThat(requestLog.value).contains("PUT /accounts/acct1/storage/kv/namespaces/ns1/values/fresh")
    }

    @Test
    fun `renaming a key writes the new name and removes the old one`() = runTest {
        serveNamespace("old")
        val vm = viewModel()
        vm.awaitLoaded()
        vm.openEditForm(KvKey(name = "old"))
        vm.uiState.first { it.form?.isLoadingValue == false }

        vm.updateForm { it.copy(key = "new") }
        vm.save()
        vm.awaitKeys("new")

        assertThat(requestLog.value).contains("DELETE /accounts/acct1/storage/kv/namespaces/ns1/values/old")
    }

    @Test
    fun `saving without renaming doesn't delete anything`() = runTest {
        serveNamespace("same")
        val vm = viewModel()
        vm.awaitLoaded()
        vm.openEditForm(KvKey(name = "same"))
        vm.uiState.first { it.form?.isLoadingValue == false }

        vm.updateForm { it.copy(value = "updated") }
        vm.save()
        vm.uiState.first { it.form == null }
        // The refresh that follows the write is the last request the save makes.
        requestLog.first { log -> log.count { it.endsWith("/keys") } == 2 }

        assertThat(requestLog.value.none { it.startsWith("DELETE") }).isTrue()
    }

    @Test
    fun `deleting a key removes it from the list`() = runTest {
        serveNamespace("doomed", "kept")
        val vm = viewModel()
        vm.awaitLoaded()

        vm.delete(KvKey(name = "doomed"))
        vm.awaitKeys("kept")

        assertThat(requestLog.value).contains("DELETE /accounts/acct1/storage/kv/namespaces/ns1/values/doomed")
    }
}
