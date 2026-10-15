// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.assertNull

class AiFailureKindTest {
    @Test
    fun `401 is an invalid key`() {
        assertEquals(AiFailureKind.INVALID_API_KEY, AiError.Http(401, "").kind)
    }

    @Test
    fun `Gemini reports an invalid key as a 400`() {
        val body =
            """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.",""" +
                """"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""
        assertEquals(AiFailureKind.INVALID_API_KEY, AiError.Http(400, body).kind)
    }

    @Test
    fun `403 is a permission problem`() {
        assertEquals(AiFailureKind.PERMISSION_DENIED, AiError.Http(403, "").kind)
    }

    @Test
    fun `OpenAI insufficient_quota 429 is out of credit, not a rate limit`() {
        val body = """{"error":{"message":"You exceeded your current quota","type":"insufficient_quota","code":"insufficient_quota"}}"""
        assertEquals(AiFailureKind.OUT_OF_CREDIT, AiError.RateLimited(null, body).kind)
    }

    @Test
    fun `Anthropic low credit balance 400 is out of credit`() {
        val body =
            """{"type":"error","error":{"type":"invalid_request_error",""" +
                """"message":"Your credit balance is too low to access the Anthropic API."}}"""
        assertEquals(AiFailureKind.OUT_OF_CREDIT, AiError.Http(400, body).kind)
    }

    @Test
    fun `402 is out of credit`() {
        assertEquals(AiFailureKind.OUT_OF_CREDIT, AiError.Http(402, "").kind)
    }

    @Test
    fun `a plain 429 is a rate limit`() {
        assertEquals(AiFailureKind.RATE_LIMITED, AiError.RateLimited(5).kind)
    }

    @Test
    fun `404 is a missing model`() {
        assertEquals(AiFailureKind.MODEL_NOT_FOUND, AiError.Http(404, "").kind)
    }

    @Test
    fun `context length errors are input too long`() {
        val openAi = """{"error":{"message":"This model's maximum context length is 128000 tokens.","code":"context_length_exceeded"}}"""
        val anthropic =
            """{"type":"error","error":{"type":"invalid_request_error","message":"prompt is too long: 210000 tokens > 200000 maximum"}}"""
        assertEquals(AiFailureKind.INPUT_TOO_LONG, AiError.Http(400, openAi).kind)
        assertEquals(AiFailureKind.INPUT_TOO_LONG, AiError.Http(400, anthropic).kind)
        assertEquals(AiFailureKind.INPUT_TOO_LONG, AiError.Http(413, "").kind)
    }

    @Test
    fun `other 400s are bad requests`() {
        assertEquals(AiFailureKind.BAD_REQUEST, AiError.Http(400, """{"error":{"message":"Invalid value for temperature"}}""").kind)
    }

    @Test
    fun `503 and Anthropic 529 are overload, other 5xx a server error`() {
        assertEquals(AiFailureKind.OVERLOADED, AiError.Http(503, "").kind)
        assertEquals(AiFailureKind.OVERLOADED, AiError.Http(529, "").kind)
        assertEquals(AiFailureKind.SERVER_ERROR, AiError.Http(500, "").kind)
    }

    @Test
    fun `network failures are split by cause`() {
        assertEquals(AiFailureKind.NO_CONNECTION, AiError.Network(UnknownHostException("api.openai.com")).kind)
        assertEquals(AiFailureKind.TIMEOUT, AiError.Network(SocketTimeoutException("timeout")).kind)
        assertEquals(AiFailureKind.TIMEOUT, AiError.Http(408, "").kind)
        assertEquals(AiFailureKind.NETWORK, AiError.Network(IOException("stream was reset")).kind)
    }

    @Test
    fun `a wrapped cause is still recognised`() {
        val wrapped = IOException("outer", UnknownHostException("api.anthropic.com"))
        assertEquals(AiFailureKind.NO_CONNECTION, AiError.Network(wrapped).kind)
    }

    @Test
    fun `missing key, empty reply and unknown codes`() {
        assertEquals(AiFailureKind.MISSING_API_KEY, AiError.MissingApiKey.kind)
        assertEquals(AiFailureKind.EMPTY_RESPONSE, AiError.EmptyResponse.kind)
        assertEquals(AiFailureKind.UNKNOWN, AiError.Http(418, "").kind)
    }

    @Test
    fun `providerMessage reads error_message from every vendor's shape`() {
        assertEquals("Bad key", AiError.Http(401, """{"error":{"message":"Bad key"}}""").providerMessage)
        assertEquals(
            "Overloaded",
            AiError.Http(529, """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""").providerMessage,
        )
        // Gemini's streaming endpoint wraps the error object in an array
        assertEquals("Quota exceeded", AiError.RateLimited(null, """[{"error":{"code":429,"message":"Quota exceeded"}}]""").providerMessage)
    }

    @Test
    fun `providerMessage is null for a non-JSON body`() {
        assertNull(AiError.Http(502, "<html>Bad Gateway</html>").providerMessage)
        assertNull(AiError.Http(500, "").providerMessage)
        assertNull(AiError.RateLimited(3).providerMessage)
    }
}
