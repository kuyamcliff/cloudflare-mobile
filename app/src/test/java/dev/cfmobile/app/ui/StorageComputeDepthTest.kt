package dev.cfmobile.app.ui

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.PagesDomain
import dev.cfmobile.app.data.remote.dto.PagesDomainVerification
import dev.cfmobile.app.data.remote.dto.QueueConsumer
import dev.cfmobile.app.data.remote.dto.QueueConsumerSettings
import dev.cfmobile.app.data.remote.dto.R2CorsAllowed
import dev.cfmobile.app.data.remote.dto.R2CorsRule
import dev.cfmobile.app.data.remote.dto.R2CustomDomain
import dev.cfmobile.app.data.remote.dto.R2CustomDomainStatus
import dev.cfmobile.app.data.remote.dto.R2LifecycleCondition
import dev.cfmobile.app.data.remote.dto.R2LifecycleConditions
import dev.cfmobile.app.data.remote.dto.R2LifecycleDeleteAction
import dev.cfmobile.app.data.remote.dto.R2LifecycleRule
import dev.cfmobile.app.data.remote.dto.WorkerDeployment
import dev.cfmobile.app.data.remote.dto.WorkerDomain
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.R2Repository
import dev.cfmobile.app.data.repository.WorkersRepository
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.pages.pagesDomainStatus
import dev.cfmobile.app.ui.pages.validatePagesDomain
import dev.cfmobile.app.ui.queues.consumerSummary
import dev.cfmobile.app.ui.r2.R2BucketViewModel
import dev.cfmobile.app.ui.r2.corsSummary
import dev.cfmobile.app.ui.r2.customDomainStatus
import dev.cfmobile.app.ui.r2.lifecycleSummary
import dev.cfmobile.app.ui.workers.SecretFormState
import dev.cfmobile.app.ui.workers.WorkerDomainFormState
import dev.cfmobile.app.ui.workers.WorkerSecretsViewModel
import dev.cfmobile.app.ui.workers.deploymentSummary
import dev.cfmobile.app.ui.workers.validateSecretForm
import dev.cfmobile.app.ui.workers.validateWorkerDomainForm
import dev.cfmobile.app.ui.workers.workerDomainSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** R2 bucket configuration, Worker secrets and domains, Pages domains, queue consumers. */
class StorageComputeDepthTest {

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

    // ---- R2 bucket configuration ----

    @Test
    fun `a bucket with no CORS policy reads as empty rather than failing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"errors":[],"result":null}"""))

        val result = R2Repository(testApi(server)).getCors("acct1", "bucket")

        // Cloudflare 404s a bucket that has never had a CORS policy; that isn't an error.
        assertThat((result as dev.cfmobile.app.data.remote.ApiResult.Success).data).isEmpty()
    }

    @Test
    fun `corsSummary reads as who may call the bucket`() {
        val rule = R2CorsRule(
            allowed = R2CorsAllowed(origins = listOf("https://a.com"), methods = listOf("GET", "PUT"))
        )

        assertThat(corsSummary(rule)).isEqualTo("https://a.com → GET, PUT")
        assertThat(corsSummary(R2CorsRule())).isEqualTo("no origins → no methods")
    }

    @Test
    fun `lifecycleSummary converts Cloudflare's seconds into days`() {
        val rule = R2LifecycleRule(
            id = "l1",
            conditions = R2LifecycleConditions(prefix = "tmp/"),
            deleteTransition = R2LifecycleDeleteAction(R2LifecycleCondition(maxAge = 2_592_000))
        )

        assertThat(lifecycleSummary(rule)).isEqualTo("prefix tmp/ · delete after 30 days")
        assertThat(lifecycleSummary(R2LifecycleRule(id = "l2", enabled = false))).contains("disabled")
    }

    @Test
    fun `customDomainStatus reports ownership and SSL separately`() {
        val domain = R2CustomDomain(
            domain = "cdn.a.com",
            enabled = true,
            status = R2CustomDomainStatus(ownership = "verified", ssl = "active")
        )

        assertThat(customDomainStatus(domain)).isEqualTo("Enabled · ownership verified · SSL active")
    }

    @Test
    fun `the bucket screen loads all four surfaces in one pass`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/domains/custom") -> MockResponse().setBody(
                        """{"success":true,"errors":[],"result":{"domains":[{"domain":"cdn.a.com","enabled":true}]}}"""
                    )
                    path.endsWith("/domains/managed") -> MockResponse().setBody(
                        """{"success":true,"errors":[],"result":{"enabled":false,"domain":"pub-x.r2.dev"}}"""
                    )
                    path.endsWith("/cors") -> MockResponse().setBody(
                        """{"success":true,"errors":[],"result":{"rules":[{"allowed":{"origins":["https://a.com"],"methods":["GET"]}}]}}"""
                    )
                    else -> MockResponse().setBody(
                        """{"success":true,"errors":[],"result":{"rules":[{"id":"l1","enabled":true}]}}"""
                    )
                }
            }
        }
        val vm = R2BucketViewModel("acct1", "bucket", R2Repository(testApi(server)))

        val state = vm.uiState.first { it.customDomains !is UiState.Loading && it.lifecycleRules.isNotEmpty() }

        assertThat((state.customDomains as UiState.Data).value.single().domain).isEqualTo("cdn.a.com")
        assertThat(state.managedDomain?.enabled).isFalse()
        assertThat(state.corsRules).hasSize(1)
    }

    // ---- Worker secrets ----

    @Test
    fun `a secret name follows environment-variable rules`() {
        assertThat(validateSecretForm(SecretFormState(name = "", value = "x"))).contains("name is required")
        assertThat(validateSecretForm(SecretFormState(name = "1BAD", value = "x"))).contains("starting with a letter")
        assertThat(validateSecretForm(SecretFormState(name = "API-TOKEN", value = "x"))).contains("underscores")
        assertThat(validateSecretForm(SecretFormState(name = "API_TOKEN", value = ""))).contains("value is required")
        assertThat(validateSecretForm(SecretFormState(name = "API_TOKEN", value = "s3cret"))).isNull()
    }

    @Test
    fun `saving a secret sends it once and keeps nothing afterwards`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[]}"""))
        val vm = WorkerSecretsViewModel("acct1", "my-worker", WorkersRepository(testApi(server)))
        vm.uiState.first { it.secrets !is UiState.Loading }
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"name":"API_TOKEN","type":"secret_text"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"API_TOKEN","type":"secret_text"}]}"""))

        vm.openCreateForm()
        vm.updateForm { it.copy(name = "API_TOKEN", value = "s3cret") }
        vm.save()
        val state = vm.uiState.first { it.form == null && (it.secrets as? UiState.Data)?.value?.isNotEmpty() == true }

        // The form is dropped whole, which is what clears the value from memory.
        assertThat(state.form).isNull()
        server.takeRequest()
        val put = server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.path).isEqualTo("/accounts/acct1/workers/scripts/my-worker/secrets")
        assertThat(put.body.readUtf8()).contains("\"text\":\"s3cret\"")
    }

    @Test
    fun `replacing a secret locks the name to the one being replaced`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":[{"name":"API_TOKEN"}]}"""))
        val vm = WorkerSecretsViewModel("acct1", "my-worker", WorkersRepository(testApi(server)))
        val secret = (vm.uiState.first { it.secrets !is UiState.Loading }.secrets as UiState.Data).value.single()

        vm.openReplaceForm(secret)

        assertThat(vm.uiState.value.form?.name).isEqualTo("API_TOKEN")
        assertThat(vm.uiState.value.form?.isReplacing).isTrue()
        // The old value is never fetched, so the field starts empty.
        assertThat(vm.uiState.value.form?.value).isEmpty()
    }

    // ---- Worker domains and deployments ----

    @Test
    fun `a worker domain needs a hostname, a zone, and a worker`() {
        val form = WorkerDomainFormState(hostname = "api.example.com", zoneId = "z1", service = "w")
        assertThat(validateWorkerDomainForm(form)).isNull()
        assertThat(validateWorkerDomainForm(form.copy(hostname = "not a host"))).contains("hostname")
        assertThat(validateWorkerDomainForm(form.copy(zoneId = null))).contains("zone")
        assertThat(validateWorkerDomainForm(form.copy(service = ""))).contains("Worker")
    }

    @Test
    fun `workerDomainSummary names the worker and its zone`() {
        val domain = WorkerDomain(id = "d1", hostname = "api.a.com", service = "my-worker", zoneName = "a.com")

        assertThat(workerDomainSummary(domain)).isEqualTo("my-worker · a.com")
    }

    @Test
    fun `deploymentSummary uses whatever the deployment recorded`() {
        val deployment = WorkerDeployment(id = "d1", source = "wrangler", createdOn = "2026-01-02")

        assertThat(deploymentSummary(deployment)).isEqualTo("wrangler · 2026-01-02")
        assertThat(deploymentSummary(WorkerDeployment(id = "bare"))).isEqualTo("bare")
    }

    // ---- Pages domains and queue consumers ----

    @Test
    fun `a pages domain has to look like a domain`() {
        assertThat(validatePagesDomain("")).contains("required")
        assertThat(validatePagesDomain("not a domain")).contains("e.g.")
        assertThat(validatePagesDomain("www.example.com")).isNull()
    }

    @Test
    fun `pagesDomainStatus surfaces a verification error rather than hiding it`() {
        assertThat(pagesDomainStatus(PagesDomain(name = "a.com", status = "active"))).isEqualTo("Active")
        assertThat(pagesDomainStatus(PagesDomain(name = "a.com"))).isEqualTo("Pending")
        assertThat(
            pagesDomainStatus(
                PagesDomain(
                    name = "a.com",
                    status = "pending",
                    verificationData = PagesDomainVerification(errorMessage = "CNAME not found")
                )
            )
        ).contains("CNAME not found")
    }

    @Test
    fun `consumerSummary reports the batching settings that shape how it's called`() {
        val consumer = QueueConsumer(
            scriptName = "worker",
            settings = QueueConsumerSettings(batchSize = 10, maxRetries = 3),
            deadLetterQueue = "dlq"
        )

        assertThat(consumerSummary(consumer)).isEqualTo("batches of 10 · up to 3 retries · dead letter: dlq")
        assertThat(consumerSummary(QueueConsumer(type = "worker"))).isEqualTo("worker")
    }
}
