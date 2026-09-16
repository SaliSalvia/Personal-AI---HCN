package com.example.data.api.hcnsec

import com.example.data.network.ProviderHttpRequest
import com.example.data.network.ProviderHttpTransport
import com.example.data.network.ProviderStreamHandle
import com.example.data.security.ProviderCredential
import com.example.data.security.ProviderCredentialSource
import com.example.domain.provider.ProviderAdapter
import com.example.domain.provider.ProviderCapability
import com.example.domain.provider.ProviderChatRequest
import com.example.domain.provider.ProviderChatResponse
import com.example.domain.provider.ProviderCircuitBreaker
import com.example.domain.provider.ProviderError
import com.example.domain.provider.ProviderErrorKind
import com.example.domain.provider.ProviderErrors
import com.example.domain.provider.ProviderException
import com.example.domain.provider.ProviderHealthTracker
import com.example.domain.provider.ProviderId
import com.example.domain.provider.ProviderModelDescriptor
import com.example.domain.provider.ProviderRateLimitParser
import com.example.domain.provider.ProviderRateLimitSnapshot
import com.example.domain.provider.ProviderStreamEvent
import com.example.domain.provider.ProviderUsage
import com.example.domain.router.CapabilityRegistry
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The real HCNSEC provider adapter: the first production provider integration of the app.
 *
 * Responsibilities:
 * - serialize provider-neutral requests into the HCNSEC OpenAI-compatible wire format;
 * - build the `Authorization: Bearer <credential>` header — the credential is obtained from the
 *   [ProviderCredentialSource] abstraction and never logged, stored or returned anywhere;
 * - normalize every response, stream event and failure into the provider-neutral model;
 * - report outcomes to the health tracker and the circuit breaker.
 *
 * It performs **no retries**: retry/failover eligibility is expressed through
 * [com.example.domain.provider.ProviderErrorPolicy] and left to the caller. Authentication,
 * quota, invalid-request and cancellation failures are never retried.
 *
 * Threading: blocking IO happens on [Dispatchers.IO]; the adapter never blocks the caller thread.
 */
class HcnsecProviderAdapter(
    private val credentials: ProviderCredentialSource,
    private val transport: ProviderHttpTransport,
    private val health: ProviderHealthTracker,
    private val circuitBreaker: ProviderCircuitBreaker = ProviderCircuitBreaker(),
    private val config: HcnsecProviderConfig = HcnsecProviderConfig(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProviderAdapter {

    companion object {
        const val USER_AGENT: String = "Sali-HCNSEC-Android/1.0"

        private const val SSE_DATA_PREFIX = "data:"
        private const val SSE_DONE = "[DONE]"

        /** Strings that mean "the account has no quota", as opposed to "slow down". */
        private val QUOTA_MARKERS = listOf(
            "insufficient_quota",
            "insufficient quota",
            "quota_exceeded",
            "quota exceeded",
            "exceeded your current quota",
            "insufficient_user_quota",
            "insufficient balance",
            "insufficient_balance",
            "billing_hard_limit",
            "out of credits",
            "no credits",
            "payment required",
        )
    }

    override val id: ProviderId = ProviderId.HCNSEC

    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.CHAT_COMPLETION,
        ProviderCapability.STREAMING,
        ProviderCapability.SYSTEM_MESSAGES,
        ProviderCapability.TEMPERATURE,
        ProviderCapability.MAX_OUTPUT_TOKENS,
        ProviderCapability.MODEL_LISTING,
        ProviderCapability.REASONING_CONTENT,
        ProviderCapability.CONNECTION_TEST,
    )

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter: JsonAdapter<HcnsecChatCompletionRequest> =
        moshi.adapter(HcnsecChatCompletionRequest::class.java)
    private val responseAdapter: JsonAdapter<HcnsecChatCompletionResponse> =
        moshi.adapter(HcnsecChatCompletionResponse::class.java)
    private val streamChunkAdapter: JsonAdapter<HcnsecStreamChunkDto> =
        moshi.adapter(HcnsecStreamChunkDto::class.java)
    private val errorAdapter: JsonAdapter<HcnsecApiErrorResponse> =
        moshi.adapter(HcnsecApiErrorResponse::class.java)
    private val modelListAdapter: JsonAdapter<HcnsecModelListResponse> =
        moshi.adapter(HcnsecModelListResponse::class.java)

    // ---------------------------------------------------------------------------------------
    // Chat (non-streaming)
    // ---------------------------------------------------------------------------------------

    override suspend fun chat(request: ProviderChatRequest): ProviderChatResponse =
        withContext(Dispatchers.IO) {
            val credential = resolveCredential()
                ?: throw fail(ProviderErrors.notConfigured(id, "no credential is configured."))

            if (!circuitBreaker.allowRequest()) {
                throw ProviderException(ProviderErrors.circuitOpen(id))
            }

            val httpRequest = buildChatHttpRequest(
                request = request,
                credential = credential,
                streaming = false,
                timeoutMillis = request.timeoutMillis ?: config.requestTimeoutMillis,
            )

            val response = try {
                transport.execute(httpRequest)
            } catch (e: CancellationException) {
                circuitBreaker.abandonProbe()
                throw e
            } catch (e: IOException) {
                throw fail(mapTransportError(e))
            }

            val rateLimit = ProviderRateLimitParser.fromHeaders(response.headers, clock())

            if (response.statusCode !in 200..299) {
                throw fail(mapErrorResponse(response.statusCode, response.body), rateLimit)
            }

            val parsed = parseChatResponse(response.body, response.latencyMillis, rateLimit)

            health.recordSuccess(response.latencyMillis, rateLimit)
            circuitBreaker.recordSuccess()
            parsed
        }

    private fun parseChatResponse(
        body: String,
        latencyMillis: Long,
        rateLimit: ProviderRateLimitSnapshot,
    ): ProviderChatResponse {
        val envelope = runCatching { responseAdapter.fromJson(body) }.getOrNull()
            ?: throw fail(
                ProviderErrors.malformedResponse("HCNSEC returned a response that could not be parsed."),
                rateLimit,
            )

        // Some gateways answer HTTP 200 with an error envelope; treat that as a real failure
        // instead of reporting an empty completion.
        val errorDetail = runCatching { errorAdapter.fromJson(body) }.getOrNull()?.error
        if (errorDetail != null && envelope.choices.isNullOrEmpty()) {
            throw fail(mapErrorPayload(errorDetail.message, errorDetail.type, errorDetail.code?.toString()), rateLimit)
        }

        val choice = envelope.choices?.firstOrNull()
            ?: throw fail(
                ProviderErrors.malformedResponse("HCNSEC returned no completion choices."),
                rateLimit,
            )

        val message = choice.message
            ?: throw fail(
                ProviderErrors.malformedResponse("HCNSEC returned a choice without a message."),
                rateLimit,
            )

        return ProviderChatResponse(
            text = message.content.orEmpty(),
            model = envelope.model.orEmpty(),
            reasoningText = message.reasoningContent,
            finishReason = choice.finishReason,
            usage = envelope.usage?.toProviderUsage(),
            latencyMillis = latencyMillis,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Chat (streaming)
    // ---------------------------------------------------------------------------------------

    override fun stream(request: ProviderChatRequest): Flow<ProviderStreamEvent> = flow {
        val credential = resolveCredential()
        if (credential == null) {
            emit(ProviderStreamEvent.Failed(recordFailure(ProviderErrors.notConfigured(id, "no credential is configured."))))
            return@flow
        }

        if (!circuitBreaker.allowRequest()) {
            emit(ProviderStreamEvent.Failed(ProviderErrors.circuitOpen(id)))
            return@flow
        }

        val httpRequest = try {
            buildChatHttpRequest(
                request = request,
                credential = credential,
                streaming = true,
                // A stream should not be cut off by a whole-call deadline by default; the
                // transport read timeout bounds an idle connection instead.
                timeoutMillis = request.timeoutMillis,
            )
        } catch (e: ProviderException) {
            emit(ProviderStreamEvent.Failed(e.error))
            return@flow
        }

        var handle: ProviderStreamHandle? = null

        // Cancelling the collector must abort the in-flight socket read immediately, so the call
        // is cancelled from the cancelling thread instead of waiting for the read to return.
        val cancellationWatch = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) handle?.cancel()
        }

        val startedAt = clock()

        try {
            val opened = transport.openStream(httpRequest)
            handle = opened

            val rateLimit = ProviderRateLimitParser.fromHeaders(opened.headers, clock())

            if (opened.statusCode !in 200..299) {
                emit(
                    ProviderStreamEvent.Failed(
                        recordFailure(mapErrorResponse(opened.statusCode, opened.readErrorBody()), rateLimit),
                    ),
                )
                return@flow
            }

            var finishReason: String? = null
            var usage: ProviderUsage? = null
            var completionEmitted = false
            var parsedChunks = 0
            var malformedChunks = 0

            while (true) {
                currentCoroutineContext().ensureActive()

                val line = opened.readLine() ?: break
                val payload = ssePayload(line) ?: continue

                if (payload == SSE_DONE) {
                    emit(ProviderStreamEvent.Completed(finishReason, usage))
                    completionEmitted = true
                    break
                }

                when (val outcome = parseStreamPayload(payload)) {
                    is StreamPayload.Malformed -> malformedChunks += 1

                    is StreamPayload.Failure -> {
                        emit(ProviderStreamEvent.Failed(recordFailure(outcome.error, rateLimit)))
                        return@flow
                    }

                    is StreamPayload.Chunk -> {
                        parsedChunks += 1
                        outcome.reasoning?.takeIf { it.isNotEmpty() }?.let {
                            emit(ProviderStreamEvent.Reasoning(it))
                        }
                        outcome.content?.takeIf { it.isNotEmpty() }?.let {
                            emit(ProviderStreamEvent.Content(it))
                        }
                        outcome.finishReason?.let { finishReason = it }
                        outcome.usage?.let { usage = it }
                    }
                }
            }

            if (!completionEmitted) {
                if (parsedChunks == 0 && malformedChunks > 0) {
                    emit(
                        ProviderStreamEvent.Failed(
                            recordFailure(
                                ProviderErrors.malformedResponse("HCNSEC returned an unreadable stream."),
                                rateLimit,
                            ),
                        ),
                    )
                    return@flow
                }
                emit(ProviderStreamEvent.Completed(finishReason, usage))
            }

            health.recordSuccess(clock() - startedAt, rateLimit)
            circuitBreaker.recordSuccess()
        } catch (e: CancellationException) {
            // Cancellation is never reported as a provider failure and never recorded against
            // health, quota or the circuit breaker.
            circuitBreaker.abandonProbe()
            handle?.cancel()
            throw e
        } catch (e: IOException) {
            if (!currentCoroutineContext().isActive) {
                // OkHttp surfaces a cancelled call as an IOException; the coroutine was cancelled,
                // so this must remain a cancellation rather than a network failure.
                circuitBreaker.abandonProbe()
                throw CancellationException("HCNSEC stream cancelled.")
            }
            emit(ProviderStreamEvent.Failed(recordFailure(mapTransportError(e))))
        } finally {
            cancellationWatch?.dispose()
            runCatching { handle?.close() }
        }
    }.flowOn(Dispatchers.IO)

    private fun ssePayload(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return null
        if (!trimmed.startsWith(SSE_DATA_PREFIX)) return null
        return trimmed.removePrefix(SSE_DATA_PREFIX).trim()
    }

    private fun parseStreamPayload(payload: String): StreamPayload {
        val chunk = runCatching { streamChunkAdapter.fromJson(payload) }.getOrNull()

        if (chunk != null && !chunk.choices.isNullOrEmpty()) {
            val choice = chunk.choices.first()
            return StreamPayload.Chunk(
                content = choice.delta?.content,
                reasoning = choice.delta?.reasoningContent,
                finishReason = choice.finishReason,
                usage = chunk.usage?.toProviderUsage(),
            )
        }

        val errorDetail = runCatching { errorAdapter.fromJson(payload) }.getOrNull()?.error
        if (errorDetail != null) {
            return StreamPayload.Failure(
                mapErrorPayload(errorDetail.message, errorDetail.type, errorDetail.code?.toString()),
            )
        }

        if (chunk != null) {
            // A valid chunk that carries no choices (for example a usage-only frame).
            return StreamPayload.Chunk(
                content = null,
                reasoning = null,
                finishReason = null,
                usage = chunk.usage?.toProviderUsage(),
            )
        }

        return StreamPayload.Malformed
    }

    // ---------------------------------------------------------------------------------------
    // Models
    // ---------------------------------------------------------------------------------------

    override suspend fun listModels(): List<ProviderModelDescriptor> {
        val credential = resolveCredential()
            ?: throw ProviderException(ProviderErrors.notConfigured(id, "no credential is configured."))
        return fetchModels(credential)
    }

    /**
     * Validates an *unsaved* credential supplied by first-run onboarding.
     *
     * The credential lives only in memory for the duration of this call and is never persisted by
     * the provider layer. Use [listModels] for the stored credential.
     */
    suspend fun validateEphemeralCredential(ephemeral: ProviderCredential): List<ProviderModelDescriptor> {
        if (ephemeral.isBlank) {
            throw ProviderException(ProviderErrors.notConfigured(id, "the supplied credential is empty."))
        }
        return fetchModels(ephemeral)
    }

    private suspend fun fetchModels(credential: ProviderCredential): List<ProviderModelDescriptor> =
        withContext(Dispatchers.IO) {
            val httpRequest = ProviderHttpRequest(
                url = config.modelsUrl,
                method = "GET",
                headers = buildHeaders(credential, streaming = false),
                body = null,
                timeoutMillis = config.requestTimeoutMillis,
            )

            val response = try {
                transport.execute(httpRequest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw fail(mapTransportError(e))
            }

            val rateLimit = ProviderRateLimitParser.fromHeaders(response.headers, clock())

            if (response.statusCode !in 200..299) {
                throw fail(mapErrorResponse(response.statusCode, response.body), rateLimit)
            }

            val parsed = runCatching { modelListAdapter.fromJson(response.body) }.getOrNull()
                ?: throw fail(
                    ProviderErrors.malformedResponse("HCNSEC returned an unreadable model list."),
                    rateLimit,
                )

            health.recordSuccess(response.latencyMillis, rateLimit)
            circuitBreaker.recordSuccess()

            parsed.data.orEmpty().map { dto ->
                ProviderModelDescriptor(
                    id = dto.id,
                    displayName = dto.id,
                    description = "HCNSEC model",
                    capabilities = CapabilityRegistry.detectCapabilities(dto.id),
                )
            }
        }

    // ---------------------------------------------------------------------------------------
    // Request construction
    // ---------------------------------------------------------------------------------------

    private fun buildChatHttpRequest(
        request: ProviderChatRequest,
        credential: ProviderCredential,
        streaming: Boolean,
        timeoutMillis: Long?,
    ): ProviderHttpRequest {
        if (request.messages.isEmpty()) {
            throw ProviderException(
                ProviderErrors.invalidRequest("A chat request requires at least one message."),
            )
        }

        val model = config.resolveModel(request.model)
            ?: throw ProviderException(ProviderErrors.notConfigured(id, "no model is configured."))

        val wireRequest = HcnsecChatCompletionRequest(
            model = model,
            messages = request.messages.map { message ->
                HcnsecChatMessage(role = message.role.wireName, content = message.content)
            },
            stream = streaming,
            temperature = request.temperature,
            maxTokens = request.maxOutputTokens,
        )

        val body = runCatching { requestAdapter.toJson(wireRequest) }.getOrElse { failure ->
            throw ProviderException(
                ProviderErrors.invalidRequest(
                    message = "The HCNSEC request could not be serialized.",
                    diagnostic = failure.message,
                ),
            )
        }

        return ProviderHttpRequest(
            url = config.chatCompletionsUrl,
            method = "POST",
            headers = buildHeaders(credential, streaming),
            body = body,
            timeoutMillis = timeoutMillis,
        )
    }

    /**
     * Builds the outbound headers, including the bearer credential.
     *
     * The returned map is passed straight to the transport and is never logged, cached or
     * attached to an error object.
     */
    private fun buildHeaders(credential: ProviderCredential, streaming: Boolean): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${credential.value}",
        "Content-Type" to "application/json; charset=utf-8",
        "Accept" to if (streaming) "text/event-stream" else "application/json",
        "User-Agent" to USER_AGENT,
    )

    /** The credential for a request: only ever the securely stored one, never an argument. */
    private fun resolveCredential(): ProviderCredential? =
        credentials.storedCredential()?.takeIf { !it.isBlank }

    // ---------------------------------------------------------------------------------------
    // Error normalization
    // ---------------------------------------------------------------------------------------

    /** Records a failure against health + circuit breaker and returns it for throwing. */
    /** Records a normalized failure and returns it for `ProviderStreamEvent.Failed` emissions. */
    private fun recordFailure(
        error: ProviderError,
        rateLimit: ProviderRateLimitSnapshot = ProviderRateLimitSnapshot.UNKNOWN,
    ): ProviderError {
        health.recordFailure(error, rateLimit)
        circuitBreaker.recordFailure(error.kind)
        return error
    }

    /** Records a normalized failure and wraps it for the non-streaming (throwing) path. */
    private fun fail(
        error: ProviderError,
        rateLimit: ProviderRateLimitSnapshot = ProviderRateLimitSnapshot.UNKNOWN,
    ): ProviderException = ProviderException(recordFailure(error, rateLimit))

    private fun mapErrorResponse(status: Int, body: String): ProviderError {
        val detail = runCatching { errorAdapter.fromJson(body) }.getOrNull()?.error
        val providerMessage = detail?.message?.takeIf { it.isNotBlank() }
        val diagnostic = listOfNotNull(detail?.type, detail?.code?.toString(), providerMessage)
            .joinToString(" | ")
            .ifBlank { null }

        return when (status) {
            400, 409, 422 -> ProviderErrors.invalidRequest(
                providerMessage ?: "HCNSEC rejected the request as invalid.",
                status,
                diagnostic,
            )

            401 -> ProviderErrors.authentication(
                "HCNSEC rejected the API key. Please check your credentials in Settings.",
                status,
                diagnostic,
            )

            403 -> if (mentionsModel(providerMessage)) {
                ProviderErrors.modelUnavailable(
                    providerMessage ?: "This model is not available for the configured HCNSEC account.",
                    status,
                    diagnostic,
                )
            } else {
                ProviderErrors.authentication(
                    "HCNSEC denied access to this resource for the configured API key.",
                    status,
                    diagnostic,
                )
            }

            404 -> ProviderErrors.modelUnavailable(
                providerMessage ?: "The requested model is not available on HCNSEC.",
                status,
                diagnostic,
            )

            402 -> ProviderErrors.quotaExhausted(
                providerMessage ?: "HCNSEC reports that the account has no remaining quota.",
                status,
                diagnostic,
            )

            408, 504 -> ProviderErrors.timeout(
                "HCNSEC did not respond in time.",
                diagnostic,
            )

            429 -> if (isQuotaSignal(detail?.type, detail?.code?.toString(), providerMessage)) {
                ProviderErrors.quotaExhausted(
                    providerMessage ?: "The HCNSEC account has no remaining quota.",
                    status,
                    diagnostic,
                )
            } else {
                ProviderErrors.rateLimited(
                    providerMessage ?: "HCNSEC rate limit reached. Please retry shortly.",
                    status,
                    diagnostic,
                )
            }

            in 500..599 -> ProviderErrors.serverError(
                "HCNSEC is temporarily unavailable (HTTP $status).",
                status,
                diagnostic,
            )

            else -> ProviderError(
                kind = ProviderErrorKind.UNKNOWN,
                message = "The HCNSEC request failed (HTTP $status).",
                httpStatus = status,
                diagnostic = ProviderError.redact(diagnostic),
            )
        }
    }

    private fun mapErrorPayload(message: String?, type: String?, code: String?): ProviderError {
        val diagnostic = listOfNotNull(type, code, message).joinToString(" | ").ifBlank { null }
        val lower = diagnostic?.lowercase().orEmpty()

        return when {
            QUOTA_MARKERS.any { lower.contains(it) } -> ProviderErrors.quotaExhausted(
                message ?: "The HCNSEC account has no remaining quota.",
                429,
                diagnostic,
            )

            lower.contains("rate") || lower.contains("limit") -> ProviderErrors.rateLimited(
                message ?: "HCNSEC rate limit reached.",
                429,
                diagnostic,
            )

            lower.contains("auth") || lower.contains("api key") || lower.contains("unauthor") ->
                ProviderErrors.authentication(
                    message ?: "HCNSEC rejected the API key.",
                    401,
                    diagnostic,
                )

            else -> ProviderErrors.serverError(
                message ?: "HCNSEC reported an error while streaming.",
                500,
                diagnostic,
            )
        }
    }

    private fun mapTransportError(error: IOException): ProviderError = when {
        error is SocketTimeoutException -> ProviderErrors.timeout(
            "The HCNSEC request timed out.",
            error.message,
        )

        error is InterruptedIOException -> ProviderErrors.timeout(
            "The HCNSEC request was interrupted before it completed.",
            error.message,
        )

        error is UnknownHostException -> ProviderErrors.network(
            "Network unavailable. Please check your connection.",
            error.message,
        )

        error is SSLException -> ProviderErrors.network(
            "Could not establish a secure connection to HCNSEC.",
            error.message,
        )

        else -> ProviderErrors.network(
            "Could not reach HCNSEC (${error.javaClass.simpleName}).",
            error.message,
        )
    }

    private fun isQuotaSignal(type: String?, code: String?, message: String?): Boolean {
        val haystack = listOfNotNull(type, code, message).joinToString(" ").lowercase()
        return QUOTA_MARKERS.any { haystack.contains(it) }
    }

    private fun mentionsModel(message: String?): Boolean =
        message?.lowercase()?.contains("model") == true

    private fun HcnsecUsageDto.toProviderUsage(): ProviderUsage = ProviderUsage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
    )

    /** Internal representation of a parsed SSE payload. */
    private sealed interface StreamPayload {
        class Chunk(
            val content: String?,
            val reasoning: String?,
            val finishReason: String?,
            val usage: ProviderUsage?,
        ) : StreamPayload

        class Failure(val error: ProviderError) : StreamPayload

        data object Malformed : StreamPayload
    }
}
