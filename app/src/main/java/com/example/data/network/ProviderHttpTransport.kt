package com.example.data.network

/**
 * Provider-neutral HTTP seam.
 *
 * The provider adapter talks to the network only through this interface, which keeps adapters
 * unit-testable without a real socket (tests supply a fake transport) and keeps raw HTTP types
 * out of the provider layer.
 */
interface ProviderHttpTransport {

    /** Executes a non-streaming request and returns the full response. */
    fun execute(request: ProviderHttpRequest): ProviderHttpResponse

    /**
     * Opens a streaming response.
     *
     * The returned handle is closed by the caller. Cancellation of the surrounding coroutine is
     * propagated by the caller through [ProviderStreamHandle.cancel], which must unblock any
     * in-flight read.
     */
    fun openStream(request: ProviderHttpRequest): ProviderStreamHandle
}

/** A provider-neutral outbound HTTP request. */
data class ProviderHttpRequest(
    val url: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    /**
     * Whole-call timeout in milliseconds, enforced by the transport. Null means "use the
     * transport default". This bounds a request even when the caller is blocked in IO.
     */
    val timeoutMillis: Long? = null,
)

/** A fully buffered provider-neutral HTTP response. */
data class ProviderHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val latencyMillis: Long = 0L,
)

/**
 * A line-oriented streaming response handle.
 *
 * Implementations are not required to be thread-safe for [readLine], but [cancel] must be safe to
 * call from another thread while a read is in progress.
 */
interface ProviderStreamHandle {
    val statusCode: Int
    val headers: Map<String, String>

    /** Reads the next line, or null at end of stream. Blocks until data is available. */
    fun readLine(): String?

    /** Error body for a non-2xx response. Empty for successful responses. */
    fun readErrorBody(): String

    /** Releases resources. Safe to call more than once. */
    fun close()

    /** Aborts the underlying call. Safe to call from another thread. */
    fun cancel()
}

/** Transports reject plaintext endpoints; only HTTPS is accepted (Phase 4.2 §17). */
class InsecureEndpointException(url: String) :
    IllegalArgumentException("Refusing a non-HTTPS provider endpoint: ${url.take(32)}")

/** Fails closed: only https URLs are permitted, and TLS validation is never bypassed. */
internal fun requireHttps(url: String): String {
    if (!url.startsWith("https://", ignoreCase = true)) throw InsecureEndpointException(url)
    return url
}
