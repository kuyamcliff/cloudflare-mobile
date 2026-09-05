package dev.cfmobile.app.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    const val BASE_URL = "https://api.cloudflare.com/client/v4/"

    fun createMoshi(): Moshi = Moshi.Builder()
        .add(Any::class.java, AnyJsonAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        // Headers only: never log request/response bodies, which for this app means
        // never logging the API token or account data to logcat.
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private fun baseClientBuilder() = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)

    private fun buildRetrofit(client: OkHttpClient, baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(createMoshi()))
        .build()

    /** Main API instance: authenticates every request as whichever account is active. */
    fun createApi(baseUrl: String = BASE_URL, activeTokenProvider: () -> String?): CloudflareApi {
        val client = baseClientBuilder()
            .addInterceptor(AuthInterceptor(activeTokenProvider))
            .build()
        return buildRetrofit(client, baseUrl).create(CloudflareApi::class.java)
    }

    /** No auth interceptor - only used to verify a brand-new token via an explicit header,
     *  before it's ever written to disk. */
    fun createVerifierApi(baseUrl: String = BASE_URL): CloudflareApi {
        val client = baseClientBuilder().build()
        return buildRetrofit(client, baseUrl).create(CloudflareApi::class.java)
    }
}
