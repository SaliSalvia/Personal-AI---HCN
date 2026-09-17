package com.example.data.api

import com.example.data.security.ApiKeyRepository
import com.squareup.moshi.Moshi
import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Factory and builder for creating configured Retrofit HCNSEC API service instances.
 */
object HcnsecApiFactory {

    const val BASE_URL = "https://api.hcnsec.cn/v1/"

    /**
     * Interceptor that attaches the secure Authorization Bearer token from ApiKeyRepository.
     */
    fun createAuthInterceptor(apiKeyRepository: ApiKeyRepository): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val key = apiKeyRepository.getApiKey().orEmpty().trim()

            val requestBuilder = original.newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "Sali-HCNSEC-Android/1.0")

            // Only attach Authorization header if not explicitly overridden by request
            if (original.header("Authorization") == null && key.isNotEmpty()) {
                requestBuilder.header("Authorization", "Bearer $key")
            }

            chain.proceed(requestBuilder.build())
        }
    }

    /**
     * Interceptor that handles transient request timeouts, standard HTTP status codes, and network retries.
     */
    fun createRetryAndHttpStatusInterceptor(maxRetries: Int = 3, initialBackoffMs: Long = 1000L): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            var attempt = 0
            var response: okhttp3.Response? = null
            var lastException: IOException? = null

            while (attempt < maxRetries) {
                try {
                    response = chain.proceed(request)
                    
                    // Handle standard HTTP status codes
                    when (response.code) {
                        in 200..299 -> return@Interceptor response
                        401 -> {
                            // Unauthorized: Invalid or expired API Key
                            return@Interceptor response
                        }
                        403 -> {
                            // Forbidden: Model not supported by tier or permission denied
                            return@Interceptor response
                        }
                        404 -> {
                            // Endpoint or model not found
                            return@Interceptor response
                        }
                        400, 422 -> {
                            // Bad Request / Unprocessable Entity: Invalid payload structure
                            return@Interceptor response
                        }
                        408 -> {
                            // Request Timeout -> Eligible for retry
                            response.close()
                        }
                        429 -> {
                            // Rate limit / Quota exceeded -> Retry with exponential backoff
                            response.close()
                        }
                        in 500..599 -> {
                            // Upstream Server Error -> Retry
                            response.close()
                        }
                        else -> return@Interceptor response
                    }
                } catch (e: IOException) {
                    lastException = e
                }

                attempt++
                if (attempt < maxRetries) {
                    val backoff = initialBackoffMs * (1L shl (attempt - 1))
                    try {
                        Thread.sleep(backoff)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }

            if (response != null) {
                response
            } else {
                throw lastException ?: IOException("HCNSEC request failed after $maxRetries retry attempts.")
            }
        }
    }

    /**
     * Builds configured OkHttpClient with timeouts, security interceptor, and retry mechanisms.
     */
    fun createOkHttpClient(apiKeyRepository: ApiKeyRepository): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(createAuthInterceptor(apiKeyRepository))
            .addInterceptor(createRetryAndHttpStatusInterceptor(maxRetries = 3))
            .apply {
                // Logging is debug only: writing every request to logcat on a release
                // build costs frames and would expose request metadata.
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                        }
                    )
                }
            }
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Creates and initializes the Retrofit HCNSEC service instance.
     */
    fun createService(apiKeyRepository: ApiKeyRepository): HcnsecApiService {
        val moshi = Moshi.Builder().build()

        val client = createOkHttpClient(apiKeyRepository)

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(HcnsecApiService::class.java)
    }
}
