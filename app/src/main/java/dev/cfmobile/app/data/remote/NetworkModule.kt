package dev.cfmobile.app.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the app's single OkHttp stack. Interceptor order matters and is fixed here:
 *
 * 1. [RequestHistoryInterceptor]: one sanitized history row per logical request.
 * 2. [RetryPolicyInterceptor]: Retry-After aware, idempotent-only retries.
 * 3. [AuthInterceptor]: attaches the token, only for the API host.
 * 4. [RedactingLogInterceptor]: debug-only, path-templated, no headers or bodies.
 *
 * Authorization is attached after retries are decided and logged after it is attached, but
 * the logger never reads headers, so no ordering can put the token in a log line.
 */
object NetworkModule {

    /** The one place the API version lives (spec: do not scatter `/v4/` through the code). */
    const val API_VERSION_PATH = "client/v4/"
    const val BASE_URL = "https://api.cloudflare.com/$API_VERSION_PATH"
    const val GRAPHQL_PATH = "graphql"

    fun createMoshi(): Moshi = Moshi.Builder()
        .add(Any::class.java, AnyJsonAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    class Options(
        val hosts: CloudflareHosts,
        val recorder: RequestRecorder? = null,
        val status: NetworkStatus? = null,
        val debugLogging: Boolean = false,
        val logSink: (String) -> Unit = {},
        val maxRetries: Int = 2
    )

    fun createClient(options: Options, activeTokenProvider: () -> String?): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(RequestHistoryInterceptor(options.hosts, options.recorder, options.status))
            .addInterceptor(RetryPolicyInterceptor(options.status, maxRetries = options.maxRetries))
            .addInterceptor(AuthInterceptor(activeTokenProvider, options.hosts))
            .addInterceptor(RedactingLogInterceptor(options.debugLogging, options.logSink))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // Redirects are followed by hand nowhere in the app; turning them off means a 3xx
            // from anywhere can never bounce a request (and its auth header) to another host.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    fun createRetrofitApi(client: OkHttpClient, baseUrl: String): CloudflareApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(createMoshi()))
        .build()
        .create(CloudflareApi::class.java)

    /** Main API instance: authenticates every request as whichever profile is active. */
    fun createApi(baseUrl: String = BASE_URL, maxRetries: Int = 2, activeTokenProvider: () -> String?): CloudflareApi {
        val client = createClient(Options(CloudflareHosts(baseUrl), maxRetries = maxRetries), activeTokenProvider)
        return createRetrofitApi(client, baseUrl)
    }

    /** No stored credential: only used to verify a brand-new token via an explicit header,
     *  before it is written to disk. */
    fun createVerifierApi(baseUrl: String = BASE_URL, maxRetries: Int = 2): CloudflareApi = createApi(baseUrl, maxRetries) { null }
}
