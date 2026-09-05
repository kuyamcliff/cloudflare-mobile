package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AnalyticsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AnalyticsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = AnalyticsRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getDashboard parses totals and passes a since-until window as query params`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":{"totals":{"requests":{"all":15000},"bandwidth":{"all":204800},"threats":{"all":42},"uniques":{"all":900}}}}"""
            )
        )

        val result = repository.getDashboard("zone1", sinceHours = 24)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val totals = (result as ApiResult.Success).data.totals!!
        assertThat(totals.requests?.all).isEqualTo(15000.0)
        assertThat(totals.threats?.all).isEqualTo(42.0)

        val request = server.takeRequest()
        assertThat(request.path).contains("since=")
        assertThat(request.path).contains("until=")
    }
}
