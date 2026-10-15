// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

sealed class AiError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(
        cause: Throwable,
    ) : AiError("Network error: ${cause.message}", cause)

    class Http(
        val code: Int,
        val body: String,
    ) : AiError("HTTP $code: $body")

    /**
     * HTTP 429, split out from [Http] so callers can offer a wait-and-retry message.
     *
     * @param body the response body, which tells an exhausted quota apart from a short-term limit
     */
    class RateLimited(
        val retryAfterSeconds: Int?,
        val body: String? = null,
    ) : AiError("Rate limited")

    object MissingApiKey : AiError("No API key configured")

    /** The stream finished without a single token, e.g. because a safety filter blocked the reply. */
    object EmptyResponse : AiError("Empty response")
}

/** What went wrong, from the user's point of view: each kind calls for different advice. */
enum class AiFailureKind {
    MISSING_API_KEY,
    INVALID_API_KEY,
    PERMISSION_DENIED,
    OUT_OF_CREDIT,
    RATE_LIMITED,
    MODEL_NOT_FOUND,
    INPUT_TOO_LONG,
    BAD_REQUEST,
    OVERLOADED,
    SERVER_ERROR,
    TIMEOUT,
    NO_CONNECTION,
    NETWORK,
    EMPTY_RESPONSE,
    UNKNOWN,
}

val AiError.kind: AiFailureKind
    get() =
        when (this) {
            AiError.MissingApiKey -> AiFailureKind.MISSING_API_KEY
            AiError.EmptyResponse -> AiFailureKind.EMPTY_RESPONSE
            is AiError.Network -> networkKind(cause)
            is AiError.RateLimited ->
                if (body.orEmpty().contains("insufficient_quota")) AiFailureKind.OUT_OF_CREDIT else AiFailureKind.RATE_LIMITED
            is AiError.Http -> httpKind(code, body.lowercase())
        }

/**
 * The vendor's own explanation, from the `error.message` field that OpenAI, Anthropic and Gemini
 * all use; `null` if the body is not such JSON.
 */
val AiError.providerMessage: String?
    get() {
        val body =
            when (this) {
                is AiError.Http -> body
                is AiError.RateLimited -> body
                else -> null
            }?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val root =
                when (val parsed = JSONTokener(body).nextValue()) {
                    // Gemini's streaming endpoint wraps the error object in an array
                    is JSONArray -> parsed.optJSONObject(0)
                    is JSONObject -> parsed
                    else -> null
                }
            root
                ?.optJSONObject("error")
                ?.optString("message")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(MAX_PROVIDER_MESSAGE_LENGTH)
        } catch (e: JSONException) {
            null
        }
    }

private const val MAX_PROVIDER_MESSAGE_LENGTH = 500

private fun httpKind(
    code: Int,
    body: String,
): AiFailureKind =
    when {
        code == 401 -> AiFailureKind.INVALID_API_KEY
        code == 402 -> AiFailureKind.OUT_OF_CREDIT
        code == 403 -> AiFailureKind.PERMISSION_DENIED
        code == 404 -> AiFailureKind.MODEL_NOT_FOUND
        code == 408 -> AiFailureKind.TIMEOUT
        code == 413 -> AiFailureKind.INPUT_TOO_LONG
        // Gemini answers a bad key with a 400 rather than a 401
        "api_key_invalid" in body || "api key not valid" in body -> AiFailureKind.INVALID_API_KEY
        "credit balance" in body || "insufficient_quota" in body -> AiFailureKind.OUT_OF_CREDIT
        CONTEXT_TOO_LONG_MARKERS.any { it in body } -> AiFailureKind.INPUT_TOO_LONG
        code == 400 || code == 422 -> AiFailureKind.BAD_REQUEST
        // 529 is Anthropic's "overloaded"
        code == 503 || code == 529 -> AiFailureKind.OVERLOADED
        code in 500..599 -> AiFailureKind.SERVER_ERROR
        else -> AiFailureKind.UNKNOWN
    }

private val CONTEXT_TOO_LONG_MARKERS =
    listOf("context_length_exceeded", "maximum context length", "prompt is too long", "exceeds the maximum number of tokens")

private fun networkKind(cause: Throwable?): AiFailureKind {
    for (throwable in generateSequence(cause) { it.cause }) {
        when (throwable) {
            is UnknownHostException, is ConnectException, is NoRouteToHostException -> return AiFailureKind.NO_CONNECTION
            // SocketTimeoutException is an InterruptedIOException, as is OkHttp's call timeout
            is InterruptedIOException -> return AiFailureKind.TIMEOUT
        }
    }
    return AiFailureKind.NETWORK
}
