package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HcnsecModelListResponse(
    @Json(name = "data") val data: List<HcnsecModelDto>?,
    @Json(name = "object") val obj: String?
)

@JsonClass(generateAdapter = true)
data class HcnsecModelDto(
    @Json(name = "id") val id: String,
    @Json(name = "object") val obj: String? = "model",
    @Json(name = "created") val created: Long? = 0,
    @Json(name = "owned_by") val ownedBy: String? = "hcnsec"
)

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<ChatMessageDto>,
    @Json(name = "stream") val stream: Boolean = false,
    @Json(name = "temperature") val temperature: Double? = 0.7,
    @Json(name = "max_tokens") val maxTokens: Int? = null,
    @Json(name = "top_p") val topP: Double? = null
)

@JsonClass(generateAdapter = true)
data class ChatMessageDto(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    @Json(name = "id") val id: String?,
    @Json(name = "object") val obj: String?,
    @Json(name = "created") val created: Long?,
    @Json(name = "model") val model: String?,
    @Json(name = "choices") val choices: List<ChatChoiceDto>?,
    @Json(name = "usage") val usage: UsageDto?
)

@JsonClass(generateAdapter = true)
data class ChatChoiceDto(
    @Json(name = "index") val index: Int?,
    @Json(name = "message") val message: ChatResponseMessageDto?,
    @Json(name = "finish_reason") val finishReason: String?
)

@JsonClass(generateAdapter = true)
data class ChatResponseMessageDto(
    @Json(name = "role") val role: String?,
    @Json(name = "content") val content: String?,
    @Json(name = "reasoning_content") val reasoningContent: String? = null
)

@JsonClass(generateAdapter = true)
data class ChatStreamChunkDto(
    @Json(name = "id") val id: String?,
    @Json(name = "object") val obj: String?,
    @Json(name = "created") val created: Long?,
    @Json(name = "model") val model: String?,
    @Json(name = "choices") val choices: List<StreamChoiceDto>?,
    @Json(name = "usage") val usage: UsageDto? = null
)

@JsonClass(generateAdapter = true)
data class StreamChoiceDto(
    @Json(name = "index") val index: Int?,
    @Json(name = "delta") val delta: StreamDeltaDto?,
    @Json(name = "finish_reason") val finishReason: String?
)

@JsonClass(generateAdapter = true)
data class StreamDeltaDto(
    @Json(name = "role") val role: String? = null,
    @Json(name = "content") val content: String? = null,
    @Json(name = "reasoning_content") val reasoningContent: String? = null
)

@JsonClass(generateAdapter = true)
data class UsageDto(
    @Json(name = "prompt_tokens") val promptTokens: Int? = 0,
    @Json(name = "completion_tokens") val completionTokens: Int? = 0,
    @Json(name = "total_tokens") val totalTokens: Int? = 0
)

@JsonClass(generateAdapter = true)
data class ApiErrorResponse(
    @Json(name = "error") val error: ApiErrorDetail?
)

@JsonClass(generateAdapter = true)
data class ApiErrorDetail(
    @Json(name = "message") val message: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "code") val code: Any?
)

@JsonClass(generateAdapter = true)
data class UserBalanceDto(
    @Json(name = "total_available") val totalAvailable: Double? = null,
    @Json(name = "total_granted") val totalGranted: Double? = null,
    @Json(name = "total_used") val totalUsed: Double? = null,
    @Json(name = "currency") val currency: String? = "CNY"
)
