# AI 카드 채팅 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reviewer(새 스터디 화면)에서 사용자가 현재 카드를 근거로 LLM(OpenAI/Anthropic/Gemini)과 자유롭게 채팅할 수 있는 기능을 추가한다.

**Architecture:** `com.ichi2.anki.ai` 패키지에 프로바이더 독립적인 채팅 도메인(메시지 모델, `AiProvider` 어댑터 인터페이스, OkHttp+okhttp-sse 기반 스트리밍 클라이언트, 암호화 키 저장소, 카드 콘텐츠 추출기)을 만든다. UI는 기존 `ViewerAction`/`ReviewerViewModel` 확장 포인트에 `AI_CHAT` 액션을 추가해 `BottomSheetDialogFragment`를 띄우고, 대화 기록은 `MetaDB`에 노트(`nid`) 단위로 저장한다. 설정은 기존 fork 전용 설정 화면(`preferences_deurim.xml`/`DeurimSettingsFragment`)에 새 "AI" 카테고리를 추가하는 방식으로, 새 최상위 설정 화면은 만들지 않는다.

**Tech Stack:** Kotlin, OkHttp 5.4.0 + `okhttp-sse`(스트리밍), `androidx.security-crypto`(API 키 암호화 저장), `org.json`(요청/응답 JSON), 기존 `MetaDB`(`SQLiteOpenHelper` 수동 마이그레이션 패턴), JUnit + Robolectric + MockWebServer + Turbine(테스트).

**Spec:** `docs/superpowers/specs/2026-08-21-ai-card-chat-design.md`

## Global Constraints

- v1 범위: 이미지/오디오 전송 안 함(텍스트만), 자동 카드 설명 없음(버튼 클릭 시 빈 채팅으로 진입), 프로바이더 1개 + 키 1개만 지원, 별도 프라이버시 고지 다이얼로그 없음(설정에서 키 입력 자체가 동의), 재시도/백오프 로직 없음.
- **범위 축소(스펙 대비 단순화):** 스펙의 "모델 선택 UI"는 v1에서 생략하고 프로바이더별 고정 기본 모델을 사용한다(YAGNI — 필요해지면 후속 스펙에서 다룬다). 사용자에게 이 결정을 알리고 진행한다.
- API 키는 `androidx.security-crypto`의 `EncryptedSharedPreferences`에만 저장한다. 절대 일반 `SharedPreferences`/`Prefs`에 평문으로 저장하지 않는다.
- Cloze 카드는 `Card.question(col)`/`Card.answer(col)`(이미 렌더링된, 화면에 보이는 것과 동일한 HTML)을 그대로 사용한다 — 별도로 cloze 정규식을 직접 파싱하지 않는다.
- 새 로직 코드는 전부 `com.ichi2.anki.ai` 패키지 아래에 둔다(UI는 `com.ichi2.anki.ai.chat` 하위 패키지).
- 버그 수정이 아닌 신규 기능이므로 TDD는 "동작이 있는 코드"에만 적용한다(순수 데이터 클래스/인터페이스 선언 자체에는 실패 테스트를 요구하지 않는다).

---

### Task 1: 의존성 추가 (okhttp-sse, androidx-security-crypto, mockwebserver3)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `AnkiDroid/build.gradle`

**Interfaces:**
- Produces: `libs.okhttp.sse`, `libs.androidx.security.crypto`, `libs.okhttp.mockwebserver` (Gradle version-catalog accessors) — 이후 모든 태스크가 이 의존성을 사용한다.

- [ ] **Step 1: `gradle/libs.versions.toml`에 버전과 라이브러리 좌표 추가**

`[versions]` 섹션에서 `okhttp = "5.4.0"` 줄 바로 아래에 추가:

```toml
androidxSecurityCrypto = "1.1.0"
```

`[libraries]` 섹션에서 `okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }` 줄 바로 아래에 추가:

```toml
okhttp-sse = { module = "com.squareup.okhttp3:okhttp-sse", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver3", version.ref = "okhttp" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "androidxSecurityCrypto" }
```

- [ ] **Step 2: `AnkiDroid/build.gradle`에 의존성 연결**

`implementation libs.okhttp` 줄(약 591번째 줄) 바로 아래에 추가:

```groovy
    implementation libs.okhttp.sse
    implementation libs.androidx.security.crypto
```

`testImplementation libs.cashapp.turbine` 줄(약 643번째 줄) 바로 아래에 추가:

```groovy
    testImplementation libs.okhttp.mockwebserver
```

- [ ] **Step 3: 동기화 확인**

Run: `./gradlew :AnkiDroid:help --dry-run`
Expected: Gradle 설정 단계가 의존성 해석 오류 없이 통과한다(빌드 자체는 하지 않아도 됨, `libs.okhttp.sse` 등 새 accessor가 인식되는지 확인하는 목적).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml AnkiDroid/build.gradle
git commit -m "build: add okhttp-sse, security-crypto and mockwebserver3 dependencies for AI chat"
```

---

### Task 2: `CardContentExtractor` (카드 HTML → LLM 전달용 텍스트)

**Files:**
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/CardContentExtractor.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/ai/CardContentExtractorTest.kt`

**Interfaces:**
- Produces: `object CardContentExtractor { fun extract(questionHtml: String, answerHtml: String): String }` — Task 10에서 `Card.question(col)`/`Card.answer(col)` 결과를 이 함수에 그대로 넘긴다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/ai/CardContentExtractorTest.kt
package com.ichi2.anki.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class CardContentExtractorTest {
    @Test
    fun `strips html tags from question and answer`() {
        val question = "<div style=\"text-align:center\">What is <b>2+2</b>?</div>"
        val answer = "<div>What is <b>2+2</b>?</div><hr id=answer><div>4</div>"

        val result = CardContentExtractor.extract(question, answer)

        assertEquals(
            "Front: What is 2+2?\nBack: What is 2+2?4",
            result,
        )
    }

    @Test
    fun `strips sound and image special fields`() {
        val question = "Listen: [sound:audio.mp3]"
        val answer = "<img src=\"pic.jpg\">A picture"

        val result = CardContentExtractor.extract(question, answer)

        assertEquals("Front: Listen:\nBack: A picture", result)
    }

    @Test
    fun `collapses repeated whitespace left by stripped tags`() {
        val question = "<div>  Multiple   spaces  </div>"
        val answer = "<div>Answer</div>"

        val result = CardContentExtractor.extract(question, answer)

        assertEquals("Front: Multiple spaces\nBack: Answer", result)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.CardContentExtractorTest"`
Expected: FAIL — `CardContentExtractor`가 존재하지 않아 컴파일 에러.

- [ ] **Step 3: 최소 구현 작성**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/CardContentExtractor.kt
package com.ichi2.anki.ai

import com.ichi2.anki.backend.stripHTMLAndSpecialFields

/**
 * Converts a card's rendered HTML (question/answer) into plain text suitable for
 * sending to an LLM. Images and audio are omitted entirely rather than described,
 * per the design decision to keep v1 text-only.
 */
object CardContentExtractor {
    fun extract(
        questionHtml: String,
        answerHtml: String,
    ): String {
        val question = clean(questionHtml)
        val answer = clean(answerHtml)
        return "Front: $question\nBack: $answer"
    }

    private fun clean(html: String): String =
        stripHTMLAndSpecialFields(html)
            .replace(Regex("\\s+"), " ")
            .trim()
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.CardContentExtractorTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add AnkiDroid/src/main/java/com/ichi2/anki/ai/CardContentExtractor.kt AnkiDroid/src/test/java/com/ichi2/anki/ai/CardContentExtractorTest.kt
git commit -m "feat(ai): add CardContentExtractor to convert card HTML to LLM-ready text"
```

---

### Task 3: AI 도메인 타입 + `AiStreamingClient` (SSE 스트리밍 오케스트레이션)

**Files:**
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/AiChatMessage.kt`
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/AiProvider.kt`
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/AiError.kt`
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/AiStreamingClient.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/ai/AiStreamingClientTest.kt`

**Interfaces:**
- Produces:
  - `enum class AiChatRole { USER, ASSISTANT }`
  - `data class AiChatMessage(val role: AiChatRole, val content: String)`
  - `sealed class AiSseEvent { data class Token(val text: String); object Done; object Ignored }`
  - `interface AiProvider { fun buildRequest(apiKey: String, model: String, systemPrompt: String?, messages: List<AiChatMessage>): okhttp3.Request; fun parseSseEvent(data: String): AiSseEvent }`
  - `sealed class AiError(message: String) : Exception(message) { class Network(cause: Throwable); class Http(val code: Int, body: String); object MissingApiKey }`
  - `class AiStreamingClient { fun stream(provider: AiProvider, apiKey: String, model: String, systemPrompt: String?, messages: List<AiChatMessage>): kotlinx.coroutines.flow.Flow<AiSseEvent> }`
- Consumes: nothing from earlier tasks.
- Tasks 4-6 (프로바이더 구현체)와 Task 11(`AiChatViewModel`)이 이 파일들의 타입을 그대로 사용한다.

- [ ] **Step 1: 도메인 타입 파일 작성 (테스트 없음 — 순수 선언)**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/AiChatMessage.kt
package com.ichi2.anki.ai

enum class AiChatRole {
    USER,
    ASSISTANT,
    ;

    companion object {
        fun fromStorageValue(value: String): AiChatRole = valueOf(value)
    }

    val storageValue: String get() = name
}

data class AiChatMessage(
    val role: AiChatRole,
    val content: String,
)
```

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/AiError.kt
package com.ichi2.anki.ai

sealed class AiError(
    message: String,
) : Exception(message) {
    class Network(
        cause: Throwable,
    ) : AiError("Network error: ${cause.message}")

    class Http(
        val code: Int,
        body: String,
    ) : AiError("HTTP $code: $body")

    object MissingApiKey : AiError("No API key configured")
}
```

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/AiProvider.kt
package com.ichi2.anki.ai

import okhttp3.Request

sealed class AiSseEvent {
    data class Token(
        val text: String,
    ) : AiSseEvent()

    object Done : AiSseEvent()

    object Ignored : AiSseEvent()
}

/** Vendor-specific adapter: builds the HTTP request and parses each SSE `data:` payload. */
interface AiProvider {
    val defaultModel: String

    fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request

    fun parseSseEvent(data: String): AiSseEvent
}
```

- [ ] **Step 2: `AiStreamingClient`를 사용하는 실패 테스트 작성 (MockWebServer + FakeProvider)**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/ai/AiStreamingClientTest.kt
package com.ichi2.anki.ai

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeProvider(
    private val url: String,
) : AiProvider {
    override val defaultModel = "fake-model"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request = Request.Builder().url(url).get().build()

    override fun parseSseEvent(data: String): AiSseEvent =
        if (data == "[DONE]") AiSseEvent.Done else AiSseEvent.Token(data)
}

class AiStreamingClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `emits a Token event per SSE data line then Done`() =
        runTest {
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body("data: hello\n\ndata: world\n\ndata: [DONE]\n\n")
                    .build(),
            )
            val provider = FakeProvider(server.url("/").toString())
            val client = AiStreamingClient()

            client.stream(provider, apiKey = "key", model = "fake-model", systemPrompt = null, messages = emptyList()).test {
                assertEquals(AiSseEvent.Token("hello"), awaitItem())
                assertEquals(AiSseEvent.Token("world"), awaitItem())
                assertEquals(AiSseEvent.Done, awaitItem())
                awaitComplete()
            }
        }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.AiStreamingClientTest"`
Expected: FAIL — `AiStreamingClient`가 존재하지 않아 컴파일 에러.

- [ ] **Step 4: `AiStreamingClient` 구현**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/AiStreamingClient.kt
package com.ichi2.anki.ai

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/** Streams tokens from an [AiProvider] over Server-Sent Events using OkHttp's `okhttp-sse`. */
class AiStreamingClient(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build(),
) {
    fun stream(
        provider: AiProvider,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Flow<AiSseEvent> =
        callbackFlow {
            val request = provider.buildRequest(apiKey, model, systemPrompt, messages)
            val factory = EventSources.createFactory(client)
            val listener =
                object : EventSourceListener() {
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        when (val event = provider.parseSseEvent(data)) {
                            AiSseEvent.Ignored -> {}
                            else -> trySend(event)
                        }
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        val error =
                            if (response != null && !response.isSuccessful) {
                                AiError.Http(response.code, response.body?.string().orEmpty())
                            } else {
                                AiError.Network(t ?: Exception("Unknown streaming failure"))
                            }
                        close(error)
                    }

                    override fun onClosed(eventSource: EventSource) {
                        trySend(AiSseEvent.Done)
                        close()
                    }
                }
            val eventSource = factory.newEventSource(request, listener)
            awaitClose { eventSource.cancel() }
        }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.AiStreamingClientTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add AnkiDroid/src/main/java/com/ichi2/anki/ai/AiChatMessage.kt AnkiDroid/src/main/java/com/ichi2/anki/ai/AiProvider.kt AnkiDroid/src/main/java/com/ichi2/anki/ai/AiError.kt AnkiDroid/src/main/java/com/ichi2/anki/ai/AiStreamingClient.kt AnkiDroid/src/test/java/com/ichi2/anki/ai/AiStreamingClientTest.kt
git commit -m "feat(ai): add AiProvider domain types and SSE-based AiStreamingClient"
```

---

### Task 4: `OpenAiProvider`

**Files:**
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/OpenAiProvider.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/ai/OpenAiProviderTest.kt`

**Interfaces:**
- Consumes: `AiProvider`, `AiChatMessage`, `AiChatRole`, `AiSseEvent` (Task 3).
- Produces: `class OpenAiProvider : AiProvider` — Task 8(설정)에서 프로바이더 선택 매핑에 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/ai/OpenAiProviderTest.kt
package com.ichi2.anki.ai

import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Buffer

class OpenAiProviderTest {
    private val provider = OpenAiProvider()

    private fun Request.bodyAsJson(): JSONObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `buildRequest targets the chat completions endpoint with bearer auth`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-test",
                model = "gpt-4o-mini",
                systemPrompt = "card context",
                messages = listOf(AiChatMessage(AiChatRole.USER, "explain this")),
            )

        assertEquals("https://api.openai.com/v1/chat/completions", request.url.toString())
        assertEquals("Bearer sk-test", request.header("Authorization"))
    }

    @Test
    fun `buildRequest includes system prompt and messages in order`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-test",
                model = "gpt-4o-mini",
                systemPrompt = "card context",
                messages = listOf(AiChatMessage(AiChatRole.USER, "explain this")),
            )

        val json = request.bodyAsJson()
        assertEquals("gpt-4o-mini", json.getString("model"))
        assertTrue(json.getBoolean("stream"))
        val messages = json.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("card context", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("explain this", messages.getJSONObject(1).getString("content"))
    }

    @Test
    fun `parseSseEvent extracts delta content as a Token`() {
        val data = """{"choices":[{"delta":{"content":"Hi"}}]}"""
        assertEquals(AiSseEvent.Token("Hi"), provider.parseSseEvent(data))
    }

    @Test
    fun `parseSseEvent maps DONE sentinel to Done`() {
        assertEquals(AiSseEvent.Done, provider.parseSseEvent("[DONE]"))
    }

    @Test
    fun `parseSseEvent ignores chunks without content delta`() {
        val data = """{"choices":[{"delta":{"role":"assistant"}}]}"""
        assertEquals(AiSseEvent.Ignored, provider.parseSseEvent(data))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.OpenAiProviderTest"`
Expected: FAIL — `OpenAiProvider`가 존재하지 않음.

- [ ] **Step 3: 구현 작성**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/OpenAiProvider.kt
package com.ichi2.anki.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class OpenAiProvider : AiProvider {
    override val defaultModel = "gpt-4o-mini"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request {
        val allMessages = mutableListOf<JSONObject>()
        if (systemPrompt != null) {
            allMessages += JSONObject().put("role", "system").put("content", systemPrompt)
        }
        messages.forEach { message ->
            allMessages +=
                JSONObject()
                    .put("role", if (message.role == AiChatRole.USER) "user" else "assistant")
                    .put("content", message.content)
        }

        val body =
            JSONObject()
                .put("model", model)
                .put("stream", true)
                .put("messages", JSONArray(allMessages))

        return Request
            .Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    override fun parseSseEvent(data: String): AiSseEvent {
        if (data == "[DONE]") return AiSseEvent.Done
        return try {
            val delta =
                JSONObject(data)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("delta")
            val content = delta.optString("content", "")
            if (content.isNotEmpty()) AiSseEvent.Token(content) else AiSseEvent.Ignored
        } catch (e: JSONException) {
            AiSseEvent.Ignored
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.OpenAiProviderTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add AnkiDroid/src/main/java/com/ichi2/anki/ai/OpenAiProvider.kt AnkiDroid/src/test/java/com/ichi2/anki/ai/OpenAiProviderTest.kt
git commit -m "feat(ai): add OpenAiProvider adapter"
```

---

### Task 5: `AnthropicProvider`

**Files:**
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/AnthropicProvider.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/ai/AnthropicProviderTest.kt`

**Interfaces:**
- Consumes: `AiProvider`, `AiChatMessage`, `AiChatRole`, `AiSseEvent` (Task 3).
- Produces: `class AnthropicProvider : AiProvider`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/ai/AnthropicProviderTest.kt
package com.ichi2.anki.ai

import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.Buffer

class AnthropicProviderTest {
    private val provider = AnthropicProvider()

    private fun Request.bodyAsJson(): JSONObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `buildRequest targets the messages endpoint with x-api-key auth`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-ant-test",
                model = "claude-3-5-haiku-latest",
                systemPrompt = "card context",
                messages = listOf(AiChatMessage(AiChatRole.USER, "explain this")),
            )

        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("sk-ant-test", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
    }

    @Test
    fun `buildRequest puts system prompt in top-level field, not in messages`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-ant-test",
                model = "claude-3-5-haiku-latest",
                systemPrompt = "card context",
                messages = listOf(AiChatMessage(AiChatRole.USER, "explain this")),
            )

        val json = request.bodyAsJson()
        assertEquals("card context", json.getString("system"))
        assertEquals(1, json.getJSONArray("messages").length())
        assertEquals("user", json.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test
    fun `buildRequest omits system field when systemPrompt is null`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-ant-test",
                model = "claude-3-5-haiku-latest",
                systemPrompt = null,
                messages = listOf(AiChatMessage(AiChatRole.USER, "hi")),
            )

        assertFalse(request.bodyAsJson().has("system"))
    }

    @Test
    fun `parseSseEvent extracts text_delta as a Token`() {
        val data = """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hi"}}"""
        assertEquals(AiSseEvent.Token("Hi"), provider.parseSseEvent(data))
    }

    @Test
    fun `parseSseEvent maps message_stop to Done`() {
        val data = """{"type":"message_stop"}"""
        assertEquals(AiSseEvent.Done, provider.parseSseEvent(data))
    }

    @Test
    fun `parseSseEvent ignores unrelated event types`() {
        val data = """{"type":"message_start"}"""
        assertEquals(AiSseEvent.Ignored, provider.parseSseEvent(data))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.AnthropicProviderTest"`
Expected: FAIL — `AnthropicProvider`가 존재하지 않음.

- [ ] **Step 3: 구현 작성**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/AnthropicProvider.kt
package com.ichi2.anki.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AnthropicProvider : AiProvider {
    override val defaultModel = "claude-3-5-haiku-latest"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request {
        val body =
            JSONObject()
                .put("model", model)
                .put("max_tokens", 1024)
                .put("stream", true)
                .put(
                    "messages",
                    JSONArray(
                        messages.map { message ->
                            JSONObject()
                                .put("role", if (message.role == AiChatRole.USER) "user" else "assistant")
                                .put("content", message.content)
                        },
                    ),
                )
        if (systemPrompt != null) {
            body.put("system", systemPrompt)
        }

        return Request
            .Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    override fun parseSseEvent(data: String): AiSseEvent =
        try {
            val json = JSONObject(data)
            when (json.optString("type")) {
                "content_block_delta" -> {
                    val text = json.getJSONObject("delta").optString("text", "")
                    if (text.isNotEmpty()) AiSseEvent.Token(text) else AiSseEvent.Ignored
                }
                "message_stop" -> AiSseEvent.Done
                else -> AiSseEvent.Ignored
            }
        } catch (e: JSONException) {
            AiSseEvent.Ignored
        }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.AnthropicProviderTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add AnkiDroid/src/main/java/com/ichi2/anki/ai/AnthropicProvider.kt AnkiDroid/src/test/java/com/ichi2/anki/ai/AnthropicProviderTest.kt
git commit -m "feat(ai): add AnthropicProvider adapter"
```

---

### Task 6: `GeminiProvider`

**Files:**
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/GeminiProvider.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/ai/GeminiProviderTest.kt`

**Interfaces:**
- Consumes: `AiProvider`, `AiChatMessage`, `AiChatRole`, `AiSseEvent` (Task 3).
- Produces: `class GeminiProvider : AiProvider`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/ai/GeminiProviderTest.kt
package com.ichi2.anki.ai

import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Buffer

class GeminiProviderTest {
    private val provider = GeminiProvider()

    private fun Request.bodyAsJson(): JSONObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `buildRequest targets streamGenerateContent with key as query param`() {
        val request =
            provider.buildRequest(
                apiKey = "gm-test",
                model = "gemini-2.0-flash",
                systemPrompt = null,
                messages = listOf(AiChatMessage(AiChatRole.USER, "hi")),
            )

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=gm-test",
            request.url.toString(),
        )
    }

    @Test
    fun `buildRequest maps ASSISTANT role to model and includes system_instruction`() {
        val request =
            provider.buildRequest(
                apiKey = "gm-test",
                model = "gemini-2.0-flash",
                systemPrompt = "card context",
                messages =
                    listOf(
                        AiChatMessage(AiChatRole.USER, "hi"),
                        AiChatMessage(AiChatRole.ASSISTANT, "hello"),
                    ),
            )

        val json = request.bodyAsJson()
        val contents = json.getJSONArray("contents")
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertTrue(json.has("system_instruction"))
        assertEquals(
            "card context",
            json
                .getJSONObject("system_instruction")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text"),
        )
    }

    @Test
    fun `parseSseEvent extracts candidate text as a Token`() {
        val data = """{"candidates":[{"content":{"parts":[{"text":"Hi"}]}}]}"""
        assertEquals(AiSseEvent.Token("Hi"), provider.parseSseEvent(data))
    }

    @Test
    fun `parseSseEvent ignores malformed payloads`() {
        assertEquals(AiSseEvent.Ignored, provider.parseSseEvent("not json"))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.GeminiProviderTest"`
Expected: FAIL — `GeminiProvider`가 존재하지 않음.

- [ ] **Step 3: 구현 작성**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/GeminiProvider.kt
package com.ichi2.anki.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class GeminiProvider : AiProvider {
    override val defaultModel = "gemini-2.0-flash"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request {
        val body =
            JSONObject()
                .put(
                    "contents",
                    JSONArray(
                        messages.map { message ->
                            JSONObject()
                                .put("role", if (message.role == AiChatRole.USER) "user" else "model")
                                .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
                        },
                    ),
                )
        if (systemPrompt != null) {
            body.put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        return Request
            .Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    override fun parseSseEvent(data: String): AiSseEvent =
        try {
            val text =
                JSONObject(data)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .optString("text", "")
            if (text.isNotEmpty()) AiSseEvent.Token(text) else AiSseEvent.Ignored
        } catch (e: JSONException) {
            AiSseEvent.Ignored
        }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.GeminiProviderTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add AnkiDroid/src/main/java/com/ichi2/anki/ai/GeminiProvider.kt AnkiDroid/src/test/java/com/ichi2/anki/ai/GeminiProviderTest.kt
git commit -m "feat(ai): add GeminiProvider adapter"
```

---

### Task 7: `AiKeyStore` (암호화된 API 키 저장소)

**Files:**
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/AiKeyStore.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/ai/AiKeyStoreTest.kt`

**Interfaces:**
- Produces: `class AiKeyStore(context: Context) { var apiKey: String?; fun hasApiKey(): Boolean }` — Task 8(설정 UI)과 Task 11(`AiChatViewModel`)에서 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/ai/AiKeyStoreTest.kt
package com.ichi2.anki.ai

import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`is`
import org.junit.Test

class AiKeyStoreTest : RobolectricTest() {
    @Test
    fun `hasApiKey is false before any key is set`() {
        val store = AiKeyStore(targetContext)
        assertThat(store.hasApiKey(), `is`(false))
    }

    @Test
    fun `apiKey round-trips through encrypted storage`() {
        val store = AiKeyStore(targetContext)
        store.apiKey = "sk-secret"

        assertThat(AiKeyStore(targetContext).apiKey, equalTo("sk-secret"))
    }

    @Test
    fun `setting apiKey to null clears hasApiKey`() {
        val store = AiKeyStore(targetContext)
        store.apiKey = "sk-secret"
        store.apiKey = null

        assertThat(store.hasApiKey(), `is`(false))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.AiKeyStoreTest"`
Expected: FAIL — `AiKeyStore`가 존재하지 않음.

- [ ] **Step 3: 구현 작성**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/AiKeyStore.kt
package com.ichi2.anki.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the user's LLM API key encrypted-at-rest, separate from ordinary [com.ichi2.anki.settings.Prefs]. */
class AiKeyStore(
    context: Context,
) {
    private val prefs =
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()

    companion object {
        private const val FILE_NAME = "ai_key_store"
        private const val KEY_API_KEY = "api_key"
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.AiKeyStoreTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add AnkiDroid/src/main/java/com/ichi2/anki/ai/AiKeyStore.kt AnkiDroid/src/test/java/com/ichi2/anki/ai/AiKeyStoreTest.kt
git commit -m "feat(ai): add encrypted AiKeyStore for API key storage"
```

---

### Task 8: AI 설정 UI (프로바이더 선택 + API 키 입력)

**Files:**
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/settings/enums/AiProviderKind.kt`
- Modify: `AnkiDroid/src/main/java/com/ichi2/anki/settings/Prefs.kt`
- Modify: `AnkiDroid/src/main/res/values/deurim_strings.xml`
- Modify: `AnkiDroid/src/main/res/xml/preferences_deurim.xml`
- Modify: `AnkiDroid/src/main/java/com/ichi2/anki/preferences/DeurimSettingsFragment.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/settings/enums/AiProviderKindTest.kt`

**Interfaces:**
- Consumes: `AiKeyStore` (Task 7).
- Produces: `enum class AiProviderKind(override val entryResId: Int) : PrefEnum { OPENAI, ANTHROPIC, GEMINI }`, `Prefs.aiProviderKind` — Task 11(`AiChatViewModel`)에서 어떤 `AiProvider` 구현체를 쓸지 결정하는 데 사용.

- [ ] **Step 1: 실패하는 테스트 작성 (enum이 3개 값을 갖고 각각 고유한 저장값을 가지는지)**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/settings/enums/AiProviderKindTest.kt
package com.ichi2.anki.settings.enums

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.junit.Test

class AiProviderKindTest {
    @Test
    fun `has exactly OpenAI, Anthropic and Gemini`() {
        assertThat(
            AiProviderKind.entries.map { it.name },
            containsInAnyOrder("OPENAI", "ANTHROPIC", "GEMINI"),
        )
    }

    @Test
    fun `every entry has a distinct entryResId`() {
        val ids = AiProviderKind.entries.map { it.entryResId }
        assertThat(ids.toSet(), hasSize(ids.size))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.settings.enums.AiProviderKindTest"`
Expected: FAIL — `AiProviderKind`가 존재하지 않음.

- [ ] **Step 3: `deurim_strings.xml`에 문자열/배열 추가**

`AnkiDroid/src/main/res/values/deurim_strings.xml`의 `</resources>` 바로 위에 추가:

```xml
    <!-- AI card chat -->
    <string name="pref_cat_ai" maxLength="41">AI</string>
    <string name="pref_ai_provider_key">deurim_ai_provider</string>
    <string name="pref_ai_provider_title">AI provider</string>
    <string name="ai_provider_openai_value">openai</string>
    <string name="ai_provider_anthropic_value">anthropic</string>
    <string name="ai_provider_gemini_value">gemini</string>
    <string-array name="ai_provider_labels">
        <item>ChatGPT (OpenAI)</item>
        <item>Claude (Anthropic)</item>
        <item>Gemini (Google)</item>
    </string-array>
    <string-array name="ai_provider_values">
        <item>@string/ai_provider_openai_value</item>
        <item>@string/ai_provider_anthropic_value</item>
        <item>@string/ai_provider_gemini_value</item>
    </string-array>
    <string name="pref_ai_api_key_key">deurim_ai_api_key</string>
    <string name="pref_ai_api_key_title">API key</string>
    <string name="pref_ai_api_key_summary_set">A key is set (tap to change)</string>
    <string name="pref_ai_api_key_summary_not_set">Required to use AI chat — stored encrypted on this device only</string>
    <string name="ai_chat_title">Ask AI about this card</string>
```

- [ ] **Step 4: `AiProviderKind` 구현**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/settings/enums/AiProviderKind.kt
package com.ichi2.anki.settings.enums

import com.ichi2.anki.R

/** [R.array.ai_provider_values] */
enum class AiProviderKind(
    override val entryResId: Int,
) : PrefEnum {
    OPENAI(R.string.ai_provider_openai_value),
    ANTHROPIC(R.string.ai_provider_anthropic_value),
    GEMINI(R.string.ai_provider_gemini_value),
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.settings.enums.AiProviderKindTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: `Prefs.kt`에 델리게이트 추가**

`AnkiDroid/src/main/java/com/ichi2/anki/settings/Prefs.kt`의 `var hkey by stringPref(R.string.hkey_key)` 줄 근처(동기화 섹션 밖, 파일 하단 아무 논리적 섹션)에 새 섹션 추가:

```kotlin
    // ****************************************** AI chat ****************************************** //

    var aiProviderKind: AiProviderKind by enumPref(R.string.pref_ai_provider_key, AiProviderKind.OPENAI)
```

파일 상단 import 블록에 추가:

```kotlin
import com.ichi2.anki.settings.enums.AiProviderKind
```

- [ ] **Step 7: `preferences_deurim.xml`에 AI 카테고리 추가**

`AnkiDroid/src/main/res/xml/preferences_deurim.xml`에서 `</PreferenceScreen>` 바로 위에 추가:

```xml
    <PreferenceCategory android:title="@string/pref_cat_ai">
        <ListPreference
            android:defaultValue="@string/ai_provider_openai_value"
            android:entries="@array/ai_provider_labels"
            android:entryValues="@array/ai_provider_values"
            android:key="@string/pref_ai_provider_key"
            android:title="@string/pref_ai_provider_title"
            app:useSimpleSummaryProvider="true"/>
        <EditTextPreference
            android:key="@string/pref_ai_api_key_key"
            android:title="@string/pref_ai_api_key_title"
            android:summary="@string/pref_ai_api_key_summary_not_set"/>
    </PreferenceCategory>
```

`deurim_summary_entries` 배열에 새 항목 추가 (`deurim_strings.xml`):

```xml
    <string-array name="deurim_summary_entries">
        <item>@string/pref_cat_deurim_update</item>
        <item>@string/pref_cat_deurim_reviewer</item>
        <item>@string/pref_cat_sound_effects</item>
        <item>@string/pref_cat_ai</item>
    </string-array>
```
(기존 3개 항목을 지우지 말고 `pref_cat_ai` 항목만 추가한다.)

- [ ] **Step 8: `DeurimSettingsFragment`에 API 키 필드 배선**

`AnkiDroid/src/main/java/com/ichi2/anki/preferences/DeurimSettingsFragment.kt`의 `initSubscreen()` 끝에 호출 추가:

```kotlin
    override fun initSubscreen() {
        requirePreference<Preference>(R.string.pref_check_update_now_key).setOnPreferenceClickListener {
            UpdateManager.checkNow(requireActivity())
            true
        }
        initReviewProgressBarColorPref()
        initAiApiKeyPref()
    }

    private fun initAiApiKeyPref() {
        val keyStore = AiKeyStore(requireContext())
        requirePreference<EditTextPreference>(R.string.pref_ai_api_key_key).apply {
            isPersistent = false
            text = keyStore.apiKey
            summaryProvider =
                Preference.SummaryProvider<EditTextPreference> {
                    if (keyStore.hasApiKey()) {
                        getString(R.string.pref_ai_api_key_summary_set)
                    } else {
                        getString(R.string.pref_ai_api_key_summary_not_set)
                    }
                }
            setOnPreferenceChangeListener { preference, newValue ->
                keyStore.apiKey = (newValue as String).trim().ifBlank { null }
                (preference as EditTextPreference).text = keyStore.apiKey
                true
            }
        }
    }
```

파일 상단 import에 추가:

```kotlin
import androidx.preference.EditTextPreference
import com.ichi2.anki.ai.AiKeyStore
```

- [ ] **Step 9: 컴파일 확인**

Run: `./gradlew :AnkiDroid:compileFullDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add AnkiDroid/src/main/java/com/ichi2/anki/settings/enums/AiProviderKind.kt AnkiDroid/src/test/java/com/ichi2/anki/settings/enums/AiProviderKindTest.kt AnkiDroid/src/main/java/com/ichi2/anki/settings/Prefs.kt AnkiDroid/src/main/res/values/deurim_strings.xml AnkiDroid/src/main/res/xml/preferences_deurim.xml AnkiDroid/src/main/java/com/ichi2/anki/preferences/DeurimSettingsFragment.kt
git commit -m "feat(ai): add AI provider/API key settings under Useful features"
```

---

### Task 9: `MetaDB`에 대화 기록 테이블 추가

**Files:**
- Modify: `AnkiDroid/src/main/java/com/ichi2/anki/MetaDB.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/MetaDBAiChatTest.kt`

**Interfaces:**
- Consumes: `AiChatMessage`, `AiChatRole` (Task 3).
- Produces: `MetaDB.storeAiChatMessage(context: Context, nid: NoteId, message: AiChatMessage)`, `MetaDB.getAiChatMessages(context: Context, nid: NoteId): List<AiChatMessage>` — Task 11(`AiChatViewModel`)에서 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/MetaDBAiChatTest.kt
package com.ichi2.anki

import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.Test

class MetaDBAiChatTest : RobolectricTest() {
    @Test
    fun `getAiChatMessages is empty for a note with no history`() {
        assertThat(MetaDB.getAiChatMessages(targetContext, nid = 1L), empty())
    }

    @Test
    fun `stored messages are returned in insertion order for the given note`() {
        MetaDB.storeAiChatMessage(targetContext, nid = 1L, AiChatMessage(AiChatRole.USER, "What is this?"))
        MetaDB.storeAiChatMessage(targetContext, nid = 1L, AiChatMessage(AiChatRole.ASSISTANT, "It's a flashcard."))
        MetaDB.storeAiChatMessage(targetContext, nid = 2L, AiChatMessage(AiChatRole.USER, "unrelated note"))

        assertThat(
            MetaDB.getAiChatMessages(targetContext, nid = 1L),
            contains(
                AiChatMessage(AiChatRole.USER, "What is this?"),
                AiChatMessage(AiChatRole.ASSISTANT, "It's a flashcard."),
            ),
        )
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.MetaDBAiChatTest"`
Expected: FAIL — `MetaDB.storeAiChatMessage`/`getAiChatMessages`가 존재하지 않음.

- [ ] **Step 3: 테이블 마이그레이션 추가**

`AnkiDroid/src/main/java/com/ichi2/anki/MetaDB.kt`에서 `DATABASE_VERSION`을 8→9로 변경:

```kotlin
    private const val DATABASE_VERSION = 9
```

`upgradeDB()`의 `updateWidgetStatus(metaDb)` 호출 다음 줄에 추가:

```kotlin
        updateWidgetStatus(metaDb)
        updateWhiteboardState(metaDb)
        metaDb.execSQL(
            """CREATE TABLE IF NOT EXISTS aiChatMessages (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            nid INTEGER NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            createdAt INTEGER NOT NULL
            )""",
        )
```

- [ ] **Step 4: CRUD 함수 추가**

`close()` 함수 바로 위에 추가:

```kotlin
    /** Appends one message to a note's AI chat history. */
    fun storeAiChatMessage(
        context: Context,
        nid: NoteId,
        message: AiChatMessage,
    ) {
        openDBIfClosed(context)
        try {
            metaDb!!.execSQL(
                "INSERT INTO aiChatMessages (nid, role, content, createdAt) VALUES (?, ?, ?, ?);",
                arrayOf<Any>(nid, message.role.storageValue, message.content, System.currentTimeMillis()),
            )
        } catch (e: Exception) {
            Timber.e(e, "Error storing AI chat message in MetaDB")
        }
    }

    /** Returns a note's AI chat history in insertion order. */
    fun getAiChatMessages(
        context: Context,
        nid: NoteId,
    ): List<AiChatMessage> {
        openDBIfClosed(context)
        val messages = mutableListOf<AiChatMessage>()
        try {
            metaDb!!
                .rawQuery(
                    "SELECT role, content FROM aiChatMessages WHERE nid = ? ORDER BY _id ASC",
                    arrayOf(nid.toString()),
                ).use { cur ->
                    while (cur.moveToNext()) {
                        messages += AiChatMessage(AiChatRole.fromStorageValue(cur.getString(0)), cur.getString(1))
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching AI chat messages from MetaDB")
        }
        return messages
    }
```

파일 상단 import 블록에 추가:

```kotlin
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.libanki.NoteId
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.MetaDBAiChatTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add AnkiDroid/src/main/java/com/ichi2/anki/MetaDB.kt AnkiDroid/src/test/java/com/ichi2/anki/MetaDBAiChatTest.kt
git commit -m "feat(ai): persist AI chat history per note in MetaDB"
```

---

### Task 10: `ViewerAction.AI_CHAT` + 아이콘 + `ReviewerViewModel` 배선

**Files:**
- Create: `AnkiDroid/src/main/res/drawable/ic_ai_chat.xml`
- Modify: `AnkiDroid/src/main/java/com/ichi2/anki/preferences/reviewer/ViewerAction.kt`
- Modify: `AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerViewModel.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/preferences/reviewer/ViewerActionTest.kt`

**Interfaces:**
- Consumes: `CardContentExtractor` (Task 2).
- Produces: `ViewerAction.AI_CHAT`, `ReviewerViewModel.openAiChatFlow: SharedFlow<AiChatLaunchArgs>`, `data class AiChatLaunchArgs(val noteId: NoteId, val cardContent: String) : Parcelable` — Task 12(`AiChatBottomSheetFragment`)에서 수집.

- [ ] **Step 1: 실패하는 테스트 작성 (모든 액션이 title/defaultBindings when-분기를 빠짐없이 처리하는지는 컴파일 타임에 강제되므로, AI_CHAT이 메뉴에만 노출되는지를 검증)**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/preferences/reviewer/ViewerActionTest.kt
package com.ichi2.anki.preferences.reviewer

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test

class ViewerActionTest {
    @Test
    fun `AI_CHAT is menu-only by default`() {
        assertThat(ViewerAction.AI_CHAT.defaultDisplayType, `is`(MenuDisplayType.MENU_ONLY))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.preferences.reviewer.ViewerActionTest"`
Expected: FAIL — `ViewerAction.AI_CHAT`이 존재하지 않음.

- [ ] **Step 3: 아이콘 벡터 드로어블 추가**

```xml
<!-- AnkiDroid/src/main/res/drawable/ic_ai_chat.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM6,9h12v2H6V9zM14,14H6v-2h8v2zM18,7H6V5h12v2z"/>
</vector>
```

- [ ] **Step 4: `ViewerAction`에 `AI_CHAT` 추가**

`ViewerAction.kt`의 "Menu only" 그룹에 추가 (`TOGGLE_WHITEBOARD` 줄 다음):

```kotlin
    TOGGLE_WHITEBOARD(R.id.action_toggle_whiteboard, R.drawable.ic_enable_whiteboard, MENU_ONLY),
    AI_CHAT(R.id.action_ai_chat, R.drawable.ic_ai_chat, MENU_ONLY),
```

`defaultBindings`의 "No default gestures" 그룹(`emptyList()`로 끝나는 when 분기)에 `AI_CHAT,`을 추가:

```kotlin
            DELETE,
            CARD_INFO,
            TAG,
            EXIT,
            AI_CHAT,
            RESCHEDULE_NOTE,
```

`title()`의 when 분기에 추가 (`TOGGLE_WHITEBOARD -> getString(R.string.gesture_toggle_whiteboard)` 다음 줄):

```kotlin
                AI_CHAT -> getString(R.string.ai_chat_title)
```

`R.id.action_ai_chat`를 위한 id 선언을 `AnkiDroid/src/main/res/values/ids.xml`에 추가:

```xml
    <item type="id" name="action_ai_chat"/>
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.preferences.reviewer.ViewerActionTest"`
Expected: PASS

- [ ] **Step 6: `ReviewerViewModel`에 열기 이벤트 배선**

`ReviewerViewModel.kt`에 `AiChatLaunchArgs` 정의(파일 최상단, import 아래):

```kotlin
@kotlinx.parcelize.Parcelize
data class AiChatLaunchArgs(
    val noteId: com.ichi2.anki.libanki.NoteId,
    val cardContent: String,
) : android.os.Parcelable
```

`destinationFlow` 선언(103번째 줄 근처) 다음에 새 SharedFlow 추가:

```kotlin
    val openAiChatFlow = MutableSharedFlow<AiChatLaunchArgs>()
```

`replayMedia()` 함수 다음에 새 private 함수 추가:

```kotlin
    private suspend fun openAiChat() {
        val card = currentCard.await()
        val content =
            withCol {
                com.ichi2.anki.ai.CardContentExtractor.extract(card.question(this), card.answer(this))
            }
        openAiChatFlow.emit(AiChatLaunchArgs(noteId = card.nid, cardContent = content))
    }
```

`executeAction()`의 `when (action)` 블록에 분기 추가 (`ViewerAction.TOGGLE_WHITEBOARD -> toggleWhiteboard()` 다음 줄):

```kotlin
                    ViewerAction.AI_CHAT -> openAiChat()
```

- [ ] **Step 7: 컴파일 확인**

Run: `./gradlew :AnkiDroid:compileFullDebugKotlin`
Expected: BUILD SUCCESSFUL (특히 `executeAction`의 `when`이 `AI_CHAT` 분기 없이는 컴파일되지 않는 것으로 배선이 빠지지 않았음을 확인)

- [ ] **Step 8: Commit**

```bash
git add AnkiDroid/src/main/res/drawable/ic_ai_chat.xml AnkiDroid/src/main/java/com/ichi2/anki/preferences/reviewer/ViewerAction.kt AnkiDroid/src/main/res/values/ids.xml AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerViewModel.kt AnkiDroid/src/test/java/com/ichi2/anki/preferences/reviewer/ViewerActionTest.kt
git commit -m "feat(ai): add AI_CHAT viewer action and wire ReviewerViewModel to emit launch args"
```

---

### Task 11: `AiChatViewModel`

**Files:**
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatViewModel.kt`
- Test: `AnkiDroid/src/test/java/com/ichi2/anki/ai/chat/AiChatViewModelTest.kt`

**Interfaces:**
- Consumes: `AiChatMessage`, `AiChatRole`, `AiProvider`, `AiSseEvent`, `AiStreamingClient`, `AiError` (Task 3), `AiKeyStore` (Task 7), `MetaDB.storeAiChatMessage`/`getAiChatMessages` (Task 9).
- Produces:
  ```kotlin
  class AiChatViewModel(
      private val noteId: NoteId,
      private val cardContent: String,
      private val provider: AiProvider,
      private val apiKey: String,
      private val model: String,
      private val streamingClient: AiStreamingClient,
      private val storeMessage: (AiChatMessage) -> Unit,
      private val loadHistory: () -> List<AiChatMessage>,
  ) : ViewModel() {
      val messages: StateFlow<List<AiChatMessage>>
      val errorFlow: SharedFlow<AiError>
      fun sendMessage(text: String)
  }
  ```
  Task 12(`AiChatBottomSheetFragment`)가 이 클래스를 `ViewModelProvider.Factory`로 생성해 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성 (fake provider + fake persistence, 실제 네트워크 없음)**

```kotlin
// AnkiDroid/src/test/java/com/ichi2/anki/ai/chat/AiChatViewModelTest.kt
package com.ichi2.anki.ai.chat

import app.cash.turbine.test
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.ai.AiProvider
import com.ichi2.anki.ai.AiSseEvent
import com.ichi2.anki.ai.AiStreamingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeProvider(
    private val events: List<AiSseEvent>,
) : AiProvider {
    override val defaultModel = "fake-model"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request = Request.Builder().url("https://example.invalid/").get().build()

    override fun parseSseEvent(data: String): AiSseEvent = AiSseEvent.Ignored

    fun asFlow(): Flow<AiSseEvent> = flowOf(*events.toTypedArray())
}

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `sendMessage appends the user message immediately and streams the assistant reply`() =
        runTest {
            val provider = FakeProvider(listOf(AiSseEvent.Token("Hel"), AiSseEvent.Token("lo"), AiSseEvent.Done))
            val stored = mutableListOf<AiChatMessage>()
            val viewModel =
                AiChatViewModel(
                    noteId = 1L,
                    cardContent = "Front: Q Back: A",
                    provider = provider,
                    apiKey = "key",
                    model = "fake-model",
                    streamingClient = FakeStreamingClient(provider.asFlow()),
                    storeMessage = { stored += it },
                    loadHistory = { emptyList() },
                )

            viewModel.messages.test {
                assertEquals(emptyList<AiChatMessage>(), awaitItem())

                viewModel.sendMessage("What is this?")
                assertEquals(listOf(AiChatMessage(AiChatRole.USER, "What is this?")), awaitItem())

                dispatcher.scheduler.advanceUntilIdle()

                assertEquals(
                    listOf(
                        AiChatMessage(AiChatRole.USER, "What is this?"),
                        AiChatMessage(AiChatRole.ASSISTANT, "Hel"),
                    ),
                    awaitItem(),
                )
                assertEquals(
                    listOf(
                        AiChatMessage(AiChatRole.USER, "What is this?"),
                        AiChatMessage(AiChatRole.ASSISTANT, "Hello"),
                    ),
                    awaitItem(),
                )
            }

            assertEquals(
                listOf(
                    AiChatMessage(AiChatRole.USER, "What is this?"),
                    AiChatMessage(AiChatRole.ASSISTANT, "Hello"),
                ),
                stored,
            )
        }
}

private class FakeStreamingClient(
    private val flow: Flow<AiSseEvent>,
) : AiStreamingClient() {
    override fun stream(
        provider: AiProvider,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Flow<AiSseEvent> = flow
}
```

- [ ] **Step 2: `AiStreamingClient.stream`을 open으로 변경 (테스트 더블을 위해 필요)**

`AnkiDroid/src/main/java/com/ichi2/anki/ai/AiStreamingClient.kt`에서 클래스와 함수 선언을 수정:

```kotlin
open class AiStreamingClient(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build(),
) {
    open fun stream(
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.chat.AiChatViewModelTest"`
Expected: FAIL — `AiChatViewModel`이 존재하지 않음.

- [ ] **Step 4: 구현 작성**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatViewModel.kt
package com.ichi2.anki.ai.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.ai.AiError
import com.ichi2.anki.ai.AiProvider
import com.ichi2.anki.ai.AiSseEvent
import com.ichi2.anki.ai.AiStreamingClient
import com.ichi2.anki.libanki.NoteId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AiChatViewModel(
    private val noteId: NoteId,
    private val cardContent: String,
    private val provider: AiProvider,
    private val apiKey: String,
    private val model: String,
    private val streamingClient: AiStreamingClient,
    private val storeMessage: (AiChatMessage) -> Unit,
    private val loadHistory: () -> List<AiChatMessage>,
) : ViewModel() {
    private val _messages = MutableStateFlow(loadHistory())
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _errorFlow = MutableSharedFlow<AiError>()
    val errorFlow = _errorFlow

    fun sendMessage(text: String) {
        val userMessage = AiChatMessage(AiChatRole.USER, text)
        appendAndPersist(userMessage)

        viewModelScope.launch {
            var assistantText = ""
            var hasReceivedToken = false
            streamingClient
                .stream(provider, apiKey, model, cardContent, _messages.value)
                .catch { throwable ->
                    _errorFlow.emit(throwable as? AiError ?: AiError.Network(throwable))
                }.collect { event ->
                    when (event) {
                        is AiSseEvent.Token -> {
                            assistantText += event.text
                            hasReceivedToken = true
                            replaceStreamingAssistantMessage(assistantText)
                        }
                        AiSseEvent.Done -> {
                            if (hasReceivedToken) {
                                storeMessage(AiChatMessage(AiChatRole.ASSISTANT, assistantText))
                            }
                        }
                        AiSseEvent.Ignored -> {}
                    }
                }
        }
    }

    private fun appendAndPersist(message: AiChatMessage) {
        _messages.value = _messages.value + message
        storeMessage(message)
    }

    private fun replaceStreamingAssistantMessage(text: String) {
        val current = _messages.value
        val last = current.lastOrNull()
        _messages.value =
            if (last?.role == AiChatRole.ASSISTANT) {
                current.dropLast(1) + AiChatMessage(AiChatRole.ASSISTANT, text)
            } else {
                current + AiChatMessage(AiChatRole.ASSISTANT, text)
            }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.chat.AiChatViewModelTest"`
Expected: PASS

- [ ] **Step 6: 관련 회귀 확인 (Task 3의 `AiStreamingClient` 테스트가 `open` 변경으로 깨지지 않았는지)**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.AiStreamingClientTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatViewModel.kt AnkiDroid/src/test/java/com/ichi2/anki/ai/chat/AiChatViewModelTest.kt AnkiDroid/src/main/java/com/ichi2/anki/ai/AiStreamingClient.kt
git commit -m "feat(ai): add AiChatViewModel orchestrating streaming and persistence"
```

---

### Task 12: `AiChatBottomSheetFragment` + `ReviewerFragment` 배선

**Files:**
- Create: `AnkiDroid/src/main/res/layout/fragment_ai_chat.xml`
- Create: `AnkiDroid/src/main/res/layout/item_ai_chat_message.xml`
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatMessageAdapter.kt`
- Create: `AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatBottomSheetFragment.kt`
- Modify: `AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerFragment.kt`

**Interfaces:**
- Consumes: `AiChatLaunchArgs`, `ReviewerViewModel.openAiChatFlow` (Task 10); `AiChatViewModel` (Task 11); `AiKeyStore` (Task 7); `Prefs.aiProviderKind`, `AiProviderKind` (Task 8); `OpenAiProvider`/`AnthropicProvider`/`GeminiProvider` (Tasks 4-6); `MetaDB.storeAiChatMessage`/`getAiChatMessages` (Task 9).
- Produces: 최종 사용자 진입점 — 이 태스크로 기능이 end-to-end 동작한다.

이 태스크는 UI 조립이라 자동 유닛 테스트보다 수동 검증이 더 신뢰도 높다(CLAUDE.md 지침: UI 변경은 실제로 실행해 확인). 컴파일 확인 후 에뮬레이터/실기기에서 수동 검증한다.

- [ ] **Step 1: 메시지 아이템 레이아웃**

```xml
<!-- AnkiDroid/src/main/res/layout/item_ai_chat_message.xml -->
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingHorizontal="12dp"
    android:paddingVertical="4dp">

    <TextView
        android:id="@+id/messageText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="start"
        android:background="@drawable/bg_ai_chat_bubble"
        android:maxWidth="280dp"
        android:padding="10dp"
        android:textColor="?android:attr/textColorPrimary"
        android:textIsSelectable="true"
        tools:text="Sample message"/>
</FrameLayout>
```

```xml
<!-- AnkiDroid/src/main/res/drawable/bg_ai_chat_bubble.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="?attr/colorSurfaceVariant"/>
    <corners android:radius="12dp"/>
</shape>
```

- [ ] **Step 2: 채팅 화면 레이아웃**

```xml
<!-- AnkiDroid/src/main/res/layout/fragment_ai_chat.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingTop="8dp">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/messageList"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:paddingBottom="8dp"/>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="8dp">

        <EditText
            android:id="@+id/messageInput"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="@string/ai_chat_input_hint"
            android:inputType="textCapSentences|textMultiLine"
            android:maxLines="4"/>

        <ImageButton
            android:id="@+id/sendButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@android:color/transparent"
            android:contentDescription="@string/ai_chat_send"
            android:src="@drawable/ic_ai_chat"/>
    </LinearLayout>
</LinearLayout>
```

`deurim_strings.xml`에 두 문자열 추가:

```xml
    <string name="ai_chat_input_hint">Ask a question about this card…</string>
    <string name="ai_chat_send">Send</string>
```

- [ ] **Step 3: 어댑터 구현**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatMessageAdapter.kt
package com.ichi2.anki.ai.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.databinding.ItemAiChatMessageBinding

class AiChatMessageAdapter : RecyclerView.Adapter<AiChatMessageAdapter.Holder>() {
    private var items: List<AiChatMessage> = emptyList()

    fun submitList(messages: List<AiChatMessage>) {
        items = messages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder {
        val binding = ItemAiChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        val message = items[position]
        holder.binding.messageText.text = message.content
        val layoutParams = holder.binding.messageText.layoutParams as android.widget.FrameLayout.LayoutParams
        layoutParams.gravity = if (message.role == AiChatRole.USER) Gravity.END else Gravity.START
        holder.binding.messageText.layoutParams = layoutParams
    }

    override fun getItemCount() = items.size

    class Holder(
        val binding: ItemAiChatMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root)
}
```

- [ ] **Step 4: `BottomSheetDialogFragment` 구현**

```kotlin
// AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatBottomSheetFragment.kt
package com.ichi2.anki.ai.chat

import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.ai.AnthropicProvider
import com.ichi2.anki.ai.AiStreamingClient
import com.ichi2.anki.ai.GeminiProvider
import com.ichi2.anki.ai.OpenAiProvider
import com.ichi2.anki.databinding.FragmentAiChatBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AiProviderKind
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.reviewer.AiChatLaunchArgs
import com.ichi2.anki.utils.ext.collectIn
import dev.androidbroadcast.vbpd.viewBinding

class AiChatBottomSheetFragment : BottomSheetDialogFragment(R.layout.fragment_ai_chat) {
    private val binding by viewBinding(FragmentAiChatBinding::bind)
    private val adapter = AiChatMessageAdapter()

    private val viewModel: AiChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val args =
                    requireNotNull(BundleCompat.getParcelable(requireArguments(), ARG_LAUNCH_ARGS, AiChatLaunchArgs::class.java))
                val keyStore = AiKeyStore(requireContext())
                val providerKind = Prefs.aiProviderKind
                val provider =
                    when (providerKind) {
                        AiProviderKind.OPENAI -> OpenAiProvider()
                        AiProviderKind.ANTHROPIC -> AnthropicProvider()
                        AiProviderKind.GEMINI -> GeminiProvider()
                    }
                @Suppress("UNCHECKED_CAST")
                return AiChatViewModel(
                    noteId = args.noteId,
                    cardContent = args.cardContent,
                    provider = provider,
                    apiKey = keyStore.apiKey.orEmpty(),
                    model = provider.defaultModel,
                    streamingClient = AiStreamingClient(),
                    storeMessage = { message -> MetaDB.storeAiChatMessage(requireContext(), args.noteId, message) },
                    loadHistory = { MetaDB.getAiChatMessages(requireContext(), args.noteId) },
                ) as T
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.messageList.layoutManager = LinearLayoutManager(requireContext())
        binding.messageList.adapter = adapter

        viewModel.messages.collectIn(lifecycleScope) { messages ->
            adapter.submitList(messages)
            if (messages.isNotEmpty()) binding.messageList.scrollToPosition(messages.size - 1)
        }

        viewModel.errorFlow.collectIn(lifecycleScope) {
            showSnackbar(R.string.ai_chat_error)
        }

        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            val keyStore = AiKeyStore(requireContext())
            if (!keyStore.hasApiKey()) {
                showSnackbar(R.string.ai_chat_missing_api_key)
                return@setOnClickListener
            }
            viewModel.sendMessage(text)
            binding.messageInput.text?.clear()
        }
    }

    companion object {
        private const val TAG = "AiChatBottomSheetFragment"
        private const val ARG_LAUNCH_ARGS = "launchArgs"

        fun newInstance(args: AiChatLaunchArgs): AiChatBottomSheetFragment =
            AiChatBottomSheetFragment().apply {
                arguments = Bundle().apply { putParcelable(ARG_LAUNCH_ARGS, args) }
            }

        fun show(
            manager: FragmentManager,
            args: AiChatLaunchArgs,
        ) = newInstance(args).show(manager, TAG)
    }
}
```

`deurim_strings.xml`에 두 문자열 추가:

```xml
    <string name="ai_chat_error">AI request failed — check your connection and API key</string>
    <string name="ai_chat_missing_api_key">Set an AI API key in Settings → Useful features → AI first</string>
```

- [ ] **Step 5: `ReviewerFragment`에서 열기 이벤트 수집**

`ReviewerFragment.kt`의 `viewModel.destinationFlow.collectIn(...)` 블록 다음에 추가:

```kotlin
        viewModel.openAiChatFlow.collectIn(lifecycleScope) { args ->
            com.ichi2.anki.ai.chat.AiChatBottomSheetFragment.show(childFragmentManager, args)
        }
```

- [ ] **Step 6: 전체 컴파일 확인**

Run: `./gradlew :AnkiDroid:compileFullDebugKotlin`
Expected: BUILD SUCCESSFUL (뷰 바인딩 클래스 `FragmentAiChatBinding`/`ItemAiChatMessageBinding`은 위 레이아웃 XML로부터 자동 생성됨)

- [ ] **Step 7: 전체 유닛 테스트 회귀 확인**

Run: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "com.ichi2.anki.ai.*"`
Expected: PASS (모든 AI 관련 테스트)

- [ ] **Step 8: 수동 검증 (에뮬레이터/실기기)**

1. 앱을 디버그 빌드로 설치한다: `./gradlew :AnkiDroid:installFullDebug`
2. 설정 → Useful features → AI에서 프로바이더를 선택하고 실제(또는 테스트용) API 키를 입력한다.
3. 리뷰 화면에서 카드 하나를 연 뒤, 오버플로 메뉴에서 "Ask AI about this card"를 탭한다.
4. 바텀시트가 뜨고, 메시지를 입력해 전송하면 스트리밍으로 답변이 타이핑되듯 채워지는지 확인한다.
5. 바텀시트를 닫고 같은 카드(같은 노트)를 다시 열어 AI 채팅을 열면 이전 대화가 남아있는지 확인한다.
6. API 키를 지운 상태에서 메시지 전송을 시도해 "Set an AI API key..." 스낵바가 뜨는지 확인한다.

- [ ] **Step 9: Commit**

```bash
git add AnkiDroid/src/main/res/layout/fragment_ai_chat.xml AnkiDroid/src/main/res/layout/item_ai_chat_message.xml AnkiDroid/src/main/res/drawable/bg_ai_chat_bubble.xml AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatMessageAdapter.kt AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatBottomSheetFragment.kt AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerFragment.kt AnkiDroid/src/main/res/values/deurim_strings.xml
git commit -m "feat(ai): add AiChatBottomSheetFragment and wire it into the reviewer"
```
