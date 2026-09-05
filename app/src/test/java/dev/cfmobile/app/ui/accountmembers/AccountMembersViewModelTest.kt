package dev.cfmobile.app.ui.accountmembers

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.AccountMembersRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccountMembersViewModelTest {

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

    private suspend fun AccountMembersViewModel.awaitLoaded() = uiState.first { it.members !is UiState.Loading }

    private val rolesJson = """{"success":true,"errors":[],"result":[{"id":"r1","name":"Administrator","description":"Full access"}]}"""

    @Test
    fun `loads members and roles together`() = runTest {
        server.enqueue(rolesJson.toResponse())
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[{"id":"m1","user":{"id":"u1","email":"a@example.com"},"status":"accepted","roles":[]}]}"""
            )
        )
        val viewModel = AccountMembersViewModel("acct1", AccountMembersRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat((state.members as UiState.Data).value).hasSize(1)
        assertThat(state.roles).hasSize(1)
        assertThat(state.roles.single().name).isEqualTo("Administrator")
    }

    @Test
    fun `a roles-fetch failure still lets members load, with an empty role picker`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"success":false,"errors":[],"result":null}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val viewModel = AccountMembersViewModel("acct1", AccountMembersRepository(testApi(server)))

        val state = viewModel.awaitLoaded()

        assertThat(state.members).isInstanceOf(UiState.Data::class.java)
        assertThat(state.roles).isEmpty()
    }

    @Test
    fun `invite rejects a blank email before calling the network`() = runTest {
        server.enqueue(rolesJson.toResponse())
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val viewModel = AccountMembersViewModel("acct1", AccountMembersRepository(testApi(server)))
        viewModel.awaitLoaded()
        viewModel.openInviteForm()
        val requestsBefore = server.requestCount

        viewModel.invite()

        assertThat(viewModel.uiState.value.form?.error).isEqualTo("Email is required")
        assertThat(server.requestCount).isEqualTo(requestsBefore)
    }

    @Test
    fun `invite rejects no selected roles`() = runTest {
        server.enqueue(rolesJson.toResponse())
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val viewModel = AccountMembersViewModel("acct1", AccountMembersRepository(testApi(server)))
        viewModel.awaitLoaded()
        viewModel.openInviteForm()
        viewModel.updateForm { it.copy(email = "new@example.com") }

        viewModel.invite()

        assertThat(viewModel.uiState.value.form?.error).isEqualTo("Select at least one role")
    }

    @Test
    fun `a valid invite creates the member and closes the form`() = runTest {
        server.enqueue(rolesJson.toResponse())
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val viewModel = AccountMembersViewModel("acct1", AccountMembersRepository(testApi(server)))
        viewModel.awaitLoaded()
        viewModel.openInviteForm()
        viewModel.updateForm { it.copy(email = "new@example.com") }
        viewModel.toggleRole("r1")

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"m2","user":{"id":"u2","email":"new@example.com"},"status":"pending","roles":[]}}"""))
        server.enqueue(rolesJson.toResponse())
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"m2","user":{"id":"u2","email":"new@example.com"},"status":"pending","roles":[]}]}"""))

        viewModel.invite()
        val state = viewModel.uiState.first { it.form == null && (it.members as? UiState.Data)?.value?.isNotEmpty() == true }

        assertThat((state.members as UiState.Data).value.map { it.user.email }).containsExactly("new@example.com")
    }

    @Test
    fun `remove clears the removing flag and refreshes`() = runTest {
        server.enqueue(rolesJson.toResponse())
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":[{"id":"m1","user":{"id":"u1","email":"a@example.com"},"status":"accepted","roles":[]}]}""")
        )
        val viewModel = AccountMembersViewModel("acct1", AccountMembersRepository(testApi(server)))
        val loaded = viewModel.awaitLoaded()
        val member = (loaded.members as UiState.Data).value.first()

        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        server.enqueue(rolesJson.toResponse())
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))

        viewModel.remove(member)
        val state = viewModel.uiState.first { (it.members as? UiState.Data)?.value?.isEmpty() == true }

        assertThat(state.removingId).isNull()
        assertThat((state.members as UiState.Data).value).isEmpty()
    }

    private fun String.toResponse() = MockResponse().setBody(this)
}
