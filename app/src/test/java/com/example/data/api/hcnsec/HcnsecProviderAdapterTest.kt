package com.example.data.api.hcnsec

import com.example.data.network.ProviderHttpResponse
import com.example.data.network.ProviderHttpTransport
import com.example.data.security.ProviderCredentialSource
import com.example.domain.provider.CircuitState
import com.example.domain.provider.ProviderChatRequest
import com.example.domain.provider.ProviderCircuitBreaker
import com.example.domain.provider.ProviderErrorKind
import com.example.domain.provider.ProviderErrorPolicy
import com.example.domain.provider.ProviderException
import com.example.domain.provider.ProviderHealthState
import com.example.domain.provider.ProviderHealthTracker
import com.example.domain.provider.ProviderMessage
import com.example.domain.provider.ProviderRole
import com.example.domain.provider.ProviderStreamEvent
import com.example.domain.provider.ProviderValue
import com.example.domain.provider.isUnknown
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Deterministic unit tests for the HCNSEC adapter.
 *
 * No real network is used: every test drives a fake transport, so the suite cannot consume HCNSEC
 * quota and cannot depend on connectivity. No real API key appears anywhere in this file.
 */
class HcnsecProviderAdapterTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val requestAdapter = moshi.adapter(HcnsecChatCompletionRequest::class.java)

    // -------------------------------------------------------------------------------------
    // A. Construction and boundary
    // -------------------------------------------------------------------------------------

    @Test
    fun `adapter advertises provider identity and capabilities`() {
        val adapter = buildAdapter()

        assertEquals(com.example.domain.provider.ProviderId.HCNSEC, adapter.id)
        assertTrue(adapter.capabilities.contains(com.example.domain.provider.ProviderCapability.CHAT_COMPLETION))
        assertTrue(adapter.capabilities.contains(com.example.domain.provider.ProviderCapability.STREAMING))
        assertTrue(adapter.capabilities.contains(com.example.domain.provider.ProviderCapability.MODEL_LISTING))
    }

    @Test
    fun `plaintext endpoints are rejected - https only`() {
        assertThrows(IllegalArgumentException::class.java) {
            HcnsecProviderConfig(baseUrl = "http://api.hcnsec.cn/v1")
        }
    }

    @Test
    fun `config resolves models without inventing one`() {
        assertEquals("explicit-model", HcnsecProviderConfig().resolveModel("explicit-model"))
        assertNull(HcnsecProviderConfig().resolveModel(null))
        // The router sentinel must never be transmitted as a literal model id.
        assertNull(HcnsecProviderConfig(defaultModel = "auto").resolveModel("auto"))
        assertEquals("configured-default", HcnsecProviderConfig(defaultModel = "configured-default").resolveModel("auto"))
        assertEquals(
            "first-configured",
            HcnsecProviderConfig(configuredModels = listOf("first-configured", "second")).resolveModel(null),
        )
    }

    @Test
    fun `urls are the documented hcnsec endpoints`() {
        val config = HcnsecProviderConfig()
        assertEquals("https://api.hcnsec.cn/v1/chat/completions", config.chatCompletionsUrl)
        assertEquals("https://api.hcnsec.cn/v1/models", config.modelsUrl)
    }

    // -------------------------------------------------------------------------------------
    // B. Request serialization + authorization header
    // -------------------------------------------------------------------------------------

    @Test
    fun `serializes an openai compatible request and posts it once`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = successResponse() }
        val adapter = buildAdapter(transport = transport)

        val result = adapter.chat(
            ProviderChatRequest(
                model = TEST_MODEL,
                messages = listOf(
                    ProviderMessage(ProviderRole.SYSTEM, "be terse"),
                    ProviderMessage(ProviderRole.USER, "hello"),
                ),
                temperature = 0.25,
                maxOutputTokens = 1,
            ),
        )

        assertEquals(1, transport.requests.size)

        val sent = transport.lastRequest
        assertEquals("POST", sent.method)
        assertEquals("https://api.hcnsec.cn/v1/chat/completions", sent.url)

        val body = requestAdapter.fromJson(sent.body.orEmpty())
        assertNotNull(body)
        assertEquals(TEST_MODEL, body?.model)
        assertEquals(false, body?.stream)
        assertEquals(0.25, body?.temperature ?: -1.0, 0.0001)
        assertEquals(1, body?.maxTokens)
        assertEquals(listOf("system", "user"), body?.messages?.map { it.role })
        assertEquals(listOf("be terse", "hello"), body?.messages?.map { it.content })

        assertEquals(TEST_MODEL, result.model)
    }

    @Test
    fun `authorization header carries the stored credential and nothing leaks into url or body`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = successResponse() }
        val credentials = FakeCredentialSource()
        val adapter = buildAdapter(transport = transport, credentials = credentials)

        adapter.chat(chatRequest())

        val sent = transport.lastRequest
        assertEquals("Bearer $TEST_CREDENTIAL", sent.headers["Authorization"])
        assertTrue(credentials.reads >= 1)

        // The secret must exist only in the Authorization header.
        assertFalse(sent.url.contains(TEST_CREDENTIAL))
        assertFalse(sent.body.orEmpty().contains(TEST_CREDENTIAL))
        assertEquals("application/json; charset=utf-8", sent.headers["Content-Type"])
    }

    @Test
    fun `missing credential fails closed without touching the network`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = successResponse() }
        val adapter = buildAdapter(transport = transport, credentials = FakeCredentialSource(stored = null))

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.NOT_CONFIGURED, failure.kind)
        assertTrue("no request may be sent without a credential", transport.requests.isEmpty())
    }

    @Test
    fun `missing model configuration is a normalized not-configured failure`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = successResponse() }
        val adapter = buildAdapter(transport = transport, config = HcnsecProviderConfig())

        val failure = assertThrows(ProviderException::class.java) {
            runBlocking { adapter.chat(ProviderChatRequest(model = null, messages = listOf(ProviderMessage(ProviderRole.USER, "hi")))) }
        }

        assertEquals(ProviderErrorKind.NOT_CONFIGURED, failure.kind)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `empty message list is rejected before the network call`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = successResponse() }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) {
            runBlocking { adapter.chat(ProviderChatRequest(model = TEST_MODEL, messages = emptyList())) }
        }

        assertEquals(ProviderErrorKind.INVALID_REQUEST, failure.kind)
        assertTrue(transport.requests.isEmpty())
    }

    // -------------------------------------------------------------------------------------
    // C. Successful response normalization
    // -------------------------------------------------------------------------------------

    @Test
    fun `successful response is normalized into the provider neutral model`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = successResponse() }
        val adapter = buildAdapter(transport = transport)

        val response = adapter.chat(chatRequest())

        assertEquals("Hello from the fixture.", response.text)
        assertEquals(TEST_MODEL, response.model)
        assertEquals("stop", response.finishReason)
        assertEquals(3, response.usage?.promptTokens)
        assertEquals(4, response.usage?.completionTokens)
        assertEquals(7, response.usage?.totalTokens)
        assertNull(response.reasoningText)
    }

    @Test
    fun `reasoning content is exposed without affecting the answer text`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse(
                """
                {"model":"$TEST_MODEL","choices":[{"index":0,"finish_reason":"stop",
                 "message":{"role":"assistant","content":"42","reasoning_content":"because"}}]}
                """,
            )
        }
        val adapter = buildAdapter(transport = transport)

        val response = adapter.chat(chatRequest())

        assertEquals("42", response.text)
        assertEquals("because", response.reasoningText)
    }

    @Test
    fun `missing usage is reported as unknown rather than zero`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse(
                """{"model":"$TEST_MODEL","choices":[{"index":0,"finish_reason":"stop","message":{"content":"ok"}}]}""",
            )
        }
        val adapter = buildAdapter(transport = transport)

        val response = adapter.chat(chatRequest())

        assertNull(response.usage)
    }

    // -------------------------------------------------------------------------------------
    // D. Error normalization
    // -------------------------------------------------------------------------------------

    @Test
    fun `authentication failure is normalized and never retried`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse(
                """{"error":{"message":"Invalid API key provided","type":"invalid_request_error","code":"invalid_api_key"}}""",
                status = 401,
            )
        }
        val health = ProviderHealthTracker()
        val adapter = buildAdapter(transport = transport, health = health)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.AUTHENTICATION, failure.kind)
        assertEquals(401, failure.error.httpStatus)
        assertFalse("authentication failures must never be retried", failure.error.retryable)
        assertFalse(failure.error.failoverAllowed)
        assertFalse(ProviderErrorPolicy.isRetryable(ProviderErrorKind.AUTHENTICATION))
        assertEquals(ProviderHealthState.AUTH_ERROR, health.snapshot.value.state)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `rate limiting is normalized and stays retryable`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse(
                """{"error":{"message":"Rate limit reached for requests","type":"rate_limit_exceeded"}}""",
                status = 429,
            )
        }
        val health = ProviderHealthTracker()
        val adapter = buildAdapter(transport = transport, health = health)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.RATE_LIMITED, failure.kind)
        assertTrue(failure.error.retryable)
        assertEquals(ProviderHealthState.RATE_LIMITED, health.snapshot.value.state)
        assertFalse("a rate limit is not a confirmed quota exhaustion", health.snapshot.value.quota.exhausted)
    }

    @Test
    fun `quota exhaustion is distinguished from rate limiting and never retried`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse(
                """{"error":{"message":"You exceeded your current quota","type":"insufficient_quota"}}""",
                status = 429,
            )
        }
        val health = ProviderHealthTracker()
        val adapter = buildAdapter(transport = transport, health = health)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.QUOTA_EXHAUSTED, failure.kind)
        assertFalse("quota exhaustion must never be retried", failure.error.retryable)
        assertEquals(ProviderHealthState.QUOTA_EXHAUSTED, health.snapshot.value.state)

        val quota = health.snapshot.value.quota
        assertTrue("exhaustion is reported explicitly", quota.exhausted)
        assertTrue("an exhaustion signal must not invent a remaining amount", quota.remaining.isUnknown)
        assertTrue(
            "UNKNOWN must stay distinguishable from ZERO",
            quota.remaining != ProviderValue.Known(0L),
        )
    }

    @Test
    fun `payment required is treated as quota exhaustion`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse("""{"error":{"message":"Payment required"}}""", status = 402)
        }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.QUOTA_EXHAUSTED, failure.kind)
    }

    @Test
    fun `invalid request is normalized and not retryable`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse("""{"error":{"message":"messages must not be empty"}}""", status = 400)
        }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.INVALID_REQUEST, failure.kind)
        assertFalse(failure.error.retryable)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `unavailable model is normalized separately from authentication`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse("""{"error":{"message":"The model does not exist"}}""", status = 404)
        }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.MODEL_UNAVAILABLE, failure.kind)
        assertFalse(failure.error.retryable)
    }

    @Test
    fun `server error is normalized as transient and not retried aggressively`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse("""{"error":{"message":"upstream failure"}}""", status = 503)
        }
        val health = ProviderHealthTracker()
        val adapter = buildAdapter(transport = transport, health = health)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.SERVER_ERROR, failure.kind)
        assertTrue(failure.error.retryable)
        assertEquals(ProviderHealthState.DEGRADED, health.snapshot.value.state)
        assertEquals("the adapter itself must not retry", 1, transport.requests.size)
    }

    @Test
    fun `malformed response is normalized`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = jsonResponse("this is not json") }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.MALFORMED_RESPONSE, failure.kind)
        assertFalse(failure.error.retryable)
    }

    @Test
    fun `response without choices is treated as malformed`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = jsonResponse("""{"model":"$TEST_MODEL","choices":[]}""") }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.MALFORMED_RESPONSE, failure.kind)
    }

    @Test
    fun `http 200 carrying an error envelope is a failure not an empty answer`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse("""{"error":{"message":"insufficient_quota","type":"insufficient_quota"}}""")
        }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.QUOTA_EXHAUSTED, failure.kind)
    }

    @Test
    fun `socket timeout is normalized as timeout`() = runBlocking {
        val transport = FakeHttpTransport().apply { failure = SocketTimeoutException("timeout") }
        val health = ProviderHealthTracker()
        val adapter = buildAdapter(transport = transport, health = health)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.TIMEOUT, failure.kind)
        assertTrue(failure.error.retryable)
        assertEquals(ProviderHealthState.NETWORK_ERROR, health.snapshot.value.state)
    }

    @Test
    fun `dns and tls failures are normalized as network errors without tls bypass`() = runBlocking {
        val dnsTransport = FakeHttpTransport().apply { failure = UnknownHostException("api.hcnsec.cn") }
        val dnsFailure = assertThrows(ProviderException::class.java) {
            runBlocking { buildAdapter(transport = dnsTransport).chat(chatRequest()) }
        }
        assertEquals(ProviderErrorKind.NETWORK, dnsFailure.kind)

        val tlsTransport = FakeHttpTransport().apply { failure = SSLException("certificate validation failed") }
        val tlsFailure = assertThrows(ProviderException::class.java) {
            runBlocking { buildAdapter(transport = tlsTransport).chat(chatRequest()) }
        }
        assertEquals(ProviderErrorKind.NETWORK, tlsFailure.kind)
        assertTrue(tlsFailure.error.retryable)
    }

    @Test
    fun `unexpected io failure is normalized as network`() = runBlocking {
        val transport = FakeHttpTransport().apply { failure = IOException("connection reset") }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.NETWORK, failure.kind)
    }

    // -------------------------------------------------------------------------------------
    // E. Health, circuit breaker, rate limit metadata
    // -------------------------------------------------------------------------------------

    @Test
    fun `successful request marks the provider available`() = runBlocking {
        val health = ProviderHealthTracker()
        val breaker = ProviderCircuitBreaker()
        val transport = FakeHttpTransport().apply { response = successResponse() }
        val adapter = buildAdapter(transport = transport, health = health, breaker = breaker)

        adapter.chat(chatRequest())

        assertEquals(ProviderHealthState.AVAILABLE, health.snapshot.value.state)
        assertEquals(0, health.snapshot.value.consecutiveFailures)
        assertNull(health.snapshot.value.lastErrorMessage)
        assertEquals(CircuitState.CLOSED, breaker.currentState())
    }

    @Test
    fun `repeated transient failures open the circuit and block further requests`() = runBlocking {
        val health = ProviderHealthTracker()
        val breaker = ProviderCircuitBreaker()
        val transport = FakeHttpTransport().apply { failure = IOException("network down") }
        val adapter = buildAdapter(transport = transport, health = health, breaker = breaker)

        repeat(3) {
            assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }
        }

        assertEquals(CircuitState.OPEN, breaker.currentState())
        val requestsBefore = transport.requests.size

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderErrorKind.CIRCUIT_OPEN, failure.kind)
        assertEquals("an open circuit must not produce traffic", requestsBefore, transport.requests.size)
    }

    @Test
    fun `an open circuit recovers through a half open probe`() = runBlocking {
        var now = 0L
        val health = ProviderHealthTracker()
        val breaker = ProviderCircuitBreaker(
            failureThreshold = 2,
            openDurationMillis = 1_000L,
            clock = { now },
        )
        val transport = FakeHttpTransport().apply { failure = IOException("network down") }
        val adapter = buildAdapter(transport = transport, health = health, breaker = breaker)

        repeat(2) {
            assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }
        }
        assertEquals(CircuitState.OPEN, breaker.currentState())

        // Cooldown elapses -> only a single probe is admitted.
        now = 1_500L
        assertEquals(CircuitState.HALF_OPEN, breaker.currentState())
        assertTrue(breaker.allowRequest())
        assertFalse("only one probe may be in flight", breaker.allowRequest())

        transport.failure = null
        transport.response = successResponse()
        breaker.recordSuccess()

        assertEquals(CircuitState.CLOSED, breaker.currentState())

        val response = adapter.chat(chatRequest())
        assertEquals("Hello from the fixture.", response.text)
        assertEquals(ProviderHealthState.AVAILABLE, health.snapshot.value.state)
    }

    @Test
    fun `authentication failure alone never permanently disables the provider`() = runBlocking {
        val health = ProviderHealthTracker()
        val breaker = ProviderCircuitBreaker(failureThreshold = 1)
        val transport = FakeHttpTransport().apply {
            response = jsonResponse("""{"error":{"message":"invalid key"}}""", status = 401)
        }
        val adapter = buildAdapter(transport = transport, health = health, breaker = breaker)

        assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

        assertEquals(ProviderHealthState.AUTH_ERROR, health.snapshot.value.state)
        assertEquals(CircuitState.CLOSED, breaker.currentState())
    }

    @Test
    fun `rate limit headers are reported only when the provider sends them`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = successResponse(
                headers = mapOf(
                    "x-ratelimit-limit-requests" to "100",
                    "x-ratelimit-remaining-requests" to "0",
                    "x-ratelimit-reset-requests" to "30",
                ),
            )
        }
        val health = ProviderHealthTracker()
        val adapter = buildAdapter(transport = transport, health = health)

        adapter.chat(chatRequest())

        val rateLimit = health.snapshot.value.rateLimit
        assertEquals(ProviderValue.Known(100), rateLimit.limitRequests)
        assertEquals("an explicit zero must stay zero", ProviderValue.Known(0), rateLimit.remainingRequests)
        // `x-ratelimit-reset-requests: 30` is a relative duration, so the parser anchors it to the
        // moment the response was observed (it must not invent an absolute instant).
        val observedAt = requireNotNull(rateLimit.observedAtMillis) { "observation time must be recorded" }
        assertEquals(ProviderValue.Known(observedAt + 30_000L), rateLimit.resetAtMillis)
    }

    @Test
    fun `absent rate limit headers stay unknown and are never fabricated`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = successResponse() }
        val health = ProviderHealthTracker()
        val adapter = buildAdapter(transport = transport, health = health)

        adapter.chat(chatRequest())

        val rateLimit = health.snapshot.value.rateLimit
        assertTrue(rateLimit.limitRequests.isUnknown)
        assertTrue(rateLimit.remainingRequests.isUnknown)
        assertTrue("nothing may be invented about quota", health.snapshot.value.quota.remaining.isUnknown)
        assertFalse(health.snapshot.value.quota.exhausted)
    }

    // -------------------------------------------------------------------------------------
    // F. Streaming
    // -------------------------------------------------------------------------------------

    @Test
    fun `streaming emits incremental content and terminates exactly once`() = runBlocking {
        val handle = FakeStreamHandle(lines = streamLines())
        val transport = FakeHttpTransport().apply { streamHandle = handle }
        val health = ProviderHealthTracker()
        val adapter = buildAdapter(transport = transport, health = health)

        val events = adapter.stream(chatRequest()).toList()

        val content = events.filterIsInstance<ProviderStreamEvent.Content>().map { it.text }
        assertEquals(listOf("Hel", "lo", "!"), content)
        assertEquals(listOf("thinking"), events.filterIsInstance<ProviderStreamEvent.Reasoning>().map { it.text })

        val completed = events.filterIsInstance<ProviderStreamEvent.Completed>()
        assertEquals("exactly one completion event", 1, completed.size)
        assertEquals("stop", completed.single().finishReason)
        assertEquals(9, completed.single().usage?.totalTokens)
        assertEquals(0, events.filterIsInstance<ProviderStreamEvent.Failed>().size)

        // No duplicate final text: the completion event carries no text, so the UI cannot append
        // the answer twice.
        assertEquals("Hello!", content.joinToString(""))

        assertTrue("the stream handle is always closed", handle.closed)
        assertEquals(1, transport.requests.size)
        assertEquals(ProviderHealthState.AVAILABLE, health.snapshot.value.state)

        val sent = transport.lastRequest
        assertEquals("text/event-stream", sent.headers["Accept"])
        assertEquals(true, requestAdapter.fromJson(sent.body.orEmpty())?.stream)
    }

    @Test
    fun `streaming with no events still terminates cleanly`() = runBlocking {
        val transport = FakeHttpTransport().apply { streamHandle = FakeStreamHandle(lines = emptyList()) }
        val adapter = buildAdapter(transport = transport)

        val events = adapter.stream(chatRequest()).toList()

        assertEquals(1, events.count { it is ProviderStreamEvent.Completed })
        assertEquals(0, events.count { it is ProviderStreamEvent.Failed })
    }

    @Test
    fun `stream error payload is normalized`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            streamHandle = FakeStreamHandle(
                lines = listOf("""data: {"error":{"message":"insufficient_quota","type":"insufficient_quota"}}"""),
            )
        }
        val adapter = buildAdapter(transport = transport)

        val events = adapter.stream(chatRequest()).toList()

        val failure = events.filterIsInstance<ProviderStreamEvent.Failed>().single()
        assertEquals(ProviderErrorKind.QUOTA_EXHAUSTED, failure.error.kind)
        assertEquals(0, events.count { it is ProviderStreamEvent.Completed })
    }

    @Test
    fun `unreadable stream is reported as malformed`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            streamHandle = FakeStreamHandle(lines = listOf("data: {not json", "data: {still not json"))
        }
        val adapter = buildAdapter(transport = transport)

        val events = adapter.stream(chatRequest()).toList()

        val failure = events.filterIsInstance<ProviderStreamEvent.Failed>().single()
        assertEquals(ProviderErrorKind.MALFORMED_RESPONSE, failure.error.kind)
    }

    @Test
    fun `non success stream response is normalized with its error body`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            streamHandle = FakeStreamHandle(
                statusCode = 429,
                errorBody = """{"error":{"message":"Rate limit reached","type":"rate_limit_exceeded"}}""",
            )
        }
        val health = ProviderHealthTracker()
        val adapter = buildAdapter(transport = transport, health = health)

        val events = adapter.stream(chatRequest()).toList()

        val failure = events.filterIsInstance<ProviderStreamEvent.Failed>().single()
        assertEquals(ProviderErrorKind.RATE_LIMITED, failure.error.kind)
        assertEquals(ProviderHealthState.RATE_LIMITED, health.snapshot.value.state)
    }

    @Test
    fun `streaming without a credential emits a normalized failure and makes no request`() = runBlocking {
        val transport = FakeHttpTransport()
        val adapter = buildAdapter(transport = transport, credentials = FakeCredentialSource(stored = null))

        val events = adapter.stream(chatRequest()).toList()

        val failure = events.filterIsInstance<ProviderStreamEvent.Failed>().single()
        assertEquals(ProviderErrorKind.NOT_CONFIGURED, failure.error.kind)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `cancelling a stream aborts the call and is never reported as a provider failure`() = runBlocking {
        val readStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val handle = BlockingStreamHandle(readStarted, release)
        val transport = FakeHttpTransport().apply { streamHandle = handle }
        val health = ProviderHealthTracker()
        val breaker = ProviderCircuitBreaker()
        val adapter = buildAdapter(transport = transport, health = health, breaker = breaker)

        val events = mutableListOf<ProviderStreamEvent>()
        val job = launch(Dispatchers.IO) {
            adapter.stream(chatRequest()).collect { events += it }
        }

        assertTrue("the stream read should have started", readStarted.await(10, TimeUnit.SECONDS))
        val healthBefore = health.snapshot.value.state
        job.cancelAndJoin()

        assertTrue("cancellation must abort the in-flight call", handle.cancelled)
        assertEquals(
            "cancellation must not be recorded as a provider fault",
            healthBefore,
            health.snapshot.value.state,
        )
        assertFalse(
            "cancellation must not be recorded as quota exhaustion",
            health.snapshot.value.quota.exhausted,
        )
        assertEquals(CircuitState.CLOSED, breaker.currentState())
        assertTrue(events.none { it is ProviderStreamEvent.Failed })
    }

    // -------------------------------------------------------------------------------------
    // G. Models + ephemeral credential validation
    // -------------------------------------------------------------------------------------

    @Test
    fun `model list is normalized`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse(
                """{"object":"list","data":[{"id":"$TEST_MODEL","owned_by":"hcnsec"},{"id":"second-model"}]}""",
            )
        }
        val adapter = buildAdapter(transport = transport)

        val models = adapter.listModels()

        assertEquals(listOf(TEST_MODEL, "second-model"), models.map { it.id })
        assertEquals("GET", transport.lastRequest.method)
        assertEquals("https://api.hcnsec.cn/v1/models", transport.lastRequest.url)
        assertEquals("Bearer $TEST_CREDENTIAL", transport.lastRequest.headers["Authorization"])
    }

    @Test
    fun `unreadable model list is a normalized failure`() = runBlocking {
        val transport = FakeHttpTransport().apply { response = jsonResponse("definitely not json") }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.listModels() } }

        assertEquals(ProviderErrorKind.MALFORMED_RESPONSE, failure.kind)
    }

    @Test
    fun `model list authentication failure is normalized`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse("""{"error":{"message":"invalid api key"}}""", status = 401)
        }
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.listModels() } }

        assertEquals(ProviderErrorKind.AUTHENTICATION, failure.kind)
    }

    @Test
    fun `ephemeral credential validation uses the supplied value and never the stored one`() = runBlocking {
        val transport = FakeHttpTransport().apply {
            response = jsonResponse("""{"object":"list","data":[{"id":"$TEST_MODEL"}]}""")
        }
        val credentials = FakeCredentialSource()
        val adapter = buildAdapter(transport = transport, credentials = credentials)
        val ephemeralValue = "onboarding-candidate-credential-not-stored"

        val models = adapter.validateEphemeralCredential(
            com.example.data.security.ProviderCredential(ephemeralValue),
        )

        assertEquals(listOf(TEST_MODEL), models.map { it.id })
        assertEquals("Bearer $ephemeralValue", transport.lastRequest.headers["Authorization"])
        assertEquals("the stored credential must not be read", 0, credentials.reads)
        assertFalse(transport.lastRequest.url.contains(ephemeralValue))
    }

    @Test
    fun `blank ephemeral credential is rejected without a request`() = runBlocking {
        val transport = FakeHttpTransport()
        val adapter = buildAdapter(transport = transport)

        val failure = assertThrows(ProviderException::class.java) {
            runBlocking {
                adapter.validateEphemeralCredential(com.example.data.security.ProviderCredential("   "))
            }
        }

        assertEquals(ProviderErrorKind.NOT_CONFIGURED, failure.kind)
        assertTrue(transport.requests.isEmpty())
    }

    // -------------------------------------------------------------------------------------
    // H. Secret leakage
    // -------------------------------------------------------------------------------------

    @Test
    fun `no failure path exposes the credential`() = runBlocking {
        val scenarios: List<Pair<ProviderHttpResponse?, IOException?>> = listOf(
            jsonResponse("""{"error":{"message":"invalid api key"}}""", status = 401) to null,
            jsonResponse("""{"error":{"message":"insufficient_quota"}}""", status = 429) to null,
            jsonResponse("""{"error":{"message":"boom"}}""", status = 500) to null,
            jsonResponse("not json") to null,
            null to IOException("connection reset by peer at Bearer $TEST_CREDENTIAL"),
            null to SocketTimeoutException("read timed out"),
        )

        for ((cannedResponse, cannedFailure) in scenarios) {
            val health = ProviderHealthTracker()
            val transport = FakeHttpTransport().apply {
                response = cannedResponse
                failure = cannedFailure
            }
            val adapter = buildAdapter(transport = transport, health = health)

            val failure = assertThrows(ProviderException::class.java) { runBlocking { adapter.chat(chatRequest()) } }

            assertNoSecret(failure.error.message, "error message")
            assertNoSecret(failure.error.diagnostic, "error diagnostic")
            assertNoSecret(failure.toString(), "exception toString")
            assertNoSecret(health.snapshot.value.lastErrorMessage, "health lastErrorMessage")
        }
    }

    @Test
    fun `provider credential toString is redacted`() {
        val credential = com.example.data.security.ProviderCredential(TEST_CREDENTIAL)

        assertFalse(credential.toString().contains(TEST_CREDENTIAL))
        assertTrue(credential.toString().contains("redacted"))
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    private fun buildAdapter(
        transport: ProviderHttpTransport = FakeHttpTransport(),
        credentials: ProviderCredentialSource = FakeCredentialSource(),
        health: ProviderHealthTracker = ProviderHealthTracker(),
        breaker: ProviderCircuitBreaker = ProviderCircuitBreaker(),
        config: HcnsecProviderConfig = HcnsecProviderConfig(defaultModel = TEST_MODEL),
    ): HcnsecProviderAdapter = HcnsecProviderAdapter(
        credentials = credentials,
        transport = transport,
        health = health,
        circuitBreaker = breaker,
        config = config,
    )

    private fun chatRequest(): ProviderChatRequest = ProviderChatRequest(
        model = TEST_MODEL,
        messages = listOf(ProviderMessage(ProviderRole.USER, "hello")),
    )

    private fun jsonResponse(body: String, status: Int = 200, headers: Map<String, String> = emptyMap()) =
        ProviderHttpResponse(statusCode = status, headers = headers, body = body, latencyMillis = 12L)

    private fun successResponse(headers: Map<String, String> = emptyMap()) = jsonResponse(
        body = """
            {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"$TEST_MODEL",
             "choices":[{"index":0,"finish_reason":"stop",
             "message":{"role":"assistant","content":"Hello from the fixture."}}],
             "usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}
        """.trimIndent(),
        headers = headers,
    )

    private fun streamLines(): List<String> = listOf(
        "",
        ": keepalive",
        """data: {"choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"}}]}""",
        """data: {"choices":[{"index":0,"delta":{"content":"lo"}}]}""",
        """data: {"choices":[{"index":0,"delta":{"reasoning_content":"thinking"}}]}""",
        """data: {"choices":[{"index":0,"delta":{"content":"!"},"finish_reason":"stop"}]}""",
        """data: {"choices":[],"usage":{"prompt_tokens":2,"completion_tokens":3,"total_tokens":9}}""",
        "data: [DONE]",
    )

    private fun assertNoSecret(value: String?, label: String) {
        if (value == null) return
        assertFalse("$label must not contain the credential", value.contains(TEST_CREDENTIAL))
        assertFalse("$label must not contain a bearer token", value.contains("Bearer $TEST_CREDENTIAL"))
    }
}
