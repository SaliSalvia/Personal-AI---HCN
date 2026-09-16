package com.example.data.api.hcnsec

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * HCNSEC wire models (OpenAI-compatible JSON).
 *
 * These types are private to the HCNSEC adapter package: they must never be used by the
 * application layer. Everything outside this package exchanges provider-neutral types only.
 *
 * Relocated from `com.example.data.api.HcnsecApiModels` in Phase 4.2 so that the provider wire
 * format lives next to the only code that understands it.
 */

@JsonClass(generateAdapter = true)
data class HcnsecModelListResponse(
    @Json(name = "data") val data: List<HcnsecModelDto>?,
    @Json(name = "object") val obj: String? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecModelDto(
    @Json(name = "id") val id: String,
    @Json(name = "object") val obj: String? = "model",
    @Json(name = "created") val created: Long? = 0,
    @Json(name = "owned_by") val ownedBy: String? = "hcnsec",
)

@JsonClass(generateAdapter = true)
data class HcnsecChatCompletionRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<HcnsecChatMessage>,
    @Json(name = "stream") val stream: Boolean = false,
    @Json(name = "temperature") val temperature: Double? = null,
    @Json(name = "max_tokens") val maxTokens: Int? = null,
    @Json(name = "top_p") val topP: Double? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecChatMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String,
    @Json(name = "name") val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecChatCompletionResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "object") val obj: String? = null,
    @Json(name = "created") val created: Long? = null,
    @Json(name = "model") val model: String? = null,
    @Json(name = "choices") val choices: List<HcnsecChoiceDto>? = null,
    @Json(name = "usage") val usage: HcnsecUsageDto? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecChoiceDto(
    @Json(name = "index") val index: Int? = null,
    @Json(name = "message") val message: HcnsecResponseMessageDto? = null,
    @Json(name = "finish_reason") val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecResponseMessageDto(
    @Json(name = "role") val role: String? = null,
    @Json(name = "content") val content: String? = null,
    @Json(name = "reasoning_content") val reasoningContent: String? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecStreamChunkDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "object") val obj: String? = null,
    @Json(name = "created") val created: Long? = null,
    @Json(name = "model") val model: String? = null,
    @Json(name = "choices") val choices: List<HcnsecStreamChoiceDto>? = null,
    @Json(name = "usage") val usage: HcnsecUsageDto? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecStreamChoiceDto(
    @Json(name = "index") val index: Int? = null,
    @Json(name = "delta") val delta: HcnsecStreamDeltaDto? = null,
    @Json(name = "finish_reason") val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecStreamDeltaDto(
    @Json(name = "role") val role: String? = null,
    @Json(name = "content") val content: String? = null,
    @Json(name = "reasoning_content") val reasoningContent: String? = null,
)

/**
 * Token usage. All fields are nullable with null defaults so that a provider which omits usage
 * (or individual counters) never gets reported as an actual zero.
 */
@JsonClass(generateAdapter = true)
data class HcnsecUsageDto(
    @Json(name = "prompt_tokens") val promptTokens: Int? = null,
    @Json(name = "completion_tokens") val completionTokens: Int? = null,
    @Json(name = "total_tokens") val totalTokens: Int? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecApiErrorResponse(
    @Json(name = "error") val error: HcnsecApiErrorDetail? = null,
)

@JsonClass(generateAdapter = true)
data class HcnsecApiErrorDetail(
    @Json(name = "message") val message: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "code") val code: Any? = null,
)
