package com.example.domain.provider

/**
 * Normalized provider error taxonomy.
 *
 * Every failure from a provider adapter — HTTP, transport, parsing, configuration or
 * cancellation — is projected onto exactly one [ProviderErrorKind]. Higher layers never see
 * provider-specific error payloads, HTTP status codes or headers.
 *
 * SECURITY: a [ProviderError] must never contain credentials. [message] is user-safe by
 * construction and [diagnostic] is passed through [redact] before storage.
 */
enum class ProviderErrorKind {
    /** Credential missing, rejected or not authorised (HTTP 401/403). */
    AUTHENTICATION,

    /** The request itself was rejected as invalid (HTTP 400/422). */
    INVALID_REQUEST,

    /** The requested model does not exist or is not available on this plan (HTTP 404/403). */
    MODEL_UNAVAILABLE,

    /** Provider throttled the request (HTTP 429 without a quota signal). */
    RATE_LIMITED,

    /** Provider confirmed the account/quota is exhausted (as opposed to merely throttled). */
    QUOTA_EXHAUSTED,

    /** Connect/read/write timeout, or caller-supplied deadline exceeded. */
    TIMEOUT,

    /** Transport-level failure with no HTTP response (DNS, TLS, connection reset). */
    NETWORK,

    /** Provider returned 5xx. */
    SERVER_ERROR,

    /** Provider returned a body that could not be parsed into the expected shape. */
    MALFORMED_RESPONSE,

    /** Caller cancelled the request. Never a provider fault. */
    CANCELLED,

    /** Provider is administratively disabled in the registry. */
    PROVIDER_DISABLED,

    /** No credential or no model configured for this provider. */
    NOT_CONFIGURED,

    /** Circuit breaker is open; the request was not attempted. */
    CIRCUIT_OPEN,

    /** Anything not covered above. */
    UNKNOWN,
}

/**
 * A single normalized provider failure.
 *
 * @param message user-facing, provider-neutral, secret-free text.
 * @param httpStatus HTTP status code when one was received, else null.
 * @param diagnostic internal debugging hint. Always passed through [redact] and never surfaced
 *   in UI state.
 * @param retryable whether an automatic retry may legitimately help.
 * @param failoverAllowed whether another provider/method may be tried for this request.
 *   Retrying authentication, quota and invalid-request failures is explicitly forbidden by the
 *   Phase 4.2 rules, and failover for them is not useful either.
 */
data class ProviderError(
    val kind: ProviderErrorKind,
    val message: String,
    val httpStatus: Int? = null,
    val diagnostic: String? = null,
    val retryable: Boolean = ProviderErrorPolicy.isRetryable(kind),
    val failoverAllowed: Boolean = ProviderErrorPolicy.isFailoverAllowed(kind),
) {
    /** Safe, loggable one-liner. Contains no credential material by construction. */
    override fun toString(): String =
        "ProviderError(kind=$kind, httpStatus=${httpStatus ?: "-"}, message=$message)"

    companion object {
        /**
         * Strips anything that could be a credential from a diagnostic string.
         *
         * Applied to every diagnostic before it is stored on an error, and unit-tested against
         * bearer tokens and key-shaped strings.
         */
        fun redact(raw: String?): String? {
            if (raw == null) return null
            var value = raw
            // Remove "Authorization: Bearer <token>" / "Bearer <token>" fragments.
            value = value.replace(Regex("(?i)bearer\\s+\\S+"), "Bearer <redacted>")
            // Remove anything that looks like a provider API key.
            value = value.replace(Regex("(?i)\\b(sk|rk|pk|api|key)[-_][A-Za-z0-9._-]{6,}"), "<redacted>")
            // Never keep control characters or unbounded payloads.
            value = value.filter { it.code >= 0x20 || it == '\t' }
            return value.take(MAX_DIAGNOSTIC_LENGTH)
        }

        private const val MAX_DIAGNOSTIC_LENGTH = 400
    }
}

/** The single exception type thrown by provider adapters. */
class ProviderException(
    val error: ProviderError,
    cause: Throwable? = null,
) : Exception(error.message, cause) {
    /** The normalized classification of this failure. */
    val kind: ProviderErrorKind get() = error.kind
}

/**
 * Retry / failover eligibility rules.
 *
 * Rules implemented (Phase 4.2 §21):
 * - never retry authentication failures;
 * - never retry confirmed quota exhaustion;
 * - never retry invalid requests or unavailable models;
 * - never retry a cancelled request;
 * - a disabled/unconfigured provider is not retried;
 * - transient transport/server/throttle failures may be retried by the caller.
 */
object ProviderErrorPolicy {

    fun isRetryable(kind: ProviderErrorKind): Boolean = when (kind) {
        ProviderErrorKind.TIMEOUT,
        ProviderErrorKind.NETWORK,
        ProviderErrorKind.SERVER_ERROR,
        ProviderErrorKind.RATE_LIMITED,
        -> true

        ProviderErrorKind.AUTHENTICATION,
        ProviderErrorKind.INVALID_REQUEST,
        ProviderErrorKind.MODEL_UNAVAILABLE,
        ProviderErrorKind.QUOTA_EXHAUSTED,
        ProviderErrorKind.MALFORMED_RESPONSE,
        ProviderErrorKind.CANCELLED,
        ProviderErrorKind.PROVIDER_DISABLED,
        ProviderErrorKind.NOT_CONFIGURED,
        ProviderErrorKind.CIRCUIT_OPEN,
        ProviderErrorKind.UNKNOWN,
        -> false
    }

    fun isFailoverAllowed(kind: ProviderErrorKind): Boolean = when (kind) {
        ProviderErrorKind.PROVIDER_DISABLED,
        ProviderErrorKind.NOT_CONFIGURED,
        ProviderErrorKind.CIRCUIT_OPEN,
        ProviderErrorKind.NETWORK,
        ProviderErrorKind.TIMEOUT,
        ProviderErrorKind.SERVER_ERROR,
        -> true

        ProviderErrorKind.AUTHENTICATION,
        ProviderErrorKind.QUOTA_EXHAUSTED,
        ProviderErrorKind.RATE_LIMITED,
        ProviderErrorKind.INVALID_REQUEST,
        ProviderErrorKind.MODEL_UNAVAILABLE,
        ProviderErrorKind.MALFORMED_RESPONSE,
        ProviderErrorKind.CANCELLED,
        ProviderErrorKind.UNKNOWN,
        -> false
    }

    /**
     * Whether a failure should count towards the circuit breaker.
     *
     * Only transient provider faults count. An authentication failure or confirmed quota
     * exhaustion must never permanently disable a provider after one occurrence.
     */
    fun countsTowardCircuit(kind: ProviderErrorKind): Boolean = when (kind) {
        ProviderErrorKind.NETWORK,
        ProviderErrorKind.TIMEOUT,
        ProviderErrorKind.SERVER_ERROR,
        -> true

        else -> false
    }
}

/** Factory helpers keeping error construction consistent and secret-free. */
object ProviderErrors {

    fun authentication(message: String, status: Int? = 401, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.AUTHENTICATION, message, status, ProviderError.redact(diagnostic))

    fun invalidRequest(message: String, status: Int? = 400, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.INVALID_REQUEST, message, status, ProviderError.redact(diagnostic))

    fun modelUnavailable(message: String, status: Int? = 404, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.MODEL_UNAVAILABLE, message, status, ProviderError.redact(diagnostic))

    fun rateLimited(message: String, status: Int? = 429, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.RATE_LIMITED, message, status, ProviderError.redact(diagnostic))

    fun quotaExhausted(message: String, status: Int? = 429, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.QUOTA_EXHAUSTED, message, status, ProviderError.redact(diagnostic))

    fun timeout(message: String, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.TIMEOUT, message, null, ProviderError.redact(diagnostic))

    fun network(message: String, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.NETWORK, message, null, ProviderError.redact(diagnostic))

    fun serverError(message: String, status: Int? = 500, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.SERVER_ERROR, message, status, ProviderError.redact(diagnostic))

    fun malformedResponse(message: String, status: Int? = null, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.MALFORMED_RESPONSE, message, status, ProviderError.redact(diagnostic))

    fun cancelled() = ProviderError(ProviderErrorKind.CANCELLED, "Request cancelled.")

    fun disabled(provider: ProviderId) =
        ProviderError(ProviderErrorKind.PROVIDER_DISABLED, "${provider.displayName} is disabled.")

    fun notConfigured(provider: ProviderId, detail: String) =
        ProviderError(ProviderErrorKind.NOT_CONFIGURED, "${provider.displayName}: $detail")

    fun circuitOpen(provider: ProviderId) = ProviderError(
        ProviderErrorKind.CIRCUIT_OPEN,
        "${provider.displayName} is temporarily paused after repeated failures.",
    )

    fun unknown(message: String, diagnostic: String? = null) =
        ProviderError(ProviderErrorKind.UNKNOWN, message, null, ProviderError.redact(diagnostic))
}
