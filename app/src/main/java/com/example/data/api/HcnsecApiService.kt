package com.example.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * Retrofit interface for HCNSEC API endpoints.
 * Base URL: https://api.hcnsec.cn/v1/
 */
interface HcnsecApiService {

    /**
     * Lists all available AI models supported by HCNSEC.
     */
    @GET("models")
    suspend fun getModels(): Response<HcnsecModelListResponse>

    /**
     * Validates an API key directly against the /models endpoint.
     */
    @GET("models")
    suspend fun validateKey(
        @Header("Authorization") authHeader: String
    ): Response<HcnsecModelListResponse>

    /**
     * Standard non-streaming chat completion.
     */
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    /**
     * Server-Sent Events (SSE) streaming chat completion.
     */
    @Streaming
    @POST("chat/completions")
    suspend fun streamChatCompletion(
        @Body request: ChatCompletionRequest
    ): Response<ResponseBody>
}
