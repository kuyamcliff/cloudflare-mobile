package dev.cfmobile.app.data.repository

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.DnsRecordWrite
import dev.cfmobile.app.data.remote.testApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class DnsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: DnsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = DnsRepository(testApi(server))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listRecords parses multiple record types`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"errors":[],"result":[
                    {"id":"1","type":"A","name":"example.com","content":"203.0.113.1","ttl":1,"proxied":true},
                    {"id":"2","type":"TXT","name":"example.com","content":"v=spf1 -all","ttl":3600}
                ]}"""
            )
        )

        val result = repository.listRecords("zone1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val records = (result as ApiResult.Success).data
        assertThat(records).hasSize(2)
        assertThat(records[0].proxied).isTrue()
        assertThat(records[1].type).isEqualTo("TXT")
    }

    @Test
    fun `createRecord sends the expected request body and path`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"id":"new1","type":"A","name":"www.example.com","content":"203.0.113.9","ttl":1,"proxied":false}}"""))

        val result = repository.createRecord("zone1", DnsRecordWrite(type = "A", name = "www.example.com", content = "203.0.113.9", ttl = 1, proxied = false))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/dns_records")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body.readUtf8()).contains("\"content\":\"203.0.113.9\"")
    }

    @Test
    fun `deleteRecord maps a Cloudflare error to Failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"success":false,"errors":[{"code":81044,"message":"Record does not exist"}],"result":null}""")
        )

        val result = repository.deleteRecord("zone1", "missing-id")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).message).contains("Record does not exist")
    }

    @Test
    fun `batchDeleteRecords sends every selected id in a single request`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"deletes":[{"id":"1"},{"id":"2"}]}}"""))

        val result = repository.batchDeleteRecords("zone1", listOf("1", "2"))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/dns_records/batch")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"id\":\"1\"")
        assertThat(body).contains("\"id\":\"2\"")
    }

    @Test
    fun `exportZoneFile returns the raw BIND text body, not JSON`() = runBlocking {
        val bindText = "example.com.\t1\tIN\tA\t203.0.113.1\n"
        server.enqueue(MockResponse().setBody(bindText).setHeader("Content-Type", "text/plain"))

        val result = repository.exportZoneFile("zone1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isEqualTo(bindText)
    }

    @Test
    fun `exportZoneFile surfaces a Failure on a non-2xx response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("unauthorized"))

        val result = repository.exportZoneFile("zone1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
    }

    @Test
    fun `importZoneFile uploads the file as multipart form data`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"recs_added":3,"total_records_parsed":3}}"""))

        val result = repository.importZoneFile("zone1", "example.com.\t1\tIN\tA\t203.0.113.1\n", proxied = false)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.recsAdded).isEqualTo(3)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/zones/zone1/dns_records/import")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body.readUtf8()).contains("203.0.113.1")
    }
}
