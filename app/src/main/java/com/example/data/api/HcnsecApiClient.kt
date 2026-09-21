package com.example.data.api

import com.example.data.security.ApiKeyRepository
import com.squareup.moshi.Moshi
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
        private const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
        private const val OPEN_ROUTER_BASE_URL = "https://openrouter.ai/api/v1"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun activeProvider(): AiProvider = apiKeyRepository.getActiveProvider()

    private fun baseUrl(provider: AiProvider): String = when (provider) {
        AiProvider.GROQ -> GROQ_BASE_URL
        AiProvider.OPEN_ROUTER -> OPEN_ROUTER_BASE_URL
        AiProvider.GOOGLE_AI_STUDIO -> GEMINI_BASE_URL
        AiProvider.HCNSEC -> BASE_URL
    }

    // Every DTO is annotated with @JsonClass(generateAdapter = true), so the KSP
    // generated adapters are used. The reflection based factory is not needed and
    // would drag kotlin-reflect (plus its startup cost) into the APK.
    private val moshi: Moshi = Moshi.Builder().build()

    private val authInterceptor = Interceptor { chain ->
        val provider = activeProvider()
        val apiKey = apiKeyRepository.getProviderKey(provider) ?: ""
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
    private val streamChunkAdapter = moshi.adapter(ChatStreamChunkDto::class.java)
    private val errorAdapter = moshi.adapter(ApiErrorResponse::class.java)

    /**
     * Validates connection to HCNSEC API by testing with the given key.
     */
    suspend fun validateApiKey(keyToTest: String): Result<List<HcnsecModelDto>> = withContext(Dispatchers.IO) {
        try {
            val provider = activeProvider()
            if (provider == AiProvider.GOOGLE_AI_STUDIO) {
                // The key travels in a header rather than a query string so it never
                // ends up in proxy logs, crash reports or URL history.
                val request = Request.Builder()
                    .url("$GEMINI_BASE_URL/models")
                    .header("x-goog-api-key", keyToTest.trim())
                    .get()
                    .build()
                OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext Result.failure(Exception("Google AI Studio key was rejected (HTTP ${response.code})."))
                    return@withContext Result.success(listOf("gemini-2.5-flash", "gemini-2.5-pro").map { HcnsecModelDto(it, ownedBy = "Google") })
                }
            }
            val request = Request.Builder()
                .url("${baseUrl(activeProvider())}/models")
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
            val provider = activeProvider()
            if (provider == AiProvider.GOOGLE_AI_STUDIO) {
                return@withContext Result.success(listOf(
                    HcnsecModelDto("gemini-2.5-flash", ownedBy = "Google"),
                    HcnsecModelDto("gemini-2.5-pro", ownedBy = "Google")
                ))
            }
            val request = Request.Builder()
                .url("${baseUrl(activeProvider())}/models")
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
        if (activeProvider() == AiProvider.GOOGLE_AI_STUDIO) {
            streamGemini(model, messages, temperature).collect { emit(it) }
            return@flow
        }
        val requestBodyJson = chatRequestAdapter.toJson(
            ChatCompletionRequest(
                model = model,
                messages = messages,
                stream = true,
                temperature = temperature
            )
        )

        val request = Request.Builder()
            .url("${baseUrl(activeProvider())}/chat/completions")
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

    private fun streamGemini(
        model: String,
        messages: List<ChatMessageDto>,
        temperature: Double
    ): Flow<StreamEvent> = flow {
        val key = apiKeyRepository.getProviderKey(AiProvider.GOOGLE_AI_STUDIO)
            ?: run { emit(StreamEvent.Error("Google AI Studio API key is not configured.")); return@flow }
        val contents = org.json.JSONArray()
        messages.filter { it.role != "system" }.forEach { message ->
            contents.put(org.json.JSONObject().apply {
                put("role", if (message.role == "assistant") "model" else "user")
                put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", message.content)))
            })
        }
        val body = org.json.JSONObject().apply {
            put("contents", contents)
            put("generationConfig", org.json.JSONObject().put("temperature", temperature))
        }.toString()
        val request = Request.Builder()
            .url("$GEMINI_BASE_URL/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", key)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(StreamEvent.Error("Google AI Studio request failed (HTTP ${response.code}).", response.code))
                    return@flow
                }
                response.body?.byteStream()?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val current = line.orEmpty()
                        if (!current.startsWith("data:")) continue
                        try {
                            val json = org.json.JSONObject(current.removePrefix("data:").trim())
                            val text = json.optJSONArray("candidates")?.optJSONObject(0)
                                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                                ?.optString("text").orEmpty()
                            if (text.isNotEmpty()) emit(StreamEvent.Content(text))
                        } catch (_: Exception) { }
                    }
                }
                emit(StreamEvent.Completed("stop", null))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(StreamEvent.Error(formatNetworkError(e)))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseErrorMessage(code: Int, bodyString: String): String {
        // The active provider can be Groq/OpenRouter/Gemini, so blaming HCNSEC for
        // every failure sent users to the wrong settings screen.
        val provider = activeProvider().displayName
        return try {
            val errorObj = errorAdapter.fromJson(bodyString)
            val msg = errorObj?.error?.message
            when (code) {
                401 -> "Invalid $provider API key. Please verify your credentials in Settings."
                403 -> "Access forbidden for this $provider model or resource."
                429 -> "Rate limit reached on the $provider API. Please wait a moment before sending more messages."
                500, 502, 503 -> "$provider server error ($code). The service is currently experiencing high load."
                else -> msg ?: "HTTP $code: Request failed."
            }
        } catch (_: Exception) {
            when (code) {
                401 -> "Invalid $provider API key."
                429 -> "$provider rate limit exceeded."
                else -> "Server returned error code $code"
            }
        }
    }

    private fun formatNetworkError(e: Throwable): String {
        val provider = activeProvider().displayName
        return when (e) {
            is java.net.UnknownHostException -> "Network unavailable. Please check your internet connection."
            is java.net.SocketTimeoutException -> "Request timed out while connecting to $provider."
            is IOException -> "Connection interrupted: ${e.localizedMessage ?: "I/O error"}"
            else -> e.localizedMessage ?: "An unexpected error occurred."
        }
    }
}
