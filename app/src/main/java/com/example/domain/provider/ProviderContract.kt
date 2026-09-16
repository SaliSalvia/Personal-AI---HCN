package com.example.domain.provider

import com.example.domain.model.ModelCapability

/**
 * Provider-neutral contract for AI provider integrations.
 *
 * This is the equivalent of the `src/services/providers/` contract layer referenced by the
 * Phase 4.2 brief, expressed in this repository's Kotlin layer instead of TypeScript.
 * See AI_CONTEXT.md for the full path mapping.
 *
 * Nothing in this file may reference a concrete provider (no HCNSEC, no wire-format types,
 * no HTTP client). Concrete providers live in `com.example.data.api.<provider>` and implement
 * [ProviderAdapter].
 */

/**
 * Stable provider identifiers.
 *
 * Only [HCNSEC] has a real adapter in Phase 4.2. The remaining entries are reserved
 * foundation-only slots: they exist so the registry can keep their identity, priority and
 * enabled state, but they intentionally have no adapter and are permanently disabled.
 *
 * Note: the Phase 4.2 brief mentions "six other provider slots" but the repository contains no
 * record of provider identities beyond the four later-phase providers named in the brief's phase
 * boundary. Those four are listed here and the remaining two slots are deliberately *not*
 * invented. See AI_CONTEXT.md (Limitations).
 */
enum class ProviderId(val displayName: String) {
    HCNSEC("HCNSEC"),
    GEMINI("Google Gemini"),
    GROQ("Groq"),
    MISTRAL("Mistral AI"),
    YOU_COM("You.com"),
}

/** Capability metadata a provider can advertise. Used by the registry for routing metadata. */
enum class ProviderCapability {
    CHAT_COMPLETION,
    STREAMING,
    SYSTEM_MESSAGES,
    TEMPERATURE,
    MAX_OUTPUT_TOKENS,
    MODEL_LISTING,
    REASONING_CONTENT,
    CONNECTION_TEST,
}

/** Conversation role in provider-neutral form. */
enum class ProviderRole(val wireName: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    ;

    companion object {
        /** Maps a stored/application role string onto a provider-neutral role. */
        fun fromWireName(value: String?): ProviderRole =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) } ?: USER
    }
}

/**
 * A single provider-neutral chat message.
 *
 * The application layer builds these; the adapter is responsible for mapping them to whatever
 * wire shape the provider expects.
 */
data class ProviderMessage(
    val role: ProviderRole,
    val content: String,
)

/**
 * Provider-neutral chat request.
 *
 * [model] is a provider model identifier resolved from configured model descriptors; a null value
 * means "use the provider's configured default" and adapters must fail with
 * [ProviderErrorKind.NOT_CONFIGURED] if no default exists (never silently invent a model).
 */
data class ProviderChatRequest(
    val model: String?,
    val messages: List<ProviderMessage>,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val stream: Boolean = false,
    /**
     * Optional whole-call deadline. Enforced at the transport/socket level so that a strict
     * timeout is possible even while a blocking read is in progress (used by "Test Connection").
     */
    val timeoutMillis: Long? = null,
)

/**
 * Provider-neutral token usage.
 *
 * Every field is optional: providers that do not report usage leave them null. Implementations
 * must never guess or synthesise token counts.
 */
data class ProviderUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)

/** Provider-neutral description of a selectable model. */
data class ProviderModelDescriptor(
    val id: String,
    val displayName: String = id,
    val description: String = "",
    val capabilities: Set<ModelCapability> = emptySet(),
)

/** Provider-neutral non-streaming chat response. */
data class ProviderChatResponse(
    val text: String,
    val model: String,
    val reasoningText: String? = null,
    val finishReason: String? = null,
    val usage: ProviderUsage? = null,
    val latencyMillis: Long = 0L,
)

/** Provider-neutral streaming events. */
sealed interface ProviderStreamEvent {
    /** Incremental answer text. Adapters must emit only new text, never a re-emitted aggregate. */
    data class Content(val text: String) : ProviderStreamEvent

    /** Incremental reasoning/thinking text, when the provider exposes it. */
    data class Reasoning(val text: String) : ProviderStreamEvent

    /**
     * Emitted at most once, and only after the stream has terminated normally.
     * Carries no text so that a final aggregate can never be double-counted by the UI.
     */
    data class Completed(
        val finishReason: String?,
        val usage: ProviderUsage? = null,
    ) : ProviderStreamEvent

    /** Terminal normalized failure. Cancellation is NOT reported here (see below). */
    data class Failed(val error: ProviderError) : ProviderStreamEvent
}

/**
 * A provider adapter is the only object allowed to know a provider's wire format.
 *
 * Contract rules enforced by tests:
 * - implementers must never expose provider JSON/HTTP types outside their own package;
 * - implementers must obtain credentials only through a credential abstraction, never by
 *   reading secure storage directly;
 * - implementers must not retry authentication, quota or invalid-request failures.
 */
interface ProviderAdapter {
    val id: ProviderId

    /** Capabilities this adapter actually implements. */
    val capabilities: Set<ProviderCapability>

    /** Lists models selectable for this provider. May fail with a normalized error. */
    suspend fun listModels(): List<ProviderModelDescriptor>

    /** Executes a non-streaming chat completion. Throws [ProviderException] on failure. */
    suspend fun chat(request: ProviderChatRequest): ProviderChatResponse

    /**
     * Executes a streaming chat completion.
     *
     * Terminal failures are emitted as [ProviderStreamEvent.Failed]; cancellation propagates as
     * [kotlinx.coroutines.CancellationException] and must never be emitted as a failure event.
     */
    fun stream(request: ProviderChatRequest): kotlinx.coroutines.flow.Flow<ProviderStreamEvent>
}
