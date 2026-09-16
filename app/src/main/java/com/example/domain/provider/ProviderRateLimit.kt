package com.example.domain.provider

/**
 * A value that a provider may or may not report.
 *
 * The distinction between [Unknown] and a known zero is critical for routing and for the UI
 * (Phase 4.2 §10): "we were not told" must never be rendered or treated as "zero remaining".
 */
sealed interface ProviderValue<out T> {
    /** The provider did not report this value. */
    data object Unknown : ProviderValue<Nothing>

    /** The provider explicitly reported this value (including zero). */
    data class Known<out T>(val value: T) : ProviderValue<T> {
        override fun toString(): String = value.toString()
    }
}

/** True when the value was explicitly reported by the provider. */
val ProviderValue<*>.isKnown: Boolean get() = this is ProviderValue.Known<*>

/** True when the provider did not report the value. */
val ProviderValue<*>.isUnknown: Boolean get() = this is ProviderValue.Unknown

/** Human-readable rendering that keeps UNKNOWN visually distinct from zero. */
fun ProviderValue<*>.displayText(unknownLabel: String = "unknown"): String =
    if (this is ProviderValue.Known<*>) value.toString() else unknownLabel

/** Where rate-limit information came from. */
enum class RateLimitSource {
    NONE,
    RESPONSE_HEADERS,
}

/**
 * Normalized, provider-neutral rate-limit snapshot.
 *
 * Only values that the provider actually returned are populated; everything else stays
 * [ProviderValue.Unknown]. This class never synthesises limits, resets or counters.
 */
data class ProviderRateLimitSnapshot(
    val limitRequests: ProviderValue<Int> = ProviderValue.Unknown,
    val remainingRequests: ProviderValue<Int> = ProviderValue.Unknown,
    val limitTokens: ProviderValue<Int> = ProviderValue.Unknown,
    val remainingTokens: ProviderValue<Int> = ProviderValue.Unknown,
    val resetAtMillis: ProviderValue<Long> = ProviderValue.Unknown,
    val retryAfterMillis: ProviderValue<Long> = ProviderValue.Unknown,
    val source: RateLimitSource = RateLimitSource.NONE,
    val observedAtMillis: Long? = null,
) {
    /** True when the provider gave us nothing usable. */
    fun isUnknown(): Boolean =
        source == RateLimitSource.NONE &&
            limitRequests.isUnknown &&
            remainingRequests.isUnknown &&
            limitTokens.isUnknown &&
            remainingTokens.isUnknown &&
            resetAtMillis.isUnknown &&
            retryAfterMillis.isUnknown

    companion object {
        /** A snapshot that claims nothing. */
        val UNKNOWN: ProviderRateLimitSnapshot = ProviderRateLimitSnapshot()
    }
}

/** Origin of quota information. */
enum class ProviderQuotaSource {
    /** Nothing is known about the quota. */
    UNKNOWN,

    /** Values came from response headers. */
    RESPONSE_HEADERS,

    /** The provider reported exhaustion through its error payload. */
    ERROR_PAYLOAD,
}

/**
 * Normalized, provider-neutral quota snapshot.
 *
 * [exhausted] is an explicit signal (the provider told us the quota is gone). Numeric amounts are
 * only ever populated when the provider actually reported them — see [ProviderValue].
 */
data class ProviderQuotaSnapshot(
    val exhausted: Boolean = false,
    val remaining: ProviderValue<Long> = ProviderValue.Unknown,
    val total: ProviderValue<Long> = ProviderValue.Unknown,
    val resetAtMillis: ProviderValue<Long> = ProviderValue.Unknown,
    val source: ProviderQuotaSource = ProviderQuotaSource.UNKNOWN,
    val observedAtMillis: Long? = null,
) {
    companion object {
        /** A snapshot that claims nothing. */
        val UNKNOWN: ProviderQuotaSnapshot = ProviderQuotaSnapshot()

        /**
         * Quota confirmed exhausted by the provider's own error payload.
         *
         * Deliberately leaves the numeric amounts UNKNOWN rather than reporting `0`: an explicit
         * "insufficient quota" statement is not the same information as a reported remaining
         * balance of zero, and Phase 4.2 forbids fabricating amounts.
         */
        fun confirmedExhausted(observedAtMillis: Long): ProviderQuotaSnapshot = ProviderQuotaSnapshot(
            exhausted = true,
            remaining = ProviderValue.Unknown,
            total = ProviderValue.Unknown,
            resetAtMillis = ProviderValue.Unknown,
            source = ProviderQuotaSource.ERROR_PAYLOAD,
            observedAtMillis = observedAtMillis,
        )
    }
}

/**
 * Parses rate-limit information from response headers.
 *
 * Handles the conventional `x-ratelimit-*` request/token headers plus `retry-after`
 * (delta-seconds). Header names are matched case-insensitively. Anything absent or unparseable
 * stays [ProviderValue.Unknown] — the parser never invents values.
 */
object ProviderRateLimitParser {

    fun fromHeaders(headers: Map<String, String>, nowMillis: Long): ProviderRateLimitSnapshot {
        if (headers.isEmpty()) return ProviderRateLimitSnapshot.UNKNOWN

        val normalized = headers.entries.associate { it.key.lowercase() to it.value.trim() }

        val limitRequests = intValue(normalized["x-ratelimit-limit-requests"])
        val remainingRequests = intValue(normalized["x-ratelimit-remaining-requests"])
        val limitTokens = intValue(normalized["x-ratelimit-limit-tokens"])
        val remainingTokens = intValue(normalized["x-ratelimit-remaining-tokens"])
        val resetAt = resetInstant(normalized["x-ratelimit-reset-requests"], nowMillis)
            ?: resetInstant(normalized["x-ratelimit-reset-tokens"], nowMillis)
        val retryAfter = retryAfterMillis(normalized["retry-after"])

        val populated = listOfNotNull(
            limitRequests, remainingRequests, limitTokens, remainingTokens, resetAt, retryAfter,
        )
        if (populated.isEmpty()) return ProviderRateLimitSnapshot.UNKNOWN

        return ProviderRateLimitSnapshot(
            limitRequests = limitRequests ?: ProviderValue.Unknown,
            remainingRequests = remainingRequests ?: ProviderValue.Unknown,
            limitTokens = limitTokens ?: ProviderValue.Unknown,
            remainingTokens = remainingTokens ?: ProviderValue.Unknown,
            resetAtMillis = resetAt ?: ProviderValue.Unknown,
            retryAfterMillis = retryAfter ?: ProviderValue.Unknown,
            source = RateLimitSource.RESPONSE_HEADERS,
            observedAtMillis = nowMillis,
        )
    }

    private fun intValue(raw: String?): ProviderValue<Int>? {
        val parsed = raw?.trim()?.toIntOrNull() ?: return null
        return ProviderValue.Known(parsed)
    }

    private fun resetInstant(raw: String?, nowMillis: Long): ProviderValue<Long>? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val duration = parseDurationMillis(value)
        if (duration != null) return ProviderValue.Known(nowMillis + duration)

        val numeric = value.toLongOrNull() ?: return null
        return when {
            numeric >= EPOCH_MILLIS_THRESHOLD -> ProviderValue.Known(numeric)
            numeric >= EPOCH_SECONDS_THRESHOLD -> ProviderValue.Known(numeric * 1_000L)
            numeric >= 0 -> ProviderValue.Known(nowMillis + numeric * 1_000L)
            else -> null
        }
    }

    private fun retryAfterMillis(raw: String?): ProviderValue<Long>? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val duration = parseDurationMillis(value)
        if (duration != null) return ProviderValue.Known(duration)

        val seconds = value.toLongOrNull() ?: return null
        if (seconds < 0) return null
        return ProviderValue.Known(seconds * 1_000L)
    }

    /**
     * Parses duration strings such as `1s`, `100ms`, `6m0s` or `1h2m3s`.
     * Returns null when the string is not a recognizable duration.
     */
    private fun parseDurationMillis(raw: String): Long? {
        if (raw.isEmpty()) return null
        if (raw.all { it.isDigit() }) return null

        var index = 0
        var totalMillis = 0L
        var matched = false

        while (index < raw.length) {
            val start = index
            while (index < raw.length && raw[index].isDigit()) index++
            if (start == index) return null

            val amount = raw.substring(start, index).toLongOrNull() ?: return null

            val unitStart = index
            while (index < raw.length && !raw[index].isDigit()) index++
            val unit = raw.substring(unitStart, index).lowercase()

            val multiplier = when (unit) {
                "ms" -> 1L
                "s" -> 1_000L
                "m" -> 60_000L
                "h" -> 3_600_000L
                else -> return null
            }

            totalMillis += amount * multiplier
            matched = true
        }

        return if (matched) totalMillis else null
    }

    private const val EPOCH_SECONDS_THRESHOLD = 1_000_000_000L
    private const val EPOCH_MILLIS_THRESHOLD = 1_000_000_000_000L
}
