package dev.cfmobile.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.local.TokenStore
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.testVerifierApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Uses the plain android.app.Application instead of CfApplication so Robolectric never
// constructs the real AppContainer (which builds an Android-Keystore-backed
// EncryptedSharedPreferences that Robolectric's JVM can't provide).
@Config(application = Application::class)
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepository
    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        tokenStore = TokenStore(context.getSharedPreferences("test_tokens", android.content.Context.MODE_PRIVATE))
        repository = AuthRepository(testVerifierApi(server), tokenStore)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `addToken only saves the token after Cloudflare confirms it's valid`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"tok1","status":"active"}}"""))

        val result = repository.addToken("My site", "cf-token-123")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(tokenStore.getAll()).hasSize(1)
        assertThat(tokenStore.getActive()?.token).isEqualTo("cf-token-123")

        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer cf-token-123")
    }

    @Test
    fun `addToken does not persist anything when Cloudflare rejects it`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"success":false,"errors":[{"code":1000,"message":"Invalid API Token"}],"result":null}""")
        )

        val result = repository.addToken("Bad token", "not-a-real-token")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat(tokenStore.getAll()).isEmpty()
    }

    @Test
    fun `blank token is rejected before any network call`() = runBlocking {
        val result = repository.addToken("label", "   ")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }
}
