package com.example.data.network

import okhttp3.Call
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OkHttp implementation of [ProviderHttpTransport].
 *
 * Security properties (Phase 4.2 §16/§17):
 * - HTTPS only, enforced before the request leaves the process;
 * - the platform default TLS trust chain and hostname verification are kept: no custom socket
 *   factory, no verifier override, no certificate bypass;
 * - there is deliberately **no** logging interceptor, so credentials can never reach logcat;
 * - provider calls do not follow redirects.
 */
class OkHttpProviderTransport(
    connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    writeTimeoutMillis: Long = DEFAULT_WRITE_TIMEOUT_MILLIS,
) : ProviderHttpTransport {

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS: Long = 15_000L
        const val DEFAULT_READ_TIMEOUT_MILLIS: Long = 120_000L
        const val DEFAULT_WRITE_TIMEOUT_MILLIS: Long = 30_000L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(writeTimeoutMillis, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    override fun execute(request: ProviderHttpRequest): ProviderHttpResponse {
        val call = client.newCall(buildRequest(request))
        applyTimeout(call, request)
        val startedAt = System.nanoTime()

        call.execute().use { response ->
            val body = response.body?.string().orEmpty()
            return ProviderHttpResponse(
                statusCode = response.code,
                headers = flattenHeaders(response.headers),
                body = body,
                latencyMillis = elapsedMillis(startedAt),
            )
        }
    }

    override fun openStream(request: ProviderHttpRequest): ProviderStreamHandle {
        val call = client.newCall(buildRequest(request))
        applyTimeout(call, request)

        val response: Response = call.execute()
        val headerMap = flattenHeaders(response.headers)

        if (response.code !in 200..299) {
            val errorBody = try {
                response.body?.string().orEmpty()
            } catch (_: IOException) {
                ""
            } finally {
                response.close()
            }
            return ClosedStreamHandle(statusCode = response.code, headers = headerMap, errorBody = errorBody)
        }

        val source = response.body?.byteStream()
        if (source == null) {
            response.close()
            return ClosedStreamHandle(statusCode = response.code, headers = headerMap, errorBody = "")
        }

        return OkHttpLineStream(
            statusCode = response.code,
            headers = headerMap,
            call = call,
            response = response,
            reader = source.bufferedReader(Charsets.UTF_8),
        )
    }

    private fun buildRequest(request: ProviderHttpRequest): Request {
        val url = requireHttps(request.url)
        val builder = Request.Builder().url(url)

        for ((name, value) in request.headers) {
            builder.header(name, value)
        }

        val method = request.method.uppercase()
        val body = request.body
        if (body != null) {
            builder.method(method, body.toRequestBody(JSON_MEDIA_TYPE))
        } else {
            builder.method(method, null)
        }

        return builder.build()
    }

    private fun applyTimeout(call: Call, request: ProviderHttpRequest) {
        val timeout = request.timeoutMillis ?: return
        if (timeout > 0) {
            call.timeout().timeout(timeout, TimeUnit.MILLISECONDS)
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long = (System.nanoTime() - startedAtNanos) / 1_000_000L

    private class OkHttpLineStream(
        override val statusCode: Int,
        override val headers: Map<String, String>,
        private val call: Call,
        private val response: Response,
        private val reader: BufferedReader,
    ) : ProviderStreamHandle {

        override fun readLine(): String? = reader.readLine()

        override fun readErrorBody(): String = ""

        override fun close() {
            runCatching { reader.close() }
            runCatching { response.close() }
        }

        override fun cancel() {
            // Unblocks a read that is currently in progress on another thread.
            runCatching { call.cancel() }
            close()
        }
    }

    private class ClosedStreamHandle(
        override val statusCode: Int,
        override val headers: Map<String, String>,
        private val errorBody: String,
    ) : ProviderStreamHandle {

        override fun readLine(): String? = null

        override fun readErrorBody(): String = errorBody

        override fun close() = Unit

        override fun cancel() = Unit
    }
}

/** Converts OkHttp headers into a flat map, keeping the first value of repeated headers. */
internal fun flattenHeaders(headers: Headers): Map<String, String> {
    if (headers.size == 0) return emptyMap()
    val result = LinkedHashMap<String, String>(headers.size)
    for (index in 0 until headers.size) {
        val headerName = headers.name(index)
        if (!result.containsKey(headerName)) {
            result[headerName] = headers.value(index)
        }
    }
    return result
}
