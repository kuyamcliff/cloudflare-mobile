package dev.cfmobile.app.ui.images

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.MainDispatcherRule
import dev.cfmobile.app.data.remote.dto.ImageVariant
import dev.cfmobile.app.data.remote.dto.ImageVariantOptions
import dev.cfmobile.app.data.remote.testApi
import dev.cfmobile.app.data.repository.ImagesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The variants and signing-key tabs added alongside the image inventory. */
class ImagesVariantsTest {

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

    private fun viewModel() = ImagesViewModel("acct1", ImagesRepository(testApi(server)))

    /** The four calls one load makes: images, stats, variants, keys. */
    private fun enqueueLoad(variants: String = "", keys: String = "") {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"images":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"count":{"current":0}}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"variants":{$variants}}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"keys":[$keys]}}"""))
    }

    private suspend fun ImagesViewModel.awaitLoaded() =
        uiState.first { it.images !is UiState.Loading && it.signingKeys !is UiState.Loading }

    @Test
    fun `a variant name has to survive being a URL segment`() {
        val valid = VariantFormState(id = "thumbnail", width = "200", height = "200")

        assertThat(validateVariantForm(valid)).isNull()
        assertThat(validateVariantForm(valid.copy(id = ""))).contains("name is required")
        assertThat(validateVariantForm(valid.copy(id = "my thumbnail"))).contains("delivery URL")
        assertThat(validateVariantForm(valid.copy(width = "0"))).contains("Width")
        assertThat(validateVariantForm(valid.copy(height = "tall"))).contains("Height")
    }

    @Test
    fun `keeping metadata is an explicit choice, since EXIF can carry a location`() {
        val form = VariantFormState(id = "t", width = "200", height = "200")

        assertThat(buildVariantOptions(form).metadata).isEqualTo("none")
        assertThat(buildVariantOptions(form.copy(keepMetadata = true)).metadata).isEqualTo("keep")
    }

    @Test
    fun `an unknown fit mode falls back rather than crashing`() {
        assertThat(variantFitFromValue("cover")).isEqualTo(VariantFit.COVER)
        assertThat(variantFitFromValue("squish")).isEqualTo(VariantFit.SCALE_DOWN)
        assertThat(variantFitFromValue(null)).isEqualTo(VariantFit.SCALE_DOWN)
    }

    @Test
    fun `variantSummary says whether the size is public`() {
        val variant = ImageVariant(
            id = "thumbnail",
            options = ImageVariantOptions(fit = "cover", width = 200, height = 200),
            neverRequireSignedUrls = true
        )

        assertThat(variantSummary(variant)).isEqualTo("200×200 · cover · public")
        assertThat(variantSummary(variant.copy(neverRequireSignedUrls = false)))
            .isEqualTo("200×200 · cover · signed URLs required")
    }

    @Test
    fun `variants arrive keyed by id and are flattened into a list`() = runTest {
        enqueueLoad(
            variants = """"thumbnail":{"options":{"fit":"cover","width":200,"height":200,"metadata":"none"}}"""
        )

        val state = viewModel().awaitLoaded()

        val variant = (state.variants as UiState.Data).value.single()
        // The id lives in the map key, not in the object.
        assertThat(variant.id).isEqualTo("thumbnail")
        assertThat(variant.options?.width).isEqualTo(200)
    }

    @Test
    fun `a signing key's value never reaches the UI`() = runTest {
        // Even when Cloudflare sends one, the repository drops it.
        enqueueLoad(keys = """{"name":"default","value":"super-secret"}""")

        val state = viewModel().awaitLoaded()

        val key = (state.signingKeys as UiState.Data).value.single()
        assertThat(key.name).isEqualTo("default")
        assertThat(key.value).isNull()
    }

    @Test
    fun `creating a variant posts its options`() = runTest {
        enqueueLoad()
        val vm = viewModel()
        vm.awaitLoaded()
        server.enqueue(
            MockResponse().setBody("""{"success":true,"errors":[],"result":{"variant":{"id":"thumbnail"}}}""")
        )
        enqueueLoad()

        vm.openVariantForm()
        vm.updateVariantForm { it.copy(id = "thumbnail", width = "200", height = "200", fit = VariantFit.COVER) }
        vm.saveVariant()
        vm.uiState.first { it.variantForm == null }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        val post = requests.single { it.method == "POST" }
        assertThat(post.path).isEqualTo("/accounts/acct1/images/v1/variants")
        val body = post.body.readUtf8()
        assertThat(body).contains("\"id\":\"thumbnail\"")
        assertThat(body).contains("\"fit\":\"cover\"")
    }

    @Test
    fun `an invalid variant never reaches the network`() = runTest {
        enqueueLoad()
        val vm = viewModel()
        vm.awaitLoaded()
        val before = server.requestCount

        vm.openVariantForm()
        vm.updateVariantForm { it.copy(id = "my thumbnail", width = "200", height = "200") }
        vm.saveVariant()

        assertThat(server.requestCount).isEqualTo(before)
        assertThat(vm.uiState.value.variantForm?.error).contains("delivery URL")
    }

    @Test
    fun `deleting a variant addresses it by name`() = runTest {
        enqueueLoad(variants = """"thumbnail":{"options":{"fit":"cover","width":200,"height":200,"metadata":"none"}}""")
        val vm = viewModel()
        val variant = (vm.awaitLoaded().variants as UiState.Data).value.single()
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{}}"""))
        enqueueLoad()

        vm.deleteVariant(variant)
        vm.uiState.first { it.deletingId == null && !it.isRefreshing }

        val requests = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
        assertThat(requests.single { it.method == "DELETE" }.path)
            .isEqualTo("/accounts/acct1/images/v1/variants/thumbnail")
    }

    @Test
    fun `a variants call the plan doesn't allow leaves the image list usable`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"images":[{"id":"i1"}]}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"count":{"current":1}}}"""))
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"success":false,"errors":[{"code":10000,"message":"Not entitled"}],"result":null}""")
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"errors":[],"result":{"keys":[]}}"""))

        val state = viewModel().awaitLoaded()

        assertThat((state.images as UiState.Data).value).hasSize(1)
        assertThat(state.variants).isInstanceOf(UiState.Error::class.java)
    }
}
