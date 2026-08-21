# 쥬얼리 (ZooEarly) — AI API 명세서

> **v1.2.0 · 2026-08-21**
> React Native 앱 ↔ API Gateway ↔ FastAPI Inference Server(STT / LLM / TTS → OpenAI API)
> **이 문서가 기존 `zooearly-api-spec.md`(13개 엔드포인트)를 대체한다.** 시나리오·스토리·진행 상태는 전부 앱 로컬로 이동했고, 서버에 남는 것은 AI 추론뿐이다.

| 항목 | 값 |
|---|---|
| Base URL | `https://zooearly.app/api/v1/ai` |
| 프로토콜 | HTTPS only |
| 인코딩 | UTF-8 |
| 요청/응답 | `application/json` (음성 업로드만 `multipart/form-data`) |
| 인증 | 없음 (프로토타입) |
| 엔드포인트 | **4개** — `chat` / `stt` / `tts` / `feedback` |

---

## 변경 이력

| 버전 | 날짜 | 변경 | 앱 영향 |
|---|---|---|---|
| **1.2.0** | 2026-08-21 | `tts`에 `language` **선택** 필드 추가 | 없음 (하위 호환) — 다만 **모국어 문장을 읽을 때는 넣어야** 발음이 맞는다 |
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

### 0.3 경로 매핑

게이트웨이와 FastAPI는 경로를 1:1 미러링한다. 헷갈릴 여지를 없애기 위해 접두사만 다르고 나머지는 같다.

| 앱 → Gateway | Gateway → FastAPI |
|---|---|
| `POST /api/v1/ai/chat` | `POST /ai/chat` |
| `POST /api/v1/ai/stt` | `POST /ai/stt` |
| `POST /api/v1/ai/tts` | `POST /ai/tts` |
| `POST /api/v1/ai/feedback` | `POST /ai/feedback` |

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
| 최대 길이 | 60초 |
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
| `language` | `string(enum)` | — | 읽을 문장의 언어. 생략 시 `KOREAN` | `"VIETNAMESE"` |

```json
{ "text": "불고기 많이 줄까?", "voice": "TEACHER", "speed": 0.9, "language": "KOREAN" }
```

모국어 번역을 읽어줄 때 — 피드백 화면 아래쪽 상자

```json
{ "text": "Cho mình nhiều nhé.", "language": "VIETNAMESE" }
```

**Response `200 OK`**

| 이름 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `audio` | `object` | §1.4 다운로드 규격. `chat`과 동일 구조 | `{ ... }` |
| `audio.data` | `string(base64)` | mp3 바이너리 | `"SUQzBAAA..."` |
| `audio.format` | `string` | 항상 `"mp3"` | `"mp3"` |

> **`language`를 생략하면 `KOREAN`으로 본다.** 모국어 문장을 읽을 때는 반드시 넣어야 한다. 안 넣으면 FastAPI가 텍스트만 보고 언어를 추측해야 하는데, 성조 부호 없는 로마자 표기(`chao! Minh cung rat vui`)는 다른 언어로 오판되기 쉽다.
>
> **`/stt`의 `language`와 형식이 다르다.** `/stt`는 BCP-47 자유 문자열(`ko-KR`), `/tts`는 §1.5 enum이다. `/tts`는 앱이 이미 가진 `nativeLanguage` 값을 그대로 쓰면 되고, 닫힌 집합이라 게이트웨이가 검증할 수 있다.
>
> **`voice`는 OpenAI 보이스 ID가 아니라 역할 enum이다.** 역할 → 실제 보이스 매핑(`TEACHER` → `nova` 등)은 FastAPI 설정에 둔다. 보이스를 교체해도 앱과 게이트웨이는 안 바뀐다.
>
> **같은 문장의 TTS 결과는 앱이 로컬 캐시한다.** 스텝 문장은 고정 텍스트라 캐시 적중률이 높다 — 같은 문장을 매번 서버에 묻지 않는다.

**에러** — `400 INVALID_PARAMETER`, `429 RATE_LIMITED`, `502 AI_SERVER_ERROR`, `504 AI_TIMEOUT`

---

## 5. POST /api/v1/ai/feedback — 발화 피드백 생성

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

**설계 계약**

1. **`recognizedText: null`도 유효한 요청이다.** "괜찮아, 다시 해볼까?" 류의 격려 피드백이 생성된다 (`understood: false`, `matched: false`).
2. **`title`에 "틀렸어요"류 문구를 넣지 않는다.** 이 제약은 FastAPI 프롬프트가 담당하되, 게이트웨이 테스트에서도 검증한다.
3. **`matched` 판정과 별점 계산은 이 응답을 받은 앱이 로컬에서 한다.** 서버는 판정 재료만 준다.
4. **발화 기록은 서버에 남지 않는다.** 성공 문장 보관함(마이페이지)도 앱 로컬 저장소가 담당한다.

**에러** — `400 INVALID_PARAMETER`, `429 RATE_LIMITED`, `502 AI_SERVER_ERROR`, `504 AI_TIMEOUT`

---

## 6. 엔드포인트 요약 (4개)

| # | Method | Path | 입력 | 출력 | 쓰는 화면 |
|---|---|---|---|---|---|
| 2 | POST | `/api/v1/ai/chat` | 음성 + history (multipart) | 텍스트 + 음성(base64) | 자유 대화 |
| 3 | POST | `/api/v1/ai/stt` | 음성 (multipart) | 텍스트 | SPEAK / SHADOW 스텝 |
| 4 | POST | `/api/v1/ai/tts` | 텍스트 (JSON) | 음성(base64) | 🔊 버튼, LISTEN 스텝 |
| 5 | POST | `/api/v1/ai/feedback` | 텍스트 2개 (JSON) | 피드백 객체 | FEEDBACK 스텝 |

---

## 7. 대표 호출 흐름

```
[SPEAK 스텝 — 목표 문장 말하기]
  ① 녹음 종료
  ② POST /stt                          → "많이 주세여"
  ③ POST /feedback                     → 피드백 객체 (matched: true)
  ④ 앱 로컬: 별점 반영, history 미사용

[자유 대화 화면]
  ① 녹음 종료
  ② POST /chat (audio + scenario + history)
       서버 내부: STT → LLM → TTS
  ③ 응답: userText + aiText + mp3
  ④ 앱 로컬: history에 2턴 추가, mp3 재생

[DIALOGUE 스텝 — 🔊 버튼]
  ① 로컬 캐시 확인 → 있으면 즉시 재생
  ② 없으면 POST /tts → mp3 캐시 + 재생

[네트워크 실패 시 (어디서든)]
  → "괜찮아, 다시 해볼까?" 폴백. 스텝 진행은 막지 않는다.
```

> **`stt`+`feedback` 2회 호출 vs `chat` 1회 호출의 구분 기준**: 목표 문장이 정해져 있으면(스텝 플레이) 전자, 자유 대화면 후자다. 스텝 플레이에서 `chat`을 쓰지 않는 이유는 LLM 응답 생성·TTS가 불필요해 지연과 비용만 늘기 때문이다.

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
