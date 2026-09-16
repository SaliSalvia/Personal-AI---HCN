package com.example.data.api

import com.example.data.security.ApiKeyRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class StreamEvent {
    data class Content(val text: String) : StreamEvent()
    data class Reasoning(val reasoningText: String) : StreamEvent()
    data class Completed(val finishReason: String?, val totalTokens: Int?) : StreamEvent()
    data class Error(val message: String, val code: Int? = null) : StreamEvent()
}

class HcnsecApiClient(
    private val apiKeyRepository: ApiKeyRepository
) {
    companion object {
        const val BASE_URL = "https://api.hcnsec.cn/v1"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val apiKey = apiKeyRepository.getApiKey() ?: ""
        val originalRequest = chain.request()
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .header("User-Agent", "Sali-HCNSEC-Android/1.0")
            .build()
        chain.proceed(authenticatedRequest)
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .retryOnConnectionFailure(true)
        .build()

    private val modelListAdapter = moshi.adapter(HcnsecModelListResponse::class.java)
    private val chatRequestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
    private val chatResponseAdapter = moshi.adapter(ChatCompletionResponse::class.java)
    private val streamChunkAdapter = moshi.adapter(ChatStreamChunkDto::class.java)
    private val errorAdapter = moshi.adapter(ApiErrorResponse::class.java)

    /**
     * Validates connection to HCNSEC API by testing with the given key.
     */
    suspend fun validateApiKey(keyToTest: String): Result<List<HcnsecModelDto>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .header("Authorization", "Bearer ${keyToTest.trim()}")
                .get()
                .build()

            val tempClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            tempClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = modelListAdapter.fromJson(bodyString)
                    val models = parsed?.data.orEmpty()
                    Result.success(models)
                } else {
                    val errorMsg = parseErrorMessage(response.code, bodyString)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(formatNetworkError(e)))
        }
    }

    /**
     * Retrieves the available models from HCNSEC API.
     */
    suspend fun getModels(): Result<List<HcnsecModelDto>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = modelListAdapter.fromJson(bodyString)
                    Result.success(parsed?.data.orEmpty())
                } else {
                    val errorMsg = parseErrorMessage(response.code, bodyString)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(formatNetworkError(e)))
        }
    }

    /**
     * Streams chat completion chunks via Server-Sent Events (SSE).
     */
    fun streamChatCompletion(
        model: String,
        messages: List<ChatMessageDto>,
        temperature: Double = 0.7
    ): Flow<StreamEvent> = flow {
        val requestBodyJson = chatRequestAdapter.toJson(
            ChatCompletionRequest(
                model = model,
                messages = messages,
                stream = true,
                temperature = temperature
            )
        )

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        var call: okhttp3.Call? = null
        var response: Response? = null
        var reader: BufferedReader? = null

        try {
            call = okHttpClient.newCall(request)
            response = call.execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                val message = parseErrorMessage(response.code, errorBody)
                emit(StreamEvent.Error(message, response.code))
                return@flow
            }

            val body = response.body ?: run {
                emit(StreamEvent.Error("Empty response body from HCNSEC API"))
                return@flow
            }

            reader = body.byteStream().bufferedReader(Charsets.UTF_8)
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim().orEmpty()
                if (currentLine.isEmpty() || currentLine.startsWith(":")) {
                    continue // SSE comment or keepalive ping
                }

                if (currentLine.startsWith("data:")) {
                    val data = currentLine.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        emit(StreamEvent.Completed(finishReason = "stop", totalTokens = null))
                        break
                    }

                    try {
                        val chunk = streamChunkAdapter.fromJson(data)
                        val choice = chunk?.choices?.firstOrNull()
                        val delta = choice?.delta

                        // Official reasoning parameter check (DeepSeek-R1 / HCNSEC reasoner)
                        val reasoning = delta?.reasoningContent
                        if (!reasoning.isNullOrEmpty()) {
                            emit(StreamEvent.Reasoning(reasoning))
                        }

                        // Standard text delta
                        val content = delta?.content
                        if (!content.isNullOrEmpty()) {
                            emit(StreamEvent.Content(content))
                        }

                        if (choice?.finishReason != null) {
                            emit(
                                StreamEvent.Completed(
                                    finishReason = choice.finishReason,
                                    totalTokens = chunk.usage?.totalTokens
                                )
                            )
                        }
                    } catch (_: Exception) {
                        // Skip malformed individual chunk
                    }
                }
            }
        } catch (e: CancellationException) {
            call?.cancel()
            throw e
        } catch (e: Exception) {
            emit(StreamEvent.Error(formatNetworkError(e)))
        } finally {
            try {
                reader?.close()
                response?.close()
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Non-streaming completion call for quick extraction or summary tasks.
     */
    suspend fun chatCompletion(
        model: String,
        messages: List<ChatMessageDto>,
        temperature: Double = 0.5
    ): Result<ChatResponseMessageDto> = withContext(Dispatchers.IO) {
        try {
            val requestBodyJson = chatRequestAdapter.toJson(
                ChatCompletionRequest(
                    model = model,
                    messages = messages,
                    stream = false,
                    temperature = temperature
                )
            )

            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = chatResponseAdapter.fromJson(bodyString)
                    val choice = parsed?.choices?.firstOrNull()?.message
                    if (choice != null) {
                        Result.success(choice)
                    } else {
                        Result.failure(Exception("No completion choices returned by model"))
                    }
                } else {
                    val errorMsg = parseErrorMessage(response.code, bodyString)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(formatNetworkError(e)))
        }
    }

    /**
     * Attempts to retrieve account balance or usage from official HCNSEC endpoints if supported.
     * Per rule: "Do not scrape or reverse-engineer undocumented private endpoints.
     * If balance cannot be retrieved through an official API endpoint, clearly communicate that limitation."
     */
    suspend fun getAccountUsage(): Result<UserBalanceDto?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/dashboard/billing/usage")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val adapter = moshi.adapter(UserBalanceDto::class.java)
                    Result.success(adapter.fromJson(body))
                } else {
                    // Endpoint is not officially published or supported by this server instance
                    Result.success(null)
                }
            }
        } catch (_: Exception) {
            Result.success(null)
        }
    }

    private fun parseErrorMessage(code: Int, bodyString: String): String {
        return try {
            val errorObj = errorAdapter.fromJson(bodyString)
            val msg = errorObj?.error?.message
            when (code) {
                401 -> "Invalid HCNSEC API Key. Please verify your credentials in Settings."
                403 -> "Access forbidden for this HCNSEC model or resource."
                429 -> "Rate limit reached on HCNSEC API. Please wait a moment before sending more messages."
                500, 502, 503 -> "HCNSEC server error ($code). The service is currently experiencing high load."
                else -> msg ?: "HTTP $code: Request failed."
            }
        } catch (_: Exception) {
            when (code) {
                401 -> "Invalid HCNSEC API Key."
                429 -> "HCNSEC Rate limit exceeded."
                else -> "Server returned error code $code"
            }
        }
    }

    private fun formatNetworkError(e: Throwable): String {
        return when (e) {
            is java.net.UnknownHostException -> "Network unavailable. Please check your internet connection."
            is java.net.SocketTimeoutException -> "Request timed out while connecting to HCNSEC."
            is IOException -> "Connection interrupted: ${e.localizedMessage ?: "I/O error"}"
            else -> e.localizedMessage ?: "An unexpected error occurred."
        }
    }
}
