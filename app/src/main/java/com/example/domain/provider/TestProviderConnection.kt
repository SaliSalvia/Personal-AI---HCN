package com.example.domain.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Outcome of a user-triggered connection test. Never contains credential material. */
data class ProviderTestConnectionResult(
    val provider: ProviderId,
    val success: Boolean,
    val model: String?,
    val latencyMillis: Long?,
    val errorKind: ProviderErrorKind?,
    val message: String,
)

/**
 * User-triggered "Test Connection" for a provider.
 *
 * Design rules (Phase 4.2 §14):
 * - the credential is read by the adapter from secure storage; this class never sees it;
 * - a minimal real request is sent with the configured model and a single output token, so the
 *   quota cost of a probe is negligible;
 * - the timeout is strict: it is enforced at the socket level through
 *   [ProviderChatRequest.timeoutMillis] (a coroutine timeout alone cannot interrupt a blocking
 *   read), with [TIMEOUT_GRACE_MILLIS] as a backstop in case a transport ignores deadlines;
 * - the result is normalized and the provider health state is updated by the adapter itself;
 * - **nothing here is ever called automatically** — neither at application startup nor when a
 *   screen opens. It runs only when the user taps the button.
 */
class TestProviderConnection(
    private val registry: ProviderRegistry,
    private val defaultTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        /** Strict probe deadline. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 10_000L

        /** Secondary coroutine-level guard, applied on top of the socket-level deadline. */
        const val TIMEOUT_GRACE_MILLIS: Long = 5_000L

        /** Minimal probe prompt: as small as an OpenAI-compatible request can reasonably be. */
        const val PROBE_PROMPT: String = "ping"

        /** One output token keeps probe quota usage negligible. */
        const val PROBE_MAX_OUTPUT_TOKENS: Int = 1
    }

    suspend fun execute(
        provider: ProviderId = ProviderId.HCNSEC,
        model: String? = null,
        timeoutMillis: Long = defaultTimeoutMillis,
    ): ProviderTestConnectionResult {
        require(timeoutMillis > 0) { "timeoutMillis must be greater than zero." }
        val startedAt = clock()

        return try {
            val adapter = registry.adapter(provider)

            val response = withTimeout(timeoutMillis + TIMEOUT_GRACE_MILLIS) {
                adapter.chat(
                    ProviderChatRequest(
                        model = model,
                        messages = listOf(ProviderMessage(ProviderRole.USER, PROBE_PROMPT)),
                        temperature = 0.0,
                        maxOutputTokens = PROBE_MAX_OUTPUT_TOKENS,
                        timeoutMillis = timeoutMillis,
                    ),
                )
            }

            val latency = if (response.latencyMillis > 0) response.latencyMillis else clock() - startedAt

            ProviderTestConnectionResult(
                provider = provider,
                success = true,
                model = response.model.ifBlank { model },
                latencyMillis = latency,
                errorKind = null,
                message = "${provider.displayName} responded successfully.",
            )
        } catch (e: TimeoutCancellationException) {
            ProviderTestConnectionResult(
                provider = provider,
                success = false,
                model = model,
                latencyMillis = null,
                errorKind = ProviderErrorKind.TIMEOUT,
                message = "The connection test timed out after ${timeoutMillis} ms.",
            )
        } catch (e: ProviderException) {
            ProviderTestConnectionResult(
                provider = provider,
                success = false,
                model = model,
                latencyMillis = clock() - startedAt,
                errorKind = e.kind,
                message = e.error.message,
            )
        } catch (e: CancellationException) {
            // A genuine caller cancellation is not a connection verdict.
            throw e
        } catch (e: Exception) {
            ProviderTestConnectionResult(
                provider = provider,
                success = false,
                model = model,
                latencyMillis = null,
                errorKind = ProviderErrorKind.UNKNOWN,
                message = "The connection test could not be completed.",
            )
        }
    }
}
