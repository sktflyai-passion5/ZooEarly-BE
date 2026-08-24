# 쥬얼리 (ZooEarly) — FastAPI 추론 서버 API 명세

> **2026-08-21 · FastAPI 담당자 작성분 정리**
> 게이트웨이(Spring)가 호출하는 내부 API. 앱이 직접 부르지 않는다.

| 항목 | 값 |
|---|---|
| Base Path | `/internal/v1` |
| 엔드포인트 | **7개** — STT / TTS / 번역 / 말하기 피드백 / 추천 문장 목록 / 낭독 피드백 / 동화 생성 |
| 담당 | 주아연 (STT·TTS) |

## 엔드포인트 요약

| # | Method | Path | 기능 | 요청 형식 | 응답 형식 |
|---|---|---|---|---|---|
| 1 | POST | `/internal/v1/speech/transcribe` | 음성 인식 (STT) | multipart | JSON |
| 2 | POST | `/internal/v1/speech/synthesize` | 음성 합성 (TTS) | JSON | **바이너리 (audio/mpeg)** |
| 3 | POST | `/internal/v1/text/translate` | 번역 | JSON | JSON |
| 4 | POST | `/internal/v1/feedback/speaking` | 말하기 피드백 | **multipart** | JSON |
| 5 | GET | `/internal/v1/feedback/sentences` | 발음 연습 추천 문장 9개 | - | JSON |
| 6 | POST | `/internal/v1/feedback/reading` | 낭독 피드백 | (미작성) | (미작성) |
| 7 | POST | `/internal/v1/story/generate` | 동화 생성 | JSON | JSON |

---

# 1. POST /internal/v1/speech/transcribe — 음성 인식 (STT)

## 0️⃣ 어떤 View 에서 사용되는 API 인가요?

등교/수업/점심/하교 활동 중 **"말해보기"** 및 **"같이 읽어볼까요?"(동시 낭독)** 화면

## 1️⃣ API 설명

사용자 녹음 파일을 텍스트로 변환(STT)하고, 단어별 타임스탬프가 포함된 segments 데이터를 반환하는 API입니다.

## 2️⃣ Request

### 제약조건

> - 요청은 반드시 `multipart/form-data` 형식이어야 합니다.
> - 최대 오디오 길이는 서버 설정(`MAX_AUDIO_DURATION_SEC`, 기본 30초)으로 제한됩니다.

```http
POST /internal/v1/speech/transcribe
Content-Type: multipart/form-data
```

**Request Body**

| 필드명 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `audio_file` | file | ✅ | 사용자 녹음 파일 (wav/m4a/mp3) |
| `language_code` | string | ✅ | 발화 언어 (`ko` \| `vi` \| `zh`) — 기본적으로 대부분 `ko` |

```json
// Request Body 예시 (form-data 형태)
{
  "audio_file": "<binary_file_data>",
  "language_code": "ko"
}
```

## 3️⃣ Response

### ✅ 성공 (200 OK)

요청에 대해 정상적으로 STT 처리가 완료된 경우

```json
{
  "text": "안녕 나도 반가워",
  "language": "ko",
  "duration_sec": 2.3,
  "segments": [
    {"start": 0.0, "end": 1.1, "text": "안녕", "words": [...]},
    {"start": 1.1, "end": 2.3, "text": "나도 반가워", "words": [...]}
  ]
}
```

### ❌ 실패

**지원하지 않는 `language_code` 이거나 오디오 파일 형식이 아닌 경우 (422)**

```json
{
  "detail": "지원하지 않는 언어 코드이거나 잘못된 파일 형식입니다."
}
```

**오디오 길이가 제한을 초과한 경우 (400)**

```json
{
  "detail": "오디오 파일의 길이가 30초를 초과했습니다."
}
```

## 4️⃣ API 수정 로그

- 초기 설계 반영

---

# 2. POST /internal/v1/speech/synthesize — 음성 합성 (TTS)

## 0️⃣ 어떤 View 에서 사용되는 API 인가요?

- NPC 대사 재생
- 시 터치 낭독
- **"이제 따라 말해볼까요?"** (모범 표현 섀도잉)

## 1️⃣ API 설명

텍스트를 받아 음성 파일 데이터(`audio/mpeg`)로 변환하여 **바이너리 스트림으로** 응답하는 API입니다.

## 2️⃣ Request

| 필드명 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `text` | string | ✅ | 읽을 문장 (NPC 대사 / 모범 표현 / 시 구절) |
| `language_code` | string | ✅ | 발화 언어 (`ko` \| `vi` \| `zh`) |
| `voice` | string | ❌ | 목소리 종류 (예: `alloy`, `nova` 등 캐릭터별 매핑) |
| `speed` | float | ❌ | 재생 속도 (기본 1.0, 어린이 대상 0.9 권장) |

```json
// Request Body 예시
{
  "text": "안녕! 나도 만나서 반가워!",
  "language_code": "ko",
  "voice": "alloy",
  "speed": 1.0
}
```

## 3️⃣ Response

### ✅ 성공 (200 OK)

- **Content-Type**: `audio/mpeg`
- 변환된 음성 파일의 바이너리 바이트 스트림을 그대로 반환합니다.

### ❌ 실패

**텍스트가 비어 있거나 지원하지 않는 `language_code` 인 경우 (422)**

```json
{
  "detail": "텍스트가 비어있거나 지원하지 않는 언어입니다."
}
```

## 4️⃣ API 수정 로그

- 초기 설계 반영

---

# 3. POST /internal/v1/text/translate — 번역

## 0️⃣ 어떤 View 에서 사용되는 API 인가요?

말해보기 피드백 결과의 **"이 말의 뜻이에요!"** 화면

## 1️⃣ API 설명

제공된 텍스트를 사용자의 모국어(이주배경학생 언어)로 번역하여 반환합니다.

## 2️⃣ Request

### Request Body (`application/json`)

| 필드명 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `text` | string | ✅ | 번역할 문장 (보통 말하기 피드백의 `natural_expression`) |
| `source_language` | string | ❌ | 원문 언어 (기본 `ko`) |
| `target_language` | string | ✅ | 번역될 사용자 모국어 (`ko` \| `vi` \| `zh`) |

```json
// Request Body 예시
{
  "text": "안녕! 나도 만나서 반가워!",
  "source_language": "ko",
  "target_language": "vi"
}
```

## 3️⃣ Response

### ✅ 성공 (200 OK)

```json
{
  "translated_text": "Chào! Mình cũng rất vui được gặp cậu!",
  "target_language": "vi"
}
```

### ❌ 실패

**지원하지 않는 `target_language` 이거나 텍스트가 비어있는 경우 (422)**

```json
{
  "detail": "번역할 텍스트가 없거나 지원하지 않는 언어입니다."
}
```

---

# 4. POST /internal/v1/feedback/speaking — 말하기(발음) 피드백

## 0️⃣ 어떤 View 에서 사용되는 API 인가요?

발음 피드백 화면

## 1️⃣ API 설명

사용자의 발음에 따른 피드백을 해줍니다.

## 2️⃣ Request

### Request Body (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `audio_file` | file | ✅ | 녹음 파일. `SCORING_MAX_UPLOAD_MB`(기본 20MB) 이하 |
| `sentence_id` | string (enum) | ✅ | `GET /internal/v1/feedback/sentences`에서 받은 9개 값 중 하나. `arrival_1`, `arrival_2`, `arrival_3`, `lunch_1`, `lunch_2`, `lunch_3`, `departure_1`, `departure_2`, `departure_3` |

## 3️⃣ Response

### ✅ 성공 (200 OK)

```json
{
  "sentence_id": "arrival_2",
  "sentence": "안녕! 우리 친하게 지내자",
  "target_word": "지내자",
  "target_index": 3,
  "target_z": -1.82,
  "words": [
    { "word": "안녕!",  "z": 0.31,  "warn": false, "worst_phone": null },
    { "word": "우리",   "z": -0.42, "warn": false, "worst_phone": null },
    { "word": "친하게", "z": -1.12, "warn": false, "worst_phone": null },
    { "word": "지내자", "z": -1.82, "warn": true,  "worst_phone": "ㄴ" }
  ]
}
```

발음이 전부 기준(z ≥ -1.5) 이상으로 좋으면, 위 예시와 달리 `target_word`가 `null`로 옵니다. **이 경우 퀴즈 화면으로 넘어가지 말고 칭찬 화면으로 바로 가야 합니다.**

### ❌ 실패

**지원하지 않는 `target_language` 이거나 텍스트가 비어있는 경우 (422)**

```json
{
  "detail": "번역할 텍스트가 없거나 지원하지 않는 언어입니다."
}
```

## 4️⃣ API 수정 로그

- 2026-08-24 — `text`(원문 직접 전달) 폐지, `sentence_id`로 변경. 9개 고정 문장 중 하나를 선택해 채점하는 방식으로 바뀜. 응답에서 `success`/`data` 래퍼와 `quiz_sentence` 필드 제거, `sentence_id` 필드 추가. `target_word`가 `null`인 경우(발음 전부 기준 이상) 칭찬 화면으로 분기하는 규칙 추가.

---

# 5. GET /internal/v1/feedback/sentences — 발음 연습 추천 문장 목록

## 0️⃣ 어떤 View 에서 사용되는 API 인가요?

발음 피드백 화면 진입 시 문장 선택 화면

## 1️⃣ API 설명

발음 연습용 추천 문장 9개(카테고리 3개 × 3개)를 반환합니다.

> **이 API는 프론트가 직접 부르는 게 아니라, Spring이 내부망에서 이 FastAPI 서버로 호출한 뒤 Spring 자체 API로 프론트에 재노출하는 용도입니다.**
> 프론트가 이 중 1개를 고르면, 그 `sentence_id`와 녹음 파일을 `POST /internal/v1/feedback/speaking`으로 보내 채점합니다.

## 2️⃣ Request

파라미터 없음.

```http
GET /internal/v1/feedback/sentences
```

## 3️⃣ Response

### ✅ 성공 (200 OK)

| 필드 | 타입 | 설명 |
|---|---|---|
| `sentence_id` | string | 채점 API(`POST /internal/v1/feedback/speaking`)를 호출할 때 그대로 넘겨야 하는 값. Spring이 프론트에 내려줄 때도 이 값을 그대로 보존해야 합니다(프론트가 문장을 선택하면 이 id를 다시 Spring에 보내고, Spring이 채점 API를 호출할 때 이 id를 그대로 씁니다). |
| `category` | string | `arrival`(등교) / `lunch`(점심) / `departure`(하교) 3가지 중 하나. 카테고리 화면 그룹핑용. |
| `text` | string | 화면에 보여줄 실제 문장 원문. |

```json
[
  { "sentence_id": "arrival_1",   "category": "arrival",   "text": "안녕 나도 만나서 반가워 !" },
  { "sentence_id": "arrival_2",   "category": "arrival",   "text": "안녕! 우리 친하게 지내자" },
  { "sentence_id": "arrival_3",   "category": "arrival",   "text": "안녕 잘 부탁해 !" },
  { "sentence_id": "lunch_1",     "category": "lunch",     "text": "조금만 주세요." },
  { "sentence_id": "lunch_2",     "category": "lunch",     "text": "적당히 주세요." },
  { "sentence_id": "lunch_3",     "category": "lunch",     "text": "많이 주세요." },
  { "sentence_id": "departure_1", "category": "departure", "text": "안녕히 가세요!" },
  { "sentence_id": "departure_2", "category": "departure", "text": "네, 안녕히 가세요." },
  { "sentence_id": "departure_3", "category": "departure", "text": "안녕히 계세요 !" }
]
```

## 4️⃣ API 수정 로그

- 2026-08-24 — 신규 추가. `/feedback/speaking`이 `sentence_id` 방식으로 바뀌면서, 프론트가 고를 수 있는 문장 목록을 내려주기 위해 신설.

---

# 6. POST /internal/v1/feedback/reading — 낭독 피드백

> **아직 상세 명세가 작성되지 않았다.** Notion 표에 경로만 등록된 상태다.
> 요청·응답 형식이 정해지면 이 문서에 추가한다.

---

# 7. POST /internal/v1/story/generate — 동화 생성

## 0️⃣ 어떤 View 에서 사용되는 API 인가요?

동화 생성 장면

## 1️⃣ API 설명

등교/수업/점심/하교 4개 카테고리 플레이 기록을 받아, LLM(`OPENAI_LLM_MODEL`)이 장면마다 소제목/전환구/인용/내레이션을 담은 동화로 이어 붙여 반환합니다.

## 2️⃣ Request

### Request Body (`application/json`)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `child_name` | `string` | ✅ | 아이 이름. 내레이션에 쓰인다 |
| `scenes` | `list[SceneInput]` | ✅ | 정확히 4개, 순서는 `school_arrival → class → lunch → school_departure` 고정 |
| `scenes[].category` | `StoryCategory` | ✅ | 4개 값 중 하나, 중복·누락 불가 |
| `scenes[].partner_line` | `string \| null` | 조건부 | 대화 장면(등교/점심/하교) **필수**. 상대방이 아이에게 한 말 |
| `scenes[].child_said` | `string \| null` | ❌ | 대화 장면에서 아이가 고른 문장. 없으면 `null` |
| `scenes[].poem_text` | `string \| null` | 조건부 | 수업(`class`) 장면 **필수**. 아이가 읽은 동시 전문 |
| `scenes[].practiced_word` | `string \| null` | ❌ | 발음이 약해 연습한 낱말(발음 피드백 API의 `target_word`). 없으면 `null` |

```json
// Request Body 예시
{
  "child_name": "지우",
  "scenes": [
    {
      "category": "school_arrival",
      "partner_line": "안녕! 오늘도 만나서 반가워",
      "child_said": "안녕! 나도 만나서 반가워 !"
    },
    {
      "category": "class",
      "poem_text": "노란 꽃이 피었어요. 예쁜 꽃이 피었어요. 바람이 살랑살랑 꽃이 웃어요.",
      "practiced_word": "살랑살랑"
    },
    {
      "category": "lunch",
      "partner_line": "오늘 반찬 맛있게 먹어요",
      "child_said": "조금만 주세요."
    },
    {
      "category": "school_departure",
      "partner_line": "오늘도 수고했어, 내일 봐",
      "child_said": "선생님, 안녕히 가세요!"
    }
  ]
}
```

## 3️⃣ Response

### ✅ 성공 (200 OK)

| 필드 | 타입 | 설명 |
|---|---|---|
| `title` | `string` | 동화 제목 (예: "오늘의 학교 동화") |
| `scenes` | `list[SceneOutput]` | 요청과 동일한 개수·순서·카테고리 |
| `scenes[].category` | `StoryCategory` | 요청의 해당 장면과 동일한 값 |
| `scenes[].subtitle` | `string` | 장면 소제목 (4~10음절) |
| `scenes[].opening` | `string` | 장면을 여는 전환구 |
| `scenes[].quote` | `string \| null` | 아이가 한 말(`child_said`를 그대로 인용). 없으면 `null` |
| `scenes[].narration` | `string` | LLM이 생성한 동화 문장(2~3문장). 새 사건·인물 없이 실제 기록만 기반으로 서술 |

```json
{
  "title": "오늘의 학교 동화",
  "scenes": [
    {
      "category": "school_arrival",
      "subtitle": "첫 만남의 아침",
      "opening": "교문 앞이었어요",
      "quote": "안녕! 나도 반가워",
      "narration": "지우가 교문 앞에 서 있었어요. 친구가 밝게 인사했지요. 지우가 두근두근 어깨가 들썩였어요."
    },
    {
      "category": "class",
      "subtitle": "받아쓰기 시간",
      "opening": "교실 문을 열자",
      "quote": "네, 열심히 할게요",
      "narration": "지우가 조용히 교실에 들어갔어요. 선생님이 받아쓰기를 한다고 하셨답니다."
    },
    {
      "category": "lunch",
      "subtitle": "점심의 소리",
      "opening": "맛있는 냄새가 났어요",
      "quote": null,
      "narration": "지우가 급식실에 들어갔어요. 급식 선생님이 반찬을 맛있게 먹으라고 하셨어요."
    },
    {
      "category": "school_departure",
      "subtitle": "하굣길의 인사",
      "opening": "가방을 메고 나서니",
      "quote": "내일 봐요!",
      "narration": "지우가 가방을 메고 교실을 나왔어요. 선생님이 오늘도 수고했다고 하셨지요."
    }
  ]
}
```

### ❌ 실패

| status_code | 상황 | detail |
|---|---|---|
| 422 | `scenes` 개수가 4개가 아님 | `"scenes는 반드시 4개(등교/수업/점심/하교)여야 합니다."` |
| 422 | `scenes`의 카테고리가 4종을 각 1개씩(고정 순서) 포함하지 않음 | `"scenes의 카테고리 또는 순서가 올바르지 않습니다. (등교→수업→점심→하교 순서 필요)"` |
| 422 | `partner_line`이 빈 문자열인 장면이 있음 | `"상대방 대사가 비어 있는 장면이 있습니다."` |
| 502 | LLM 호출 실패(OpenAI API 오류/타임아웃) | `"동화 생성 서비스에 연결할 수 없습니다."` |

## 4️⃣ API 수정 로그

- 2026-08-24 — 요청 필드 개편. `child_action` 삭제 → `child_name`(최상위, 신규) + `partner_line`(구 `child_action` 자리, **의미가 아이의 행동 → 상대방의 대사로 바뀜**) + `child_said`(구 `child_speech`, nullable로 변경). 수업 장면용 `poem_text`·`practiced_word` 추가. 응답도 `narration` 하나에서 `subtitle`/`opening`/`quote`/`narration` 4개로 확장. 422 메시지도 "행동 기록 또는 대사가 비어 있는…" → "상대방 대사가 비어 있는…"으로 변경.
- 초기 설계 — `scenes[].child_action` + `child_speech`(둘 다 필수, 빈 문자열 불가) → 장면별 `narration` 1문장.

> ⚠️ **Spring 게이트웨이에 공유 필요.** 필드명과 의미가 함께 바뀌었다(`child_action` → `partner_line`은 이름만이 아니라 **누가 한 말인지가 반대**다). 게이트웨이가 body를 그대로 통과시키므로 코드 수정은 없지만, 앱 쪽 요청 구성이 바뀌어야 한다.

---

# 부록. 게이트웨이 연동 시 확인해야 할 것

아래는 이 문서를 정리하며 발견된, **Spring 게이트웨이 명세와 어긋나는 지점**이다. 코드를 고치기 전에 FastAPI 담당자와 합의가 필요하다.

| # | 항목 | FastAPI 실제 | 게이트웨이 명세(v1.0) | 영향 |
|---|---|---|---|---|
| 1 | 응답 껍데기 | `{"text": ...}` 처럼 **바로 결과** (feedback만 `{"success", "data"}`) | 전부 `{"success": true, "data": {...}}` | 앱 파싱 실패 |
| 2 | 에러 포맷 | `{"detail": "..."}` (FastAPI 기본형) | `{"success": false, "error": {code, message, field}}` | 앱이 에러 코드 못 읽음 |
| 3 | 오디오 필드명 | `audio_file` | `audio` | STT 호출 실패 |
| 4 | 필드 표기법 | `snake_case` (`language_code`, `translated_text`) | `camelCase` (`targetLanguage`, `translatedText`) | 전 필드 불일치 |
| 5 | 언어 코드 | `ko` / `vi` / `zh` | `KOREAN` / `VIETNAMESE` / `CHINESE` | enum 불일치 |
| 6 | TTS 응답 | **바이너리** `audio/mpeg` | JSON 내 base64 | 게이트웨이 릴레이 방식 변경 필요 |
| 7 | 오디오 길이 제한 | 30초 | 60초 | 앱 녹음 제한 조정 필요 |
| 8 | 말하기 피드백 입력 | **multipart (음성 파일)** | JSON (STT 결과 텍스트) | 호출 흐름 자체가 다름 |
| 9 | 피드백 응답 내용 | 발음 점수(`z`, `worst_phone`) | 표현 교정(`naturalSentence`, `highlightWords`) | 기능 정의가 다름 |
| 10 | `chat` (대화) | **없음** | 있음 | 앱에 자유 대화 화면이 없으므로 삭제 예정 |

**8번과 9번이 가장 크다.** 게이트웨이 명세는 "STT로 텍스트를 얻은 뒤 그 텍스트로 피드백을 요청"하는 2단계 흐름인데, FastAPI는 **음성 파일을 직접 받아 발음을 채점**한다. 즉 피드백의 성격 자체가 다르다.

- 게이트웨이 명세: **표현 교정** — "'주세여'보다 '주세요'가 좋아요"
- FastAPI 실제: **발음 채점** — 어느 단어의 발음이 약한지 z-score로 판정, 빈칸 퀴즈 문장 생성

어느 쪽이 앱 기획에 맞는지 확인한 뒤 한쪽으로 통일해야 한다.
