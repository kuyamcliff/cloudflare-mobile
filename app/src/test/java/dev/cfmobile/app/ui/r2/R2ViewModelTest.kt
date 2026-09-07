package dev.cfmobile.app.ui.r2

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.R2Repository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class R2ViewModelTest {

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

    private fun viewModel() = R2ViewModel("acct1", R2Repository(testApi(server)))

    private suspend fun R2ViewModel.awaitLoaded() = uiState.first { it.buckets !is UiState.Loading }

    @Test
    fun `validateBucketName enforces S3-style naming rules`() {
        assertThat(validateBucketName("")).isEqualTo("Bucket name is required")
        assertThat(validateBucketName("AB")).isNotNull()
        assertThat(validateBucketName("-leading-hyphen")).isNotNull()
        assertThat(validateBucketName("trailing-hyphen-")).isNotNull()
        assertThat(validateBucketName("my-bucket")).isNull()
    }

    @Test
    fun `loads buckets on init`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"buckets":[{"name":"a"},{"name":"b"}]}}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.buckets as UiState.Data).value.map { it.name }).containsExactly("a", "b")
    }

    @Test
    fun `save rejects an invalid bucket name before calling the network`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"buckets":[]}}"""))
        val vm = viewModel()
        vm.awaitLoaded()
        val requestsBefore = server.requestCount

        vm.openForm()
        vm.updateForm { it.copy(name = "AB") }
        vm.save()

        assertThat(vm.uiState.value.form?.error).isNotNull()
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `a valid save creates the bucket and closes the form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"buckets":[]}}"""))
        val vm = viewModel()
        vm.awaitLoaded()

        vm.openForm()
        vm.updateForm { it.copy(name = "new-bucket") }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"name":"new-bucket"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"buckets":[{"name":"new-bucket"}]}}"""))

        vm.save()
        val state = vm.uiState.first { it.form == null && (it.buckets as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.buckets as UiState.Data).value.map { it.name }).containsExactly("new-bucket")
    }
}
