# 쥬얼리 (ZooEarly) — AI API 명세서

> **v1.6.0 · 2026-08-24**
> React Native 앱 ↔ API Gateway ↔ FastAPI Inference Server(STT / LLM / TTS → OpenAI API)
> **이 문서가 기존 `zooearly-api-spec.md`(13개 엔드포인트)를 대체한다.** 시나리오·스토리·진행 상태는 전부 앱 로컬로 이동했고, 서버에 남는 것은 AI 추론뿐이다.

| 항목 | 값 |
|---|---|
| Base URL | `https://zooearly.app/api/v1/ai` |
| 프로토콜 | HTTPS only |
| 인코딩 | UTF-8 |
| 요청/응답 | `application/json` (음성 업로드만 `multipart/form-data`) |
| 인증 | 없음 (프로토타입) |
| 엔드포인트 | **6개** — `chat` / `stt` / `tts` / `feedback` / `pronunciation` / `pronunciation/sentences` |

---

## 변경 이력

| 버전 | 날짜 | 변경 | 앱 영향 |
|---|---|---|---|
| **1.6.0** | 2026-08-24 | `pronunciation/sentences`에 `study`(수업시간 시) 카테고리 추가. 9개 → **10개** | ⚠️ **있음** — 수업시간 "같이 읽어볼까요?"도 이제 이 목록의 `sentenceId`로 `/pronunciation`을 부를 수 있다. `study`는 3개가 아니라 1개다 |
| **1.5.0** | 2026-08-24 | `pronunciation/sentences` 신설 · `pronunciation`의 `targetSentence`→`sentenceId` 전환 · `quizSentence` 제거 · 잘함/못함 판정이 앱→FastAPI로 이동 | ⚠️ **있음** — "표현 고르기" 선택지가 앱 번들이 아니라 서버 목록이 된다. `/pronunciation` 요청 필드명이 바뀐다. 빈칸 문장은 앱이 직접 만들어야 한다 |
| **1.4.0** | 2026-08-22 | `pronunciation`(발음 채점) 추가 · 오디오 **최대 길이 60초 → 30초** | ⚠️ **있음** — 녹음을 30초에서 끊어야 한다. 발음 피드백 화면은 신규 구현 |
| **1.3.0** | 2026-08-21 | `tts`의 `language`를 **필수**로 전환 | ⚠️ **있음** — `/tts`를 부르는 **모든 곳**에 넣어야 한다. 피드백 화면뿐 아니라 `DIALOGUE` 🔊·`LISTEN` 스텝의 한국어 재생도 `"KOREAN"`을 명시한다. 누락 시 `400 INVALID_PARAMETER` |
| 1.2.0 | 2026-08-21 | `tts`에 `language` 선택 필드 추가 | — (1.3.0에서 필수로 바뀜) |
| **1.1.0** | 2026-08-21 | `chat` / `feedback`에 `nickname` **필수** 필드 추가 | ⚠️ **있음** — 앱이 온보딩에서 받은 닉네임을 매 요청에 보내야 한다. 누락 시 `400 INVALID_PARAMETER` |
| 1.0.0 | 2026-08-21 | 최초 작성. 엔드포인트 4개 | — |

> `nativeLanguage`는 1.0.0과 동일하게 **선택**이다 (생략 시 `KOREAN`). 바뀌지 않았다.

---

## 0. 아키텍처 계약

```
React Native App ──HTTPS/REST──▶ API Gateway ──HTTP──▶ FastAPI ──▶ OpenAI API
 (UI/시나리오/게임/로컬 상태)      (릴레이 전용)         (STT/LLM/TTS)
```

### 0.1 게이트웨이는 릴레이다

게이트웨이(Spring)가 하는 일은 딱 세 가지다.

1. **요청 검증** — 필수 파라미터, 오디오 포맷·용량. 잘못된 요청은 FastAPI까지 가지 않고 게이트웨이에서 `400`으로 끊는다.
2. **전달** — 검증을 통과한 요청을 FastAPI의 동일 엔드포인트로 그대로 넘기고, 응답을 그대로 되돌려준다. **body를 가공하지 않는다.**
3. **에러 통일** — FastAPI가 죽었거나 늦거나 5xx를 내면, 앱에는 항상 §1.3의 공통 에러 포맷으로 변환해서 내려준다. 앱은 FastAPI의 생(raw) 에러를 볼 일이 없다.

**하지 않는 일**: DB 저장, 대화 이력 관리, 사용자 조회, 비즈니스 로직. 게이트웨이는 상태가 없다(stateless).

### 0.2 상태는 전부 앱에 있다

서버가 아무것도 기억하지 않으므로, **문맥이 필요한 요청은 앱이 문맥을 함께 보낸다.**

- `chat`의 대화 이력(`history`) — 앱이 로컬에 쌓아서 매 요청에 실어 보낸다
- `feedback`의 목표 문장(`targetSentence`) — 스텝 데이터가 앱 번들에 있으므로 앱이 보낸다
- 시나리오 컨텍스트(`scenario`) — LLM 프롬프트 구성용 힌트로 앱이 보낸다
- 아이 호칭(`nickname`) — 앱 온보딩에서 필수로 받는 값이므로 앱이 매 요청에 보낸다. 서버는 저장하지 않는다

**예외 — `pronunciation`의 `sentenceId`(§6-1)는 앱이 만들지 않는다.** 발음 연습 문장
10개는 고정 목록이라 서버(`GET /pronunciation/sentences`)가 준다. 사용자별로 다른 걸
기억하는 게 아니라 누가 불러도 같은 값이 오므로 게이트웨이는 여전히 무상태다 — 아이
개인의 상태를 저장하는 것과는 다르다.

### 0.3 경로 매핑

앱은 이 문서의 `/api/v1/ai/*` 경로만 안다. **게이트웨이가 FastAPI로 부르는 실제 경로는
이것과 다르다** (v1.4.0부터) — FastAPI가 이미 정해둔 이름(`/internal/v1/speech/transcribe` 등)을
그대로 쓰기로 했고, 게이트웨이의 `application.yml`(`inference.path.*`)에서 관리한다.
FastAPI가 경로를 또 바꿔도 앱과 이 명세는 그대로다 — 게이트웨이 설정만 고치면 된다.

실제 대응은 FastAPI 담당자용 문서 [`zooearly-gateway-to-fastapi.md`](zooearly-gateway-to-fastapi.md) §1 참고.

### 0.4 타임아웃 정책

| 구간 | 값 | 초과 시 |
|---|---|---|
| Gateway → FastAPI 연결 | 3s | `504 AI_TIMEOUT` |
| Gateway → FastAPI 응답 (`stt` / `tts` / `feedback`) | 15s | `504 AI_TIMEOUT` |
| Gateway → FastAPI 응답 (`chat` — STT+LLM+TTS 3단) | 30s | `504 AI_TIMEOUT` |
| 앱 → Gateway | 위 값 + 5s 여유를 앱 쪽 클라이언트에 설정 | 앱 로컬 폴백 |

> 앱은 어떤 타임아웃·에러에서도 아이에게 "오류"를 보여주지 않는다. 전부 "괜찮아, 다시 해볼까?" 화면으로 폴백한다.

---

## 1. 공통 규약

### 1.1 타입 표기법

| 표기 | TypeScript 대응 | 설명 | 예시 |
|---|---|---|---|
| `string` | `string` | 문자열 | `"많이 주세요."` |
| `string(base64)` | `string` | base64 인코딩된 바이너리 | `"UklGRi4A..."` |
| `string(enum)` | union type | 허용값은 §1.4 | `"LUNCH"` |
| `string(JSON)` | `string` | multipart 필드에 실린 JSON 문자열 | `"[{\"role\":\"user\",...}]"` |
| `integer` | `number` | 정수 | `3` |
| `number` | `number` | 소수 | `0.97` |
| `boolean` | `boolean` | 참/거짓 | `true` |
| `object` | interface | 하위 필드는 별도 표 | `{ ... }` |
| `object[]` | `T[]` | 객체 배열 | `[{ ... }]` |
| **`?` 접미사** | nullable | **`null`이 올 수 있다** | `string?` |

> `?`가 없으면 `null`이 오지 않는다. 빈 배열과 `null`은 다르다 — 값이 없을 때 `[]`를 보내지 `null`을 보내지 않는다.

### 1.2 응답 포맷

**성공**

```json
{ "success": true, "data": { } }
```

**실패**

```json
{
  "success": false,
  "error": {
    "code": "AI_SERVER_ERROR",
    "message": "추론 서버가 응답하지 않습니다.",
    "field": null
  }
}
```

| 이름 | 타입 | 설명 |
|---|---|---|
| `error.code` | `string(enum)` | §1.3 에러 코드 |
| `error.message` | `string` | 개발자용. 아이 화면에 띄우지 않는다 |
| `error.field` | `string?` | 문제가 된 파라미터명. 검증 에러에만 채워진다 |

### 1.3 에러 코드

| HTTP | code | 발생 위치 | 상황 |
|---|---|---|---|
| 400 | `INVALID_PARAMETER` | Gateway | 필수 파라미터 누락·형식 오류 |
| 400 | `UNSUPPORTED_AUDIO_FORMAT` | Gateway | 허용 외 오디오 포맷 |
| 400 | `AUDIO_TOO_LARGE` | Gateway | 음성 파일 10MB 초과 |
| 413 | `PAYLOAD_TOO_LARGE` | Gateway | 요청 전체 용량 초과 |
| 422 | `STT_FAILED` | FastAPI | STT 엔진 자체 실패 (인식 실패와 다름 — §2 계약 참고) |
| 429 | `RATE_LIMITED` | FastAPI | OpenAI API 쿼터 초과 |
| 502 | `AI_SERVER_ERROR` | Gateway | FastAPI가 5xx를 반환하거나 연결 불가 |
| 504 | `AI_TIMEOUT` | Gateway | FastAPI 응답이 §0.4 타임아웃 초과 |
| 500 | `INTERNAL_ERROR` | Gateway | 게이트웨이 자체 오류 |

> **FastAPI의 어떤 에러도 앱에 그대로 새지 않는다.** FastAPI가 `{"detail": "..."}`를 내더라도 게이트웨이가 `AI_SERVER_ERROR`로 감싼다. 단 `STT_FAILED` / `RATE_LIMITED`는 FastAPI가 §1.2 포맷으로 직접 만들어 보내고 게이트웨이는 그대로 통과시킨다.

### 1.4 오디오 규격

**업로드 (앱 → 서버, multipart)**

| 항목 | 값 |
|---|---|
| 포맷 | `m4a` / `wav` / `webm` |
| 최대 용량 | 10MB |
| 최대 길이 | **30초** |
| multipart 필드명 | `audio` |

**다운로드 (서버 → 앱, JSON 내 base64)**

| 항목 | 값 |
|---|---|
| 포맷 | `mp3` (OpenAI TTS 기본) |
| 인코딩 | base64 문자열 |
| 필드 구조 | `{ "data": "<base64>", "format": "mp3" }` |

> **응답 오디오는 항상 같은 `audio` 객체 구조다.** `chat`과 `tts`가 동일한 모양을 쓴다 — 앱의 재생 코드가 하나면 된다.
>
> base64는 원본보다 약 33% 크다. TTS 문장은 짧아(수 초) 실사용 페이로드는 수백 KB 수준이므로 허용한다. 문장이 길어져 문제가 되면 그때 바이너리 응답으로 바꾼다.

### 1.5 Enum

**`scenario`** — LLM 프롬프트 컨텍스트용 힌트. 앱 로컬의 시나리오 코드와 동일하다.

| 값 | 상황 |
|---|---|
| `ARRIVAL` | 등교하기 |
| `CLASS` | 수업시간 |
| `LUNCH` | 급식시간 |
| `DISMISSAL` | 하교시간 |

**`nativeLanguage`** — 피드백·번역 생성 언어

| 값 | 언어 |
|---|---|
| `KOREAN` | 한국어 (번역 생략) |
| `CHINESE` | 중국어 |
| `VIETNAMESE` | 베트남어 |

**`role`** — `chat` 대화 이력의 화자

| 값 | 의미 |
|---|---|
| `user` | 아이 |
| `assistant` | AI 캐릭터(선생님/친구) |

---

## 2. POST /api/v1/ai/chat — 음성 대화 (통합 파이프라인) ★

아이의 음성을 올리면 서버가 **STT → LLM → TTS를 한 번에** 처리하고, 텍스트와 음성을 함께 돌려준다. 왕복 1회로 지연을 최소화한 핵심 엔드포인트다.

```http
POST /api/v1/ai/chat
Content-Type: multipart/form-data
```

**Request (multipart)**

| 이름 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|
| `audio` | `file` | ✅ | 아이의 발화. §1.4 업로드 규격 | `speech.m4a` |
| `scenario` | `string(enum)` | ✅ | LLM 시스템 프롬프트 구성용 | `"LUNCH"` |
| `history` | `string(JSON)` | ✅ | 지금까지의 대화. 없으면 `"[]"` | 아래 참고 |
| `nativeLanguage` | `string(enum)` | — | 생략 시 `KOREAN` | `"VIETNAMESE"` |
| `nickname` | `string` | ✅ | 아이 호칭. LLM이 말을 걸 때 쓴다. 최대 20자 | `"민수"` |

**`history` JSON 구조** — 앱이 로컬에 쌓아 매 요청에 실어 보낸다 (서버는 무상태)

```json
[
  { "role": "assistant", "content": "불고기 많이 줄까?" },
  { "role": "user", "content": "네, 많이 주세요." }
]
```

| 이름 | 타입 | 설명 |
|---|---|---|
| `[].role` | `string(enum)` | `user` / `assistant` |
| `[].content` | `string` | 발화 텍스트 |

> **`history`는 최근 10턴까지만 보낸다.** 그 이상은 앱이 잘라서 보낸다 — 프롬프트 길이와 비용이 대화 길이에 비례해 늘어나는 것을 앱 쪽에서 차단한다.

```bash
curl -X POST https://zooearly.app/api/v1/ai/chat \
  -F "audio=@speech.m4a;type=audio/m4a" \
  -F "scenario=LUNCH" \
  -F 'history=[{"role":"assistant","content":"불고기 많이 줄까?"}]' \
  -F "nativeLanguage=VIETNAMESE" \
  -F "nickname=민수"
```

**Response `200 OK`**

| 이름 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `userText` | `string?` | STT 결과. **못 알아들으면 `null`** | `"네, 많이 주세요."` |
| `aiText` | `string` | LLM이 생성한 응답 문장 | `"그래, 많이 줄게! 맛있게 먹어."` |
| `audio` | `object` | AI 응답의 TTS. §1.4 다운로드 규격 | `{ ... }` |
| `audio.data` | `string(base64)` | mp3 바이너리 | `"SUQzBAAA..."` |
| `audio.format` | `string` | 항상 `"mp3"` | `"mp3"` |

```json
{
  "success": true,
  "data": {
    "userText": "네, 많이 주세요.",
    "aiText": "그래, 많이 줄게! 맛있게 먹어.",
    "audio": { "data": "SUQzBAAA...", "format": "mp3" }
  }
}
```

**설계 계약**

1. **STT가 아이 말을 못 알아들어도 `200`이다.** `userText: null`로 내려가고, `aiText`는 "잘 안 들렸어. 다시 말해 줄래?" 류의 되묻기 문장이 온다. `422 STT_FAILED`는 STT 엔진 자체가 죽었을 때만 쓴다.
2. **응답을 받은 앱은 `history`에 두 턴을 추가한다** — `{role:"user", content:userText}` + `{role:"assistant", content:aiText}`. `userText`가 `null`이면 user 턴은 추가하지 않는다.
3. **오디오 원본은 서버에 저장하지 않는다.** 추론 직후 폐기한다. 응답 헤더 `X-Audio-Retention: none`.
4. **`aiText`는 국립국어원 표준 한국어교육과정 1~2급 어휘 범위로 생성한다.** 이 제약은 FastAPI의 시스템 프롬프트가 담당한다.

**에러** — `400 INVALID_PARAMETER` / `UNSUPPORTED_AUDIO_FORMAT` / `AUDIO_TOO_LARGE`, `422 STT_FAILED`, `429 RATE_LIMITED`, `502 AI_SERVER_ERROR`, `504 AI_TIMEOUT`

---

## 3. POST /api/v1/ai/stt — 음성 → 텍스트

음성만 텍스트로 바꾼다. **`SPEAK` 스텝(목표 문장 따라 말하기)** 처럼 LLM 응답이 필요 없는 화면에서 쓴다 — 인식 결과와 목표 문장의 매칭 판정은 앱 로컬에서 한다.

```http
POST /api/v1/ai/stt
Content-Type: multipart/form-data
```

**Request (multipart)**

| 이름 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|
| `audio` | `file` | ✅ | §1.4 업로드 규격 | `attempt.m4a` |
| `language` | `string` | — | BCP-47. 생략 시 `ko-KR` | `"ko-KR"` |

**Response `200 OK`**

| 이름 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `text` | `string?` | 인식 결과. **못 알아들으면 `null`** | `"많이 주세요"` |
| `confidence` | `number?` | 0~1. 엔진이 안 주면 `null`. **화면 표시 금지** | `0.94` |

```json
{
  "success": true,
  "data": { "text": "많이 주세요", "confidence": 0.94 }
}
```

> **인식 실패는 에러가 아니다.** `text: null`로 `200`이 내려간다. 앱은 이를 "다시 해볼까?" 화면으로 처리한다.

**에러** — `400 INVALID_PARAMETER` / `UNSUPPORTED_AUDIO_FORMAT` / `AUDIO_TOO_LARGE`, `422 STT_FAILED`, `429 RATE_LIMITED`, `502 AI_SERVER_ERROR`, `504 AI_TIMEOUT`

---

## 4. POST /api/v1/ai/tts — 텍스트 → 음성

텍스트를 음성으로 바꾼다. `DIALOGUE` 말풍선의 🔊 버튼, `LISTEN` 스텝의 다시 듣기, 피드백 화면의 자연스러운 표현·모국어 번역 재생에 쓴다.

**한국어 전용이 아니다.** 피드백 화면은 한국어 문장과 모국어 번역을 각각 재생하므로 `language`로 어느 쪽인지 알려준다.

```http
POST /api/v1/ai/tts
Content-Type: application/json
```

**Request Body**

| 이름 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|
| `text` | `string` | ✅ | 읽을 문장. 최대 200자 | `"불고기 많이 줄까?"` |
| `voice` | `string(enum)` | — | `TEACHER` / `FRIEND`. 생략 시 `TEACHER` | `"TEACHER"` |
| `speed` | `number` | — | 0.5~1.5. 생략 시 `0.9` (아동용 기본 느리게) | `0.9` |
| `language` | `string(enum)` | ✅ | 읽을 문장의 언어. §1.5 enum | `"VIETNAMESE"` |

```json
{ "text": "불고기 많이 줄까?", "voice": "TEACHER", "speed": 0.9, "language": "KOREAN" }
```

모국어 번역을 읽어줄 때 — 피드백 화면 아래쪽 상자 (`voice`·`speed`는 생략 가능)

```json
{ "text": "Cho mình nhiều nhé.", "language": "VIETNAMESE" }
```

**Response `200 OK`**

| 이름 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `audio` | `object` | §1.4 다운로드 규격. `chat`과 동일 구조 | `{ ... }` |
| `audio.data` | `string(base64)` | mp3 바이너리 | `"SUQzBAAA..."` |
| `audio.format` | `string` | 항상 `"mp3"` | `"mp3"` |

> **`language`는 필수다.** 한국어 문장을 읽을 때도 `"KOREAN"`을 명시한다. 같은 엔드포인트로 여러 언어가 나가므로 추측의 여지를 두지 않는다 — 성조 부호 없는 로마자 표기(`chao! Minh cung rat vui`)는 다른 언어로 오판되기 쉽고, 그러면 아이가 엉뚱한 발음을 듣는다.
>
> **`/stt`의 `language`와 형식이 다르다.** `/stt`는 BCP-47 자유 문자열(`ko-KR`), `/tts`는 §1.5 enum이다. `/tts`는 앱이 이미 가진 `nativeLanguage` 값을 그대로 쓰면 되고, 닫힌 집합이라 게이트웨이가 검증할 수 있다.
>
> **`voice`는 OpenAI 보이스 ID가 아니라 역할 enum이다.** 역할 → 실제 보이스 매핑(`TEACHER` → `nova` 등)은 FastAPI 설정에 둔다. 보이스를 교체해도 앱과 게이트웨이는 안 바뀐다.
>
> **같은 문장의 TTS 결과는 앱이 로컬 캐시한다.** 스텝 문장은 고정 텍스트라 캐시 적중률이 높다 — 같은 문장을 매번 서버에 묻지 않는다.

**에러** — `400 INVALID_PARAMETER`, `429 RATE_LIMITED`, `502 AI_SERVER_ERROR`, `504 AI_TIMEOUT`

---

## 5. POST /api/v1/ai/feedback — 발화 피드백 생성

> ⚠️ **현재 이 엔드포인트를 부르는 화면이 없다.** 표현 교정 화면("이렇게 말하면 더
> 자연스러워요")을 구현하지 않기로 했고, FastAPI 쪽에도 대응 API가 없다.
> 게이트웨이에는 구현·테스트가 끝난 상태로 남겨둔다 — 나중에 화면이 생기면
> 앱에서 부르기만 하면 된다. 아래 명세는 그때를 위한 것이다.

아이의 발화(STT 결과 텍스트)와 목표 문장을 주면, `FEEDBACK` 스텝에 그릴 피드백 객체를 생성한다. **음성이 아니라 텍스트를 받는다** — STT는 §3에서 이미 끝났다.

```http
POST /api/v1/ai/feedback
Content-Type: application/json
```

**Request Body**

| 이름 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|
| `targetSentence` | `string` | ✅ | 목표 문장 (앱 번들의 스텝 데이터) | `"많이 주세요."` |
| `recognizedText` | `string?` | ✅ | STT 결과. 인식 실패면 `null` | `"많이 주세여"` |
| `scenario` | `string(enum)` | — | 상황 힌트 | `"LUNCH"` |
| `nativeLanguage` | `string(enum)` | — | 번역 생성 언어. 생략 시 `KOREAN`(번역 없음) | `"VIETNAMESE"` |
| `nickname` | `string` | ✅ | 아이 호칭. 피드백 문구에 쓴다. 최대 20자 | `"민수"` |

```json
{
  "targetSentence": "많이 주세요.",
  "recognizedText": "많이 주세여",
  "scenario": "LUNCH",
  "nativeLanguage": "VIETNAMESE",
  "nickname": "민수"
}
```

**Response `200 OK`**

| 이름 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `understood` | `boolean` | 의미가 통했는가 (아이콘 결정) | `true` |
| `matched` | `boolean` | 목표 문장과 통했는가 (별점 판정용) | `true` |
| `similarity` | `number` | 0~1. **화면 표시 금지** | `0.92` |
| `title` | `string` | 배너 제목 | `"잘했어요!"` |
| `body` | `string?` | 배너 본문 | `"무슨 뜻인지 잘 이해했어요."` |
| `naturalSentence` | `string?` | 더 자연스러운 표현 | `"많이 주세요."` |
| `naturalHint` | `string?` | 설명문. 불필요하면 `null` | `"'주세여'보다 '주세요'가 좋아요."` |
| `highlightWords` | `string[]` | 밑줄 칠 어절. 없으면 `[]` | `["주세요"]` |
| `translation` | `string?` | `naturalSentence`의 모국어 번역. `KOREAN`이면 `null` | `"Cho mình nhiều nhé."` |

```json
{
  "success": true,
  "data": {
    "understood": true,
    "matched": true,
    "similarity": 0.92,
    "title": "잘했어요!",
    "body": "무슨 뜻인지 잘 이해했어요.",
    "naturalSentence": "많이 주세요.",
    "naturalHint": "'주세여'보다 '주세요'가 좋아요.",
    "highlightWords": ["주세요"],
    "translation": "Cho mình nhiều nhé."
  }
}
```

> `naturalSentence`와 `translation`은 피드백 화면에서 각각 🔊 버튼이 달린 상자로 표시된다. 탭하면 앱이 `/tts`를 호출한다 — 흐름은 §7 참고.

**설계 계약**

1. **`recognizedText: null`도 유효한 요청이다.** "괜찮아, 다시 해볼까?" 류의 격려 피드백이 생성된다 (`understood: false`, `matched: false`).
2. **`title`에 "틀렸어요"류 문구를 넣지 않는다.** 이 제약은 FastAPI 프롬프트가 담당하되, 게이트웨이 테스트에서도 검증한다.
3. **`matched` 판정과 별점 계산은 이 응답을 받은 앱이 로컬에서 한다.** 서버는 판정 재료만 준다.
4. **발화 기록은 서버에 남지 않는다.** 성공 문장 보관함(마이페이지)도 앱 로컬 저장소가 담당한다.

**에러** — `400 INVALID_PARAMETER`, `429 RATE_LIMITED`, `502 AI_SERVER_ERROR`, `504 AI_TIMEOUT`

---

## 6. POST /api/v1/ai/pronunciation — 발음 채점

아이가 따라 말한 녹음의 **발음**을 채점한다. `발음 피드백` 화면에서 쓴다.

> **`/feedback`과 다르다.** 저쪽은 "어떤 **단어**를 골랐나"를 텍스트로 보고,
> 이쪽은 "어떻게 **소리** 냈나"를 오디오로 본다.
> 단어를 맞게 골랐어도 발음이 어눌할 수 있고, 그 반대도 있다.
> 그래서 STT를 거치지 않고 **녹음을 그대로 보낸다** — 텍스트로는 발음을 알 수 없다.

```http
POST /api/v1/ai/pronunciation
Content-Type: multipart/form-data
```

**Request**

| 이름 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|
| `audio` | `file` | ✅ | 따라 말한 녹음. §1.4 업로드 규격 | `speech.m4a` |
| `sentenceId` | `string` | ✅ | `GET /api/v1/ai/pronunciation/sentences`(§6-1)에서 받은 10개 값 중 하나 | `"arrival_2"` |

> **자유 텍스트가 아니다.** (v1.5.0부터) FastAPI가 자기 문장 목록에서 채점 기준을
> 직접 찾기 때문에, 목록에 없는 문장으로는 채점할 수 없다. 앱이 `sentenceId`를
> 만들어내면 안 되고, 반드시 §6-1이 내려준 값을 그대로 써야 한다.

```bash
curl -X POST https://zooearly.app/api/v1/ai/pronunciation \
  -F "audio=@speech.m4a;type=audio/m4a" \
  -F "sentenceId=arrival_2"
```

**Response `200 OK`**

| 이름 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `sentenceId` | `string` | 요청에 실은 값을 그대로 돌려준다 | `"arrival_2"` |
| `sentence` | `string` | 채점 대상 문장 | `"안녕! 우리 친하게 지내자"` |
| `targetWord` | `string?` | **가장 약하게 발음한 어절.** 빈칸으로 만들 대상. **`null`이면 전부 기준 이상** — 아래 계약 3 참고 | `"지내자"` |
| `targetIndex` | `integer?` | 그 어절이 몇 번째인가 (0부터) | `2` |
| `targetZ` | `number?` | 그 어절의 z점수. **낮을수록 약함** | `-1.82` |
| `words` | `object[]` | 어절별 채점 결과 | 아래 |
| `words[].word` | `string` | 어절 | `"지내자"` |
| `words[].z` | `number?` | z점수 | `-1.82` |
| `words[].warn` | `boolean` | 주의 임계값 미만인가 | `true` |
| `words[].worstPhone` | `string?` | 그 어절에서 가장 약한 음소 | `"ㄴ"` |

```json
{
  "success": true,
  "data": {
    "sentenceId": "arrival_2",
    "sentence": "안녕! 우리 친하게 지내자",
    "targetWord": "지내자",
    "targetIndex": 2,
    "targetZ": -1.82,
    "words": [
      { "word": "안녕!",  "z": 0.31,  "warn": false, "worstPhone": null },
      { "word": "우리",   "z": -0.42, "warn": false, "worstPhone": null },
      { "word": "친하게", "z": -1.12, "warn": false, "worstPhone": null },
      { "word": "지내자", "z": -1.82, "warn": true,  "worstPhone": "ㄴ" }
    ]
  }
}
```

**설계 계약**

1. **점수는 0~1이 아니라 z점수다.** 또래 규준 대비 상대값이라 **음수가 정상**이다. 0에 가까울수록 또래 평균, 낮을수록 약하다. **화면에 숫자를 표시하지 않는다** — 아이에게 점수를 보여주지 않는다.
2. **빈칸은 `targetWord` 하나뿐이다.** `warn`이 여러 개 켜져도 빈칸은 하나만 만든다. 여러 곳을 동시에 지적하면 아이가 좌절한다.
3. **잘함/못함 판정은 FastAPI가 한다** (v1.5.0부터 바뀐 부분). `targetWord`가 `null`이면 **모든 어절이 기준(z ≥ -1.5) 이상**이라는 뜻이다 — 이때는 **퀴즈 화면 없이 바로 칭찬 화면**으로 간다. `null`이 아니면 퀴즈 화면으로 간다. 앱은 이 값을 보고 분기만 하면 되고, 직접 임계값을 계산할 필요가 없다.
4. **빈칸 문장은 앱이 만든다.** 서버는 `quizSentence`를 주지 않는다 — `sentence`를 공백 기준으로 나눠 `targetIndex`번째를 빈칸으로 바꾸면 된다. (v1.5.0에서 `quizSentence` 필드가 빠졌다.)
5. **규준 집단이 우리 사용자와 다르다.** ⚠️ 아래 주의 참고 — 다만 임계값(z ≥ -1.5) 자체는 FastAPI가 정해서 이미 반영했다.

> ### ⚠️ 규준 한계 — 참고
>
> 채점 모델의 규준은 **만 8~13세 네이티브 아동** 262명에서 산출됐다.
> 이 앱의 사용자는 **중도입국 초등 1~2학년(만 7~8세)** 이라 연령·모어가 모두 다르다.
>
> 모델 제작자의 실험(`demo_l2.py`)에서, 외국어 억양이 섞이면
> **주의 어절 비율이 7% → 59%** 로 뛰었다. 임계값을 너무 엄격하게 잡으면
> 아이가 계속 틀렸다는 화면만 보게 되어 §0.4의 톤 원칙과 어긋난다.
>
> **잘함/못함 임계값(z ≥ -1.5)은 FastAPI가 정해서 이미 적용했다** (계약 3).
> 그래도 실제 중도입국 아동 녹음으로 검증된 값은 아니므로, 시연·초기 운영 중
> "다 틀렸다고 나온다"는 피드백이 들어오면 FastAPI 쪽에 임계값 재조정을 요청한다.

**에러** — `400 INVALID_PARAMETER` / `UNSUPPORTED_AUDIO_FORMAT` / `AUDIO_TOO_LARGE`, `422 STT_FAILED`(목록에 없는 `sentenceId` 포함), `429 RATE_LIMITED`, `502 AI_SERVER_ERROR`, `504 AI_TIMEOUT`

---

## 6-1. GET /api/v1/ai/pronunciation/sentences — 발음 연습 문장 목록

발음 연습용 문장 10개(등교·급식·하교 3개씩 + 수업시간 시 1개)를 받는다.
**"어떤 표현을 사용해볼까요?" 화면의 선택지 3개, 수업시간 "같이 읽어볼까요?"의
시 구절이 모두 여기서 온다** (2026-08-24부터) — 그 전에는 앱 번들에 하드코딩돼 있었다.

```http
GET /api/v1/ai/pronunciation/sentences
```

요청 파라미터 없음.

**Response `200 OK`**

| 이름 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `sentenceId` | `string` | `POST /api/v1/ai/pronunciation`(§6)에 그대로 실어 보내는 값 | `"arrival_1"` |
| `category` | `string(enum)` | `arrival` / `study` / `lunch` / `departure`. 화면 그룹핑용 | `"arrival"` |
| `text` | `string` | 화면에 보여줄 문장 원문. `study`만 여러 문장이 한 항목에 이어져 있다 (시 전체) | `"안녕 나도 만나서 반가워 !"` |

```json
{
  "success": true,
  "data": [
    { "sentenceId": "arrival_1",   "category": "arrival",   "text": "안녕 나도 만나서 반가워 !" },
    { "sentenceId": "arrival_2",   "category": "arrival",   "text": "안녕! 우리 친하게 지내자" },
    { "sentenceId": "arrival_3",   "category": "arrival",   "text": "안녕 잘 부탁해 !" },
    { "sentenceId": "study_1",     "category": "study",     "text": "노란 꽃이 피었어요. 예쁜 꽃이 피었어요. 바람이 살랑살랑 꽃이 웃어요." },
    { "sentenceId": "lunch_1",     "category": "lunch",     "text": "조금만 주세요." },
    { "sentenceId": "lunch_2",     "category": "lunch",     "text": "적당히 주세요." },
    { "sentenceId": "lunch_3",     "category": "lunch",     "text": "많이 주세요." },
    { "sentenceId": "departure_1", "category": "departure", "text": "안녕히 가세요!" },
    { "sentenceId": "departure_2", "category": "departure", "text": "네, 안녕히 가세요." },
    { "sentenceId": "departure_3", "category": "departure", "text": "안녕히 계세요 !" }
  ]
}
```

**설계 계약**

1. **`category`는 §1.5의 `scenario` enum과 다르다.** `arrival`/`study`/`lunch`/`departure`는
   소문자이고 이 API 전용 값이다. `scenario`(`ARRIVAL`/`CLASS`/`LUNCH`/`DISMISSAL`, 대문자)와
   섞어 쓰지 않는다. 특히 `departure` ↔ `DISMISSAL`, `study` ↔ `CLASS` 이름이 다르다는 점을 주의한다.
2. **`study`만 3개가 아니라 1개다.** 시가 하나뿐이라 고를 필요가 없어서인 것으로 보인다.
   화면에 "고르는 UI"가 필요 없다 — `category === "study"`인 항목을 그대로 쓰면 된다.
3. **10개 전부를 한 번에 받는다.** 시나리오마다 따로 부르지 않는다 — 앱이 `category`로
   화면에 맞는 항목만 걸러서 보여준다.
4. **앱이 캐시해도 된다.** 문장 10개는 고정값이다. 앱 실행마다 새로 받을 필요는 없지만,
   서버가 바뀔 가능성을 생각하면 세션마다 한 번은 새로 받는 편이 안전하다.
5. **`말해보기`(자유 발화) 경로에는 `sentenceId`가 없다.** 이 목록에서 문장을 **고른**
   경우에만 `sentenceId`가 생기고, 그 값으로만 §6 발음 채점을 부를 수 있다.

**에러** — `502 AI_SERVER_ERROR`, `504 AI_TIMEOUT`

---

## 엔드포인트 요약 (6개)

| 절 | Method | Path | 입력 | 출력 | 쓰는 화면 |
|---|---|---|---|---|---|
| §2 | POST | `/api/v1/ai/chat` | 음성 + history (multipart) | 텍스트 + 음성(base64) | 자유 대화 — **현재 쓰는 화면 없음** |
| §3 | POST | `/api/v1/ai/stt` | 음성 (multipart) | 텍스트 | 말해보기, 같이 읽어볼까요 |
| §4 | POST | `/api/v1/ai/tts` | 텍스트 (JSON) | 음성(base64) | 🔊 버튼, 시 터치 |
| §5 | POST | `/api/v1/ai/feedback` | 텍스트 2개 (JSON) | **표현 교정** 객체 | 이렇게 말하면 더 자연스러워요 — **현재 쓰는 화면 없음** |
| §6 | POST | `/api/v1/ai/pronunciation` | 음성 + `sentenceId` (multipart) | **발음 채점** 객체 | 발음 피드백 (빈칸 퀴즈) |
| §6-1 | GET | `/api/v1/ai/pronunciation/sentences` | 없음 | 문장 10개 배열 | 어떤 표현을 사용해볼까요?, 같이 읽어볼까요? |

> `feedback`과 `pronunciation`은 성격이 다르다. 전자는 **어떤 단어를 골랐나**(텍스트),
> 후자는 **어떻게 소리 냈나**(오디오)를 본다. 화면도 다르다.

---

## 7. 대표 호출 흐름

```
[표현 고르기 → 발음 피드백]
  ① GET /pronunciation/sentences        → 문장 10개
       └ category로 현재 시나리오(등교/급식/하교)에 맞는 3개만 골라 보여준다.
         (수업시간의 시 읽기는 category="study" 1개를 고를 것 없이 바로 쓴다)
  ② 아이가 3개 중 하나를 고른다          → 그 sentenceId를 들고 있는다
  ③ 🔊 문장 상자 탭 → POST /tts          → 미리 들어본다 (선택)
  ④ 🎤 따라 말하기 녹음 종료
  ⑤ POST /pronunciation { audio, sentenceId }
       └ targetWord가 null이면 → 칭찬 화면 (퀴즈 없이 바로)
       └ null이 아니면        → 퀴즈 화면. sentence + targetIndex로 빈칸을 앱이 만든다
  ⑥ 앱 로컬: 진행 상태 저장

[SPEAK 스텝 — 자유 발화, 채점 없이 인식만]
  ① 녹음 종료
  ② POST /stt                          → "많이 주세여"
  ③ 앱 로컬: 화면에 반영 (표현 교정 §5는 현재 미사용)

[자유 대화 화면]
  ① 녹음 종료
  ② POST /chat (audio + scenario + history)
       서버 내부: STT → LLM → TTS
  ③ 응답: userText + aiText + mp3
  ④ 앱 로컬: history에 2턴 추가, mp3 재생

[DIALOGUE 스텝 — 🔊 버튼]  /  [LISTEN 스텝 — 다시 듣기]
  ① 로컬 캐시 확인 → 있으면 즉시 재생
  ② 없으면 POST /tts { text: <앱 번들 문장>, language: "KOREAN" }
       └ 한국어 문장이어도 language를 넣는다. 필수다
  ③ mp3 캐시 + 재생

[피드백 화면 — 🔊 상자 두 개]  ※ §5가 구현되면 쓸 흐름. 현재는 미사용
  ① POST /feedback 응답에서 두 문장을 받아둔다
       naturalSentence  "많이 주세요."          (한국어)
       translation      "Cho mình nhiều nhé."   (모국어, KOREAN이면 null)
  ② 윗상자 탭  → POST /tts { text: naturalSentence, language: "KOREAN" }
  ③ 아랫상자 탭 → POST /tts { text: translation,     language: <아이의 nativeLanguage> }
  ④ 앱 로컬: 문장별로 캐시. 같은 문장은 두 번 묻지 않는다

[네트워크 실패 시 (어디서든)]
  → "괜찮아, 다시 해볼까?" 폴백. 스텝 진행은 막지 않는다.
```

> **스텝 플레이(목표 문장 있음) vs `chat`(자유 대화)의 구분 기준**: 목표 문장이 정해져 있으면 `stt`/`pronunciation` 조합을 쓰고, 자유 대화면 `chat`을 쓴다. 스텝 플레이에서 `chat`을 쓰지 않는 이유는 LLM 응답 생성·TTS가 불필요해 지연과 비용만 늘기 때문이다.
>
> **피드백 화면의 아랫상자에는 `language`를 반드시 넣는다.** 같은 `/tts`로 한국어와 모국어가 둘 다 나가므로, 언어를 안 알려주면 FastAPI가 텍스트로 추측해야 한다. 성조 부호 없는 로마자 표기(`chao! Minh cung rat vui`)는 오판되기 쉽고, 그러면 아이가 엉뚱한 발음을 듣는다.
>
> **"어떤 표현을 사용해볼까요?"의 선택지는 (v1.5.0부터) 서버가 준다.** `GET /pronunciation/sentences`가 내려주는 10개 중 시나리오에 맞는 항목이다(등교/급식/하교는 3개, 수업시간 시는 1개). §0.2의 무상태 원칙과는 어긋나지 않는다 — 서버가 사용자별로 다른 걸 기억하는 게 아니라, 누가 불러도 같은 고정 목록을 주는 것뿐이다. (다른 화면의 스텝 진행 상태 같은 건 여전히 앱 로컬이다.)

---

## 8. 게이트웨이 구현 노트 (Spring)

기존 도메인 설계(`zooearly-domain-design.md`)의 4개 도메인(user/story/play/speech)은 이 아키텍처에서 **전부 사라진다.** 남는 구조는 이것뿐이다.

```
src/main/java/com/zooearly/
├── ZooEarlyApplication.java
├── common/
│   ├── response/ApiResponse.java        success/data/error 래퍼
│   ├── response/ErrorCode.java          §1.3
│   └── exception/GlobalExceptionHandler.java
└── ai/
    ├── AiController.java                4개 엔드포인트
    ├── AiRelayService.java              검증 + FastAPI 호출 + 에러 변환
    └── client/
        └── InferenceClient.java         WebClient/RestClient. 타임아웃 §0.4
```

- **DB 의존성이 없다.** JPA·데이터소스 설정을 넣지 않는다.
- `AiRelayService`는 응답 body를 파싱하지 않고 통과시키는 것이 기본이다. 파싱하는 순간 FastAPI 응답 스키마가 바뀔 때마다 게이트웨이도 배포해야 한다.
- multipart는 스트리밍으로 릴레이한다(메모리에 전부 올리지 않는다) — 10MB 오디오 동시 요청을 견디기 위함이다.
