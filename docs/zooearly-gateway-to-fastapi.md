# 게이트웨이 → FastAPI 연동 규약

> **FastAPI 담당자에게 주는 문서다.** 이것만 보고 구현하면 붙는다.
>
> 작성 근거: 실제로 게이트웨이를 띄우고 FastAPI 자리(8000)에 캡처 서버를 앉혀
> **실제로 나간 HTTP 요청을 그대로 받아 적었다.** 추측이 아니다.

## 0. 왜 이 문서가 따로 있나

`zooearly-ai-openapi.yaml`은 `servers: https://zooearly.app/api/v1` 로 시작한다.
즉 **"앱 → 게이트웨이" 규약**이고, FastAPI가 받는 요청은 거기 없다.

명세서 §0.3에는 "접두사만 다르다"는 한 줄뿐이라, 실제로 어떤 헤더와
어떤 파트 이름으로 도착하는지는 적혀 있지 않았다. 그 빈칸을 메우는 문서다.

필드 이름·타입·enum 값은 `openapi.yaml`과 **완전히 같다.** 게이트웨이가 1:1로
미러링하기 때문이다. 그러니 스키마는 그쪽을 보고, 이 문서는 **전송 형식**을 보면 된다.

---

## 1. 엔드포인트 6개

**경로는 FastAPI 쪽 이름을 그대로 쓴다.** 게이트웨이가 설정으로 맞춘다 — 바꿔달라고 하지 않는다.

| 앱 → 게이트웨이 | **게이트웨이 → FastAPI** | 형식 |
|---|---|---|
| `POST /api/v1/ai/stt` | `POST /internal/v1/speech/transcribe` | multipart/form-data |
| `POST /api/v1/ai/tts` | `POST /internal/v1/speech/synthesize` | application/json |
| `POST /api/v1/ai/feedback` | `POST /internal/v1/feedback/expression` ⚠️ | application/json |
| `POST /api/v1/ai/pronunciation` | `POST /internal/v1/feedback/speaking` | multipart/form-data |
| `GET /api/v1/ai/pronunciation/sentences` | `GET /internal/v1/feedback/sentences` | 없음 (GET) |
| `POST /api/v1/ai/chat` | `POST /internal/v1/chat` (미사용) | multipart/form-data |

⚠️ `feedback/expression`은 **아직 FastAPI에 없는 경로**다. 4번 항목 참고.

경로가 바뀌면 게이트웨이의 `application.yml`만 고치면 된다 — 재배포도 앱 수정도 없다.

```yaml
inference:
  path:
    stt: /internal/v1/speech/transcribe
    tts: /internal/v1/speech/synthesize
    feedback: /internal/v1/feedback/expression
    pronunciation: /internal/v1/feedback/speaking
    sentences: /internal/v1/feedback/sentences
```

Base URL은 환경변수 `INFERENCE_BASE_URL`로 주입한다 (기본 `http://localhost:8000`).
인증 헤더는 없다 — 프로토타입 단계라 명세에 없다.

---

## 1-1. ★ 지금 맞춰야 할 것 — before / after

FastAPI 명세(`zooearly-fastapi-spec.md`)와 대조한 결과다.
**전부 응답 쪽이다.** 요청은 게이트웨이가 맞춘다.

| # | 항목 | 지금 (FastAPI) | 바꿀 것 | 왜 |
|---|---|---|---|---|
| 1 | **TTS 응답** | `audio/mpeg` 바이너리 | **base64 JSON** | 게이트웨이가 body를 `String`으로 받는다. **바이너리는 깨진다** |
| 2 | 성공 봉투 | `{"text": ...}` | `{"success": true, "data": {...}}` | 게이트웨이가 body를 그대로 앱에 넘긴다. 봉투가 없으면 앱이 못 읽는다 |
| 3 | 에러 (422/429만) | `{"detail": "..."}` | `{"success": false, "error": {code, message, field}}` | 이 둘만 body가 앱까지 간다 |
| 4 | 응답 필드명 | `translated_text`, `duration_sec` | `translation`, `durationSec` (camelCase) | 앱이 읽는 이름이다 |
| 5 | 응답 언어 코드 | `ko` / `vi` / `zh` | `KOREAN` / `VIETNAMESE` / `CHINESE` | 앱 계약의 enum |
| ~~6~~ | ~~**표현 교정 API**~~ | 없음 | **당분간 만들지 않는다** | 화면을 구현하지 않기로 했다 |
| 7 | **발음 채점 응답 봉투** | (2번과 동일) | `{success, data}` | `/feedback/speaking`에도 2번 규칙이 그대로 적용된다 |
| 8 | 문장 목록 응답 봉투 | 배열을 봉투 없이 그대로 | `{success, data: [...]}` | `GET /feedback/sentences`도 마찬가지. `data`가 배열이면 된다 |

**요청 쪽은 이미 맞다 — 고칠 필요 없다.** `/feedback/speaking`의 `sentence_id` 필드,
`GET /feedback/sentences`의 `sentence_id`/`category`/`text` 필드는 게이트웨이가 그대로
쓸 수 있는 모양으로 나온다(2026-08-24 개정). 이 문서 §3·§4는 그 형태를 그대로 반영한다.

**왜 게이트웨이가 못 고치나** — CLAUDE.md의 핵심 제약이다.

> FastAPI 응답 body를 파싱·가공하지 않는다. String으로 받아 그대로 통과시킨다

파싱하는 순간 FastAPI 스키마가 바뀔 때마다 게이트웨이를 재배포해야 한다.
그래서 **FastAPI 응답이 곧 앱이 받는 JSON**이고, 앱 계약이 기준이 될 수밖에 없다.

**요청 쪽(`audio_file`, `language_code`, `ko`/`vi`/`zh`)은 게이트웨이가 맞춘다.**
FastAPI 어댑터에서 같이 받아주면 총 작업량이 줄지만, 부담되면 게이트웨이가 변환한다.

### 6번 — 표현 교정은 당분간 만들지 않는다

`naturalSentence`·`naturalHint`·`highlightWords`를 만들어줄 API가 FastAPI에 없는데,
**그 화면("이렇게 말하면 더 자연스러워요")을 구현하지 않기로 했다.** 그래서 지금은 불필요하다.

게이트웨이의 `/api/v1/ai/feedback`과 `inference.path.feedback` 설정은 그대로 둔다.
안 부르면 아무 일도 일어나지 않고, 나중에 만들기로 하면 그때 붙이면 된다.

참고로 `feedback/speaking`(발음 채점)과는 다른 기능이다.

| | 표현 교정 | 발음 채점 |
|---|---|---|
| 무엇을 보나 | 어떤 **단어**를 골랐나 | 어떻게 **소리** 냈나 |
| 입력 | STT 텍스트 | 오디오 |
| 예 | "주세**여**" → "주세**요**" | 단어는 맞지만 `ㅈ` 발음이 약함 |
| FastAPI | **없음** | `feedback/speaking` ✓ |

번역(`text/translate`)도 **이 응답에 합쳐야 한다.** 앱이 두 번 부르면 화면이 늦게 뜬다.

---

## 2. 먼저 알아야 할 것 3가지

### ① multipart 요청에는 `Content-Length`가 없다

게이트웨이는 `Transfer-Encoding: chunked`로 보낸다. 캡처 원문:

```
POST /internal/v1/speech/transcribe
Content-Type: multipart/form-data;boundary=yPNU8nRDsHHHMi52i98bP5bAbwc3gj3xf6ff4f
User-Agent: Java/17.0.16
Transfer-Encoding: chunked          ← Content-Length 없음
```

FastAPI(Starlette)는 청크 요청을 정상 처리하므로 **그냥 쓰면 된다.**
다만 앞단에 nginx 같은 프록시를 두면 청크 업로드를 막지 않는지 확인해야 한다.

### ② 선택 필드는 "빈 값"이 아니라 **아예 안 온다** ★

앱이 `nativeLanguage`를 생략하면, 게이트웨이는 그 파트를 **만들지 않는다.**
빈 문자열도, `null` 문자열도 오지 않는다. 파트가 통째로 없다.

생략했을 때 실제로 도착한 파트 목록:

```
POST /ai/chat
  name="audio"      ← 있음
  name="scenario"   ← 있음
  name="history"    ← 있음
                    ← nativeLanguage 파트가 아예 없음

POST /ai/stt
  name="audio"      ← 있음
                    ← language 파트가 아예 없음
```

**여기서 가장 많이 터진다.** `Form(...)`로 필수 선언하면 422가 나면서 죽는다.
선택 필드는 반드시 `Form(None)`으로 받아야 한다.

### ③ JSON body는 앱이 보낸 바이트 그대로 온다

게이트웨이는 JSON을 파싱하거나 다시 만들지 않는다. 앱이 보낸 바이트를
**그대로 흘려보낸다.** 한글도 UTF-8 그대로 보존된다 (65바이트 → 65바이트 검증 완료).

키 순서, 공백, 들여쓰기도 앱이 보낸 그대로다. 그러니 **키 순서에 의존하지 말 것.**

---

## 3. 엔드포인트별 실제 도착 형태

아래는 전부 캡처 원문이다.

### POST /internal/v1/chat  (미사용)

```
Content-Type: multipart/form-data;boundary=...
Transfer-Encoding: chunked

--boundary
Content-Disposition: form-data; name="audio"; filename="ok.m4a"
Content-Type: audio/mp4                      ← 앱이 보낸 타입 그대로
Content-Length: 5000
<바이너리>
--boundary
Content-Disposition: form-data; name="scenario"
Content-Type: text/plain;charset=UTF-8
LUNCH
--boundary
Content-Disposition: form-data; name="history"
Content-Type: text/plain;charset=UTF-8
[{"role":"assistant","content":"불고기 많이 줄까?"}]
--boundary
Content-Disposition: form-data; name="nativeLanguage"
Content-Type: text/plain;charset=UTF-8
VIETNAMESE
--boundary
Content-Disposition: form-data; name="nickname"
Content-Type: text/plain;charset=UTF-8
민수
```

| 파트 | 필수 | 값 |
|---|---|---|
| `audio` | ✅ | m4a/wav/webm, 최대 10MB · **30초** (게이트웨이가 검증 후 통과시킨 것) |
| `scenario` | ✅ | `ARRIVAL` / `CLASS` / `LUNCH` / `DISMISSAL` |
| `history` | ✅ | JSON **문자열**. 없으면 `"[]"`. 형식 `[{"role":"user"\|"assistant","content":"..."}]` |
| `nativeLanguage` | — | `KOREAN` / `CHINESE` / `VIETNAMESE`. 생략 시 파트 없음 |
| `nickname` | ✅ | 아이 호칭(최대 20자). LLM이 말을 걸 때 쓴다. 항상 온다 |

`history`는 파싱된 객체가 아니라 **문자열**이다. FastAPI에서 `json.loads()` 해야 한다.

### POST /internal/v1/speech/transcribe

```
--boundary
Content-Disposition: form-data; name="audio"; filename="ok.m4a"
Content-Type: audio/mp4
Content-Length: 5000
<바이너리>
--boundary
Content-Disposition: form-data; name="language"
Content-Type: text/plain;charset=UTF-8
ko-KR
```

| 파트 | 필수 | 값 |
|---|---|---|
| `audio` | ✅ | 위와 동일 |
| `language` | — | **BCP-47 자유 문자열** (`ko-KR` 등). enum이 아니다. 생략 시 `ko-KR`로 처리 |

### POST /internal/v1/speech/synthesize

```
Content-Type: application/json
Content-Length: 65

{"text":"불고기 많이 줄까?","voice":"TEACHER","speed":0.9}
```

게이트웨이가 통과시키기 전에 검증하는 것 (여기까지 왔으면 이미 통과한 값이다):

| 필드 | 필수 | 게이트웨이 검증 |
|---|---|---|
| `text` | ✅ | 비어있지 않음, 200자 이하 |
| `voice` | — | `TEACHER` / `FRIEND` |
| `speed` | — | 0.5 ~ 1.5 |
| `language` | ✅ | `KOREAN` / `CHINESE` / `VIETNAMESE`. 게이트웨이가 검증하므로 항상 온다 |

> **`/ai/tts`는 한국어 전용이 아니다.** 피드백 화면이 한국어 문장과 모국어 번역을
> 각각 재생하므로, 같은 엔드포인트로 두 언어가 온다. `language`가 **필수**라
> 항상 값이 오니 그걸로 보이스·발음을 고르면 된다. 텍스트로 추측할 필요가 없다.
>
> 주의: `/ai/stt`의 `language`는 BCP-47 문자열(`ko-KR`)이고, 이쪽은 enum이다.

### POST /internal/v1/feedback/expression

```
Content-Type: application/json
Content-Length: 145

{"targetSentence":"많이 주세요.","recognizedText":"많이 주세여","scenario":"LUNCH","nativeLanguage":"VIETNAMESE","nickname":"민수"}
```

| 필드 | 필수 | 비고 |
|---|---|---|
| `targetSentence` | ✅ | 비어있지 않음 |
| `recognizedText` | ✅ | **`null`이 유효값이다.** 단 키는 반드시 있다 (STT 인식 실패 케이스) |
| `scenario` | — | 시나리오 enum |
| `nativeLanguage` | — | 언어 enum |
| `nickname` | ✅ | 아이 호칭(최대 20자). 피드백 문구에 쓴다. 항상 온다 |

### POST /internal/v1/feedback/speaking  — 발음 채점

게이트웨이가 실제로 보내는 파트 이름이다 (2026-08-24 FastAPI 명세 개정 반영 — `text`가
아니라 `sentence_id`를 받는 버전).

```
Content-Type: multipart/form-data
Transfer-Encoding: chunked

--boundary
Content-Disposition: form-data; name="audio"; filename="speech.m4a"
Content-Type: audio/mp4
<바이너리>
--boundary
Content-Disposition: form-data; name="sentenceId"
Content-Type: text/plain;charset=UTF-8
arrival_2
```

| 파트 | 필수 | 값 |
|---|---|---|
| `audio` | ✅ | 따라 말한 녹음. m4a/wav/webm, 최대 10MB · **30초** |
| `sentenceId` | ✅ | `GET /internal/v1/feedback/sentences`가 준 9개 값 중 하나. **camelCase다** — FastAPI 응답 예시의 `sentence_id`와 대소문자만 다르다는 점 주의 |

> **STT를 거치지 않는다.** 발음은 텍스트로 알 수 없어서 녹음을 그대로 보낸다.
> 게이트웨이는 오디오 형식·크기만 검증하고 통과시킨다. `sentenceId`가 유효한 값인지는
> 검증하지 않는다 — 그건 FastAPI가 자기 목록으로 판단한다 (422로 알려주면 된다).

**응답** — 어절 단위 z점수. 필드명만 camelCase로 맞춰주면 된다.

```json
{ "success": true, "data": {
    "sentenceId": "arrival_2",
    "sentence": "안녕! 우리 친하게 지내자",
    "targetWord": "지내자",
    "targetIndex": 2,
    "targetZ": -1.82,
    "words": [
      { "word": "안녕!", "z": 0.31,  "warn": false, "worstPhone": null },
      { "word": "지내자", "z": -1.82, "warn": true,  "worstPhone": "ㄴ" }
    ] } }
```

`sentence_id` → `sentenceId`, `target_word` → `targetWord`, `worst_phone` → `worstPhone`
처럼 **snake_case만 camelCase로 바꾸면 된다.** 나머지 구조는 지금 그대로다.

**`quizSentence` 필드는 없다** (2026-08-24 FastAPI 명세에서 빠졌다). 앱이
`sentence`를 공백으로 나눠 `targetIndex`번째를 빈칸으로 바꿔서 직접 만든다.
FastAPI·게이트웨이 둘 다 이 필드를 만들 필요가 없다.

**`targetWord: null`은 정상 응답이다.** 발음이 전부 기준(z ≥ -1.5) 이상이면 FastAPI가
`target_word: null`을 준다. 그대로 `targetWord: null`로 통과시키면 된다 — 에러가 아니다.

### GET /internal/v1/feedback/sentences  — 발음 연습 문장 목록

```
GET /internal/v1/feedback/sentences
```

body 없음, 인증 헤더 없음. 게이트웨이는 이 요청을 그대로 중계한다.

**응답** — FastAPI 원본은 배열을 봉투 없이 그대로 준다 (`zooearly-fastapi-spec.md` §5).
게이트웨이로 보낼 때는 다른 엔드포인트와 똑같이 `{success, data}`로 감싼다 — `data`가 배열이다.

```json
{ "success": true, "data": [
    { "sentenceId": "arrival_1", "category": "arrival", "text": "안녕 나도 만나서 반가워 !" },
    { "sentenceId": "arrival_2", "category": "arrival", "text": "안녕! 우리 친하게 지내자" }
  ] }
```

`sentence_id` → `sentenceId`만 camelCase로 바꾸면 된다. `category`·`text`는 그대로 쓴다.

---

## 4. FastAPI 시그니처 — 그대로 쓰면 된다

```python
from typing import Optional
from fastapi import FastAPI, File, Form, UploadFile
from pydantic import BaseModel

app = FastAPI()


@app.post("/internal/v1/chat")          # 미사용
async def chat(
    audio: UploadFile = File(...),
    scenario: str = Form(...),
    history: str = Form(...),            # JSON 문자열. json.loads() 필요
    nativeLanguage: Optional[str] = Form(None),   # 생략 시 파트 자체가 없다
    nickname: str = Form(...),                    # 필수 — 게이트웨이가 검증 후 항상 보낸다
):
    ...


@app.post("/internal/v1/speech/transcribe")
async def stt(
    audio: UploadFile = File(...),
    language: Optional[str] = Form(None),         # 생략 시 파트 자체가 없다
):
    ...


class TtsRequest(BaseModel):
    text: str
    voice: Optional[str] = "TEACHER"
    speed: Optional[float] = 0.9
    language: str                        # 필수 — KOREAN / CHINESE / VIETNAMESE


@app.post("/internal/v1/speech/synthesize")
async def tts(req: TtsRequest):
    ...


class FeedbackRequest(BaseModel):
    targetSentence: str
    recognizedText: Optional[str]        # null이 유효값
    scenario: Optional[str] = None
    nativeLanguage: Optional[str] = None
    nickname: str


@app.post("/internal/v1/feedback/expression")
async def feedback(req: FeedbackRequest):
    ...


@app.post("/internal/v1/feedback/speaking")
async def pronunciation(
    audio: UploadFile = File(...),
    sentenceId: str = Form(...),         # 필드명 주의 — text 도 targetSentence 도 아니다
):
    ...


@app.get("/internal/v1/feedback/sentences")
async def sentences():
    # 9개 고정 목록. 요청 파라미터 없음
    ...
```

필드 이름이 camelCase다 (`nativeLanguage`, `targetSentence`, `sentenceId`). snake_case로 바꾸면 안 된다.

---

## 5. 응답 규약 — FastAPI가 지켜야 할 것 ★

게이트웨이는 응답 body를 **파싱하지 않고 그대로 앱에 전달한다.**
따라서 **명세 §1.2 봉투를 FastAPI가 직접 만들어야 한다.**

### 성공 (200)

```json
{ "success": true, "data": { ... } }
```

`data` 안의 구조는 `openapi.yaml`의 각 엔드포인트 200 응답을 그대로 따르면 된다.
게이트웨이는 이 안을 들여다보지 않는다.

### STT 인식 실패는 **에러가 아니다**

아이가 우물거리거나 조용해서 못 알아들은 경우:

```json
{ "success": true, "data": { "text": null, "confidence": null } }
```

**200으로 내려야 한다.** 422가 아니다.
`/ai/chat`이면 `userText: null`. 422는 **STT 엔진 자체가 죽었을 때만** 쓴다.

### 에러 응답

| 상태 | 어떻게 되나 |
|---|---|
| **422** `STT_FAILED` | §1.2 포맷으로 만들면 **body 그대로 앱까지 간다** |
| **429** `RATE_LIMITED` | 위와 같음 |
| 그 외 4xx / 5xx | 게이트웨이가 **`502 AI_SERVER_ERROR`로 감싼다. body는 버려진다** |

422/429는 이 형식을 지켜야 앱이 읽을 수 있다:

```json
{ "success": false, "error": { "code": "STT_FAILED", "message": "...", "field": null } }
```

FastAPI 기본 에러 형식인 `{"detail": "..."}`로 내면 앱이 못 읽는다.
**422/429를 쓸 거면 반드시 위 봉투로 감싸야 한다.**

그 외 상태 코드는 어떤 형식으로 내든 앱에는 `502 AI_SERVER_ERROR`만 도착한다.
디버깅에 필요한 정보는 FastAPI 쪽 로그에 남겨야 한다 — 앱까지 전달되지 않는다.

---

## 6. 응답 시간 제한

게이트웨이가 기다려주는 시간이다. 넘기면 게이트웨이가 연결을 끊고
앱에 `504 AI_TIMEOUT`을 내려보낸다. **그 뒤 FastAPI가 응답해도 버려진다.**

| 엔드포인트 | 제한 |
|---|---|
| `/ai/chat` | **30초** (STT + LLM + TTS 3단이라 길게 잡음) |
| `/ai/stt` `/ai/tts` `/ai/feedback` `/ai/pronunciation` `/ai/pronunciation/sentences` | **15초** |
| 연결(TCP) | 3초 |

OpenAI 호출이 느릴 수 있으니 FastAPI 쪽에서도 자체 타임아웃을 이보다
짧게 걸어두면 좋다. 그래야 게이트웨이가 끊기 전에 의미 있는 에러를 만들 수 있다.

---

## 7. 붙기 전에 혼자 확인하는 법

FastAPI를 8000번에 띄운 뒤, 게이트웨이 없이 직접 때려보면 된다.

```bash
# stt — 선택 필드 없이 (가장 흔한 실패 케이스)
curl -X POST http://localhost:8000/internal/v1/speech/transcribe \
  -F "audio=@test.m4a;type=audio/m4a"

# 문장 목록 — 먼저 받아서 유효한 sentenceId를 확인한다
curl http://localhost:8000/internal/v1/feedback/sentences

# 발음 채점 — 위에서 받은 sentenceId 중 하나를 쓴다
curl -X POST http://localhost:8000/internal/v1/feedback/speaking \
  -F "audio=@test.m4a;type=audio/m4a" \
  -F "sentenceId=arrival_2"

# tts
curl -X POST http://localhost:8000/internal/v1/speech/synthesize \
  -H 'Content-Type: application/json' \
  -d '{"text":"불고기 많이 줄까?","language":"KOREAN"}'
```

그 다음 게이트웨이를 띄워 전체를 확인한다.

```bash
INFERENCE_BASE_URL=http://localhost:8000 ./gradlew bootRun
curl -X POST http://localhost:8080/api/v1/ai/stt -F "audio=@test.m4a"
curl http://localhost:8080/api/v1/ai/pronunciation/sentences
```

게이트웨이가 `502 AI_SERVER_ERROR`를 내면 FastAPI가 4xx/5xx를 냈다는 뜻이고,
`504 AI_TIMEOUT`이면 제한 시간을 넘겼다는 뜻이다. 원인은 FastAPI 로그를 봐야 한다.
