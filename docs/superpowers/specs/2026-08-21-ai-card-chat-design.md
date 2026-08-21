# AI 카드 설명/질문 기능 설계

날짜: 2026-08-21
상태: 브레인스토밍 완료, 구현 계획(writing-plans) 대기

## 배경 및 목표

AnkiDroid Reviewer에서 사용자가 보고 있는 카드 내용을 기반으로 LLM(ChatGPT/Claude/Gemini)과 자유롭게 대화하며 설명을 듣거나 질문할 수 있는 기능을 추가한다. 사용자가 직접 API 키를 입력하는 BYOK(Bring Your Own Key) 방식으로, 서버 비용 없이 오프라인 우선 원칙을 최대한 해치지 않는 선택적 기능으로 제공한다.

## 범위

- Reviewer 툴바에 AI 채팅 진입 버튼 추가
- 설정에 AI 카테고리 신규 추가 (프로바이더 선택 + API 키 입력)
- 카드 콘텐츠를 사람이 읽는 텍스트로 정제해 LLM에 전달
- 노트(noteId) 단위 대화 기록 영속 저장
- 스트리밍 응답 표시

## 범위 밖 (v1 제외)

- 이미지/오디오 멀티모달 전송 (텍스트만 전송)
- 자동 카드 설명 생성 기능 (버튼 클릭 시 자유 채팅으로 바로 진입, 자동 요약 없음)
- 여러 프로바이더 키 동시 저장/전환 (프로바이더 1개 + 키 1개만 지원)
- 별도의 프라이버시 고지 다이얼로그 (설정에서 API 키를 입력하는 행위 자체를 동의로 간주)
- 재시도/레이트리밋 백오프 로직

## 아키텍처 개요

```
Reviewer 툴바 [AI_CHAT 버튼]
      │
      ▼
AiChatViewModel (신규)
      │
      ├─ CardContentExtractor : 카드 HTML → 사람이 읽는 텍스트로 정제
      ├─ AiChatRepository     : 대화 기록 CRUD (MetaDB 신규 테이블, noteId 기준)
      └─ AiProvider (인터페이스)
              ├─ OpenAiProvider
              ├─ AnthropicProvider
              └─ GeminiProvider
                     │
                     ▼
              OkHttp + okhttp-sse (스트리밍 SSE)
```

신규 코드는 기존 `AnkiDroid` 앱 모듈 내 `com.ichi2.anki.ai.*` 패키지로 추가한다(별도 모듈 분리 없음). 프로바이더별 로직은 `AiProvider` 인터페이스 뒤에 완전히 숨겨서, 채팅 UI/ViewModel은 벤더를 몰라도 되게 한다.

## 컴포넌트별 설계

### 1. 설정 (AI 카테고리)

- `AnkiDroid/src/main/res/xml/preferences_ai.xml` 신규 작성.
- `AnkiDroid/src/main/java/com/ichi2/anki/preferences/AiSettingsFragment.kt` 신규 작성, `SettingsFragment`를 상속.
- `Preferences.kt`/`HeaderFragment.kt`에 AI 헤더 등록.
- 항목:
  - 프로바이더 선택 (`ListPreference`: ChatGPT / Claude / Gemini)
  - API 키 입력 (`EditTextPreference`, 입력 시 마스킹 표시)
  - 모델 선택 (선택된 프로바이더에 따라 옵션 갱신, 예: OpenAI는 gpt-4o-mini/gpt-4o)
- API 키는 `androidx.security-crypto`의 `EncryptedSharedPreferences`에 별도 저장한다. 기존 AnkiWeb 동기화 토큰(`hkey`)은 평문 SharedPreferences에 저장되는 관행이 있지만, API 키는 사용자의 개인 결제 수단에 직결된 자격증명이라 유출 시 금전적 피해로 이어질 수 있으므로 기존 관행보다 보안을 우선한다. `androidx.security-crypto` 의존성 신규 추가가 필요하다.

### 2. 카드 콘텐츠 추출 (`CardContentExtractor`)

- 기존 `HtmlUtils.stripHTML()` 계열(`AnkiDroid/src/main/java/com/ichi2/anki/backend/HtmlUtils.kt`)을 재사용해 HTML 태그를 제거한다.
- `[sound:xxx.mp3]` 태그는 제거한다(오디오는 텍스트로 설명할 수 없으므로 생략).
- `<img>` 태그는 생략한다(이미지 미전송 결정과 일치).
- Cloze(`{{c1::정답::힌트}}`)는 **항상 정답을 포함한 전체 맥락**을 LLM에 전달한다. 앞면/뒷면 등 현재 화면 상태와 무관하게 동일한 정보를 보낸다. (AI가 카드 내용을 더 정확히 이해하고 설명하도록 하기 위함이며, 사용자가 원치 않으면 화면을 보지 않고 질문만 할 수도 있다는 점을 감안한 결정)

### 3. 프로바이더 & 스트리밍

- `AiProvider` 인터페이스:
  - `fun buildRequest(messages: List<ChatMessage>, apiKey: String, model: String): Request`
  - `fun parseChunk(rawChunk: String): String?`
- 각 프로바이더(OpenAI/Anthropic/Gemini)는 벤더별 요청 JSON 포맷과 SSE 청크 포맷 차이를 이 인터페이스 뒤에서 흡수한다.
- 스트리밍은 OkHttp 확장 모듈 `okhttp-sse`의 `EventSource`로 응답을 받아, 토큰 단위로 `AiChatViewModel`의 `StateFlow`에 append한다.
- 에러 처리: 네트워크 실패/인증 오류/레이트리밋 등은 각 프로바이더 어댑터가 공통 `AiError` sealed class로 변환하고, UI는 스낵바 또는 에러 말풍선으로 표시한다. 재시도 로직은 두지 않는다(기존 코드베이스의 네트워크 에러 처리 관행과 동일하게 단순 실패 처리).

### 4. Reviewer 통합

- `AnkiDroid/src/main/java/com/ichi2/anki/preferences/reviewer/ViewerAction.kt`에 `AI_CHAT` 액션을 신규 추가한다(예비 슬롯인 `USER_ACTION_1~9`가 아닌 정식 액션으로 등록하여 문서화와 아이콘을 명확히 한다).
- `ReviewerViewModel.executeAction()`(`AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerViewModel.kt`)에 `AI_CHAT` 처리 분기를 추가한다.
- `res/menu/reviewer.xml`에 메뉴 아이템을 추가한다.
- 버튼 클릭 시 바텀시트(또는 다이얼로그)로 채팅 UI를 연다 — 전체 화면 전환은 하지 않아 카드를 보면서 대화할 수 있게 한다.
- 채팅을 열 때 현재 카드의 `noteId` 기준으로 기존 대화 기록을 불러와 이어서 표시한다.
- 사용자가 처음 채팅을 열면 자동 설명 없이 빈 채팅창에서 자유롭게 질문을 시작한다.

### 5. 저장소

- `AnkiDroid/src/main/java/com/ichi2/anki/MetaDB.kt`에 `ai_chat_messages` 테이블을 신규 추가한다: `id, noteId, role(user/assistant), content, timestampMillis`.
- `DATABASE_VERSION`을 증가시키고 `upgradeDB()`에 마이그레이션 분기를 추가한다(기존 컨벤션을 그대로 따름 — Room 미사용).
- 같은 노트에서 나온 카드들(예: 정방향/역방향 카드)은 대화 기록을 공유한다.

## 데이터 흐름 요약

1. 사용자가 Reviewer에서 AI_CHAT 버튼 클릭
2. `AiChatViewModel`이 현재 카드의 `noteId`로 `AiChatRepository`에서 기존 대화 기록 조회, 채팅 UI에 표시
3. 사용자가 메시지 입력 → `CardContentExtractor`가 카드 필드를 정제한 텍스트를 시스템 프롬프트/컨텍스트로 포함해 `AiProvider`에 요청 위임
4. 선택된 프로바이더 어댑터가 벤더별 API로 SSE 스트리밍 요청, 응답 토큰을 순차적으로 UI에 반영
5. 완료된 사용자 메시지 + AI 응답을 `AiChatRepository`를 통해 `ai_chat_messages`에 저장

## 테스트 전략

- `CardContentExtractor`: HTML/cloze 다양한 케이스에 대한 유닛테스트를 실패하는 상태로 먼저 작성 후 구현(프로젝트 규칙: 버그 수정/신규 로직은 실패 테스트 선행).
- `AiProvider` 어댑터별 요청 빌드/응답 파싱 유닛테스트 — 실제 API 호출 없이 mock JSON 사용.
- SSE 스트리밍 파싱은 MockWebServer로 청크 단위 응답을 시뮬레이션해 테스트.
- `AiChatRepository`(MetaDB 신규 테이블) CRUD 및 마이그레이션 유닛테스트.

## 미해결/추후 고려 사항

- 모델별 토큰 한도 초과 시 대화 기록을 어떻게 잘라낼지(오래된 메시지 삭제 vs 요약)는 v1에서는 다루지 않고, 필요 시 후속 스펙에서 다룬다.
- 다국어(UI 문자열) 대응은 기존 `strings.xml` 컨벤션을 따른다.
