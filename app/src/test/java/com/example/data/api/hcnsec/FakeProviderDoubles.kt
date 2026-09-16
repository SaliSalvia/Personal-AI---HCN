package com.example.data.api.hcnsec

import com.example.data.network.ProviderHttpRequest
import com.example.data.network.ProviderHttpResponse
import com.example.data.network.ProviderHttpTransport
import com.example.data.network.ProviderStreamHandle
import com.example.data.security.ProviderCredential
import com.example.data.security.ProviderCredentialSource
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test doubles for the provider layer.
 *
 * SECURITY: [TEST_CREDENTIAL] is an obvious non-secret sentinel. No real HCNSEC key exists in this
 * repository, and the sentinel deliberately does not resemble any real provider key format.
 */
internal const val TEST_CREDENTIAL: String = "unit-test-credential-not-a-real-key"

internal const val TEST_MODEL: String = "hcnsec-unit-test-model"

/** Credential abstraction fake: the adapter must depend on this, never on secure storage. */
internal class FakeCredentialSource(private var stored: String? = TEST_CREDENTIAL) : ProviderCredentialSource {

    var reads: Int = 0
        private set

    override fun storedCredential(): ProviderCredential? {
        reads += 1
        return stored?.let { ProviderCredential(it) }
    }

    override fun hasCredential(): Boolean = stored != null

    fun setStored(value: String?) {
        stored = value
    }
}

/** Records every outbound request so tests can assert on URL, headers and body. */
internal class FakeHttpTransport : ProviderHttpTransport {

    val requests = mutableListOf<ProviderHttpRequest>()

    var response: ProviderHttpResponse? = null
    var failure: IOException? = null
    var streamHandle: ProviderStreamHandle? = null

    override fun execute(request: ProviderHttpRequest): ProviderHttpResponse {
        requests += request
        failure?.let { throw it }
        return response ?: error("FakeHttpTransport has no canned response")
    }

    override fun openStream(request: ProviderHttpRequest): ProviderStreamHandle {
        requests += request
        failure?.let { throw it }
        return streamHandle ?: error("FakeHttpTransport has no canned stream")
    }

    val lastRequest: ProviderHttpRequest get() = requests.last()
}

/** Deterministic SSE stream fake. */
internal class FakeStreamHandle(
    override val statusCode: Int = 200,
    override val headers: Map<String, String> = emptyMap(),
    private val lines: List<String> = emptyList(),
    private val errorBody: String = "",
) : ProviderStreamHandle {

    private var index = 0

    var closed: Boolean = false
        private set

    var cancelled: Boolean = false
        private set

    override fun readLine(): String? {
        if (statusCode !in 200..299) return null
        return if (index < lines.size) lines[index++] else null
    }

    override fun readErrorBody(): String = errorBody

    override fun close() {
        closed = true
    }

    override fun cancel() {
        cancelled = true
    }
}

/**
 * Stream fake whose read blocks until the test cancels the surrounding coroutine.
 * Used to prove that cancellation really aborts the in-flight call.
 */
internal class BlockingStreamHandle(
    private val readStarted: CountDownLatch,
    private val release: CountDownLatch,
) : ProviderStreamHandle {

    override val statusCode: Int = 200
    override val headers: Map<String, String> = emptyMap()

    private var served = false

    var cancelled: Boolean = false
        private set

    override fun readLine(): String? {
        if (!served) {
            served = true
            readStarted.countDown()
            release.await(10, TimeUnit.SECONDS)
            return ": keepalive"
        }
        return null
    }

    override fun readErrorBody(): String = ""

    override fun close() {
        release.countDown()
    }

    override fun cancel() {
        cancelled = true
        release.countDown()
    }
}
