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

## 1. 엔드포인트 4개

| 앱 → 게이트웨이 | **게이트웨이 → FastAPI** | 형식 |
|---|---|---|
| `POST /api/v1/ai/chat` | **`POST /ai/chat`** | multipart/form-data |
| `POST /api/v1/ai/stt` | **`POST /ai/stt`** | multipart/form-data |
| `POST /api/v1/ai/tts` | **`POST /ai/tts`** | application/json |
| `POST /api/v1/ai/feedback` | **`POST /ai/feedback`** | application/json |

`/api/v1` 접두사가 **없다.** FastAPI는 `/ai/...`로 받으면 된다.

Base URL은 게이트웨이의 환경변수 `INFERENCE_BASE_URL`로 주입한다 (기본 `http://localhost:8000`).
인증 헤더는 없다 — 프로토타입 단계라 명세에 없다.

---

## 2. 먼저 알아야 할 것 3가지

### ① multipart 요청에는 `Content-Length`가 없다

게이트웨이는 `Transfer-Encoding: chunked`로 보낸다. 캡처 원문:

```
POST /ai/chat
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

### POST /ai/chat

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
```

| 파트 | 필수 | 값 |
|---|---|---|
| `audio` | ✅ | m4a/wav/webm, 최대 10MB (게이트웨이가 검증 후 통과시킨 것) |
| `scenario` | ✅ | `ARRIVAL` / `CLASS` / `LUNCH` / `DISMISSAL` |
| `history` | ✅ | JSON **문자열**. 없으면 `"[]"`. 형식 `[{"role":"user"\|"assistant","content":"..."}]` |
| `nativeLanguage` | — | `KOREAN` / `CHINESE` / `VIETNAMESE`. 생략 시 파트 없음 |

`history`는 파싱된 객체가 아니라 **문자열**이다. FastAPI에서 `json.loads()` 해야 한다.

### POST /ai/stt

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

### POST /ai/tts

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

### POST /ai/feedback

```
Content-Type: application/json
Content-Length: 145

{"targetSentence":"많이 주세요.","recognizedText":"많이 주세여","scenario":"LUNCH","nativeLanguage":"VIETNAMESE"}
```

| 필드 | 필수 | 비고 |
|---|---|---|
| `targetSentence` | ✅ | 비어있지 않음 |
| `recognizedText` | ✅ | **`null`이 유효값이다.** 단 키는 반드시 있다 (STT 인식 실패 케이스) |
| `scenario` | — | 시나리오 enum |
| `nativeLanguage` | — | 언어 enum |

---

## 4. FastAPI 시그니처 — 그대로 쓰면 된다

```python
from typing import Optional
from fastapi import FastAPI, File, Form, UploadFile
from pydantic import BaseModel

app = FastAPI()


@app.post("/ai/chat")
async def chat(
    audio: UploadFile = File(...),
    scenario: str = Form(...),
    history: str = Form(...),            # JSON 문자열. json.loads() 필요
    nativeLanguage: Optional[str] = Form(None),   # 생략 시 파트 자체가 없다
):
    ...


@app.post("/ai/stt")
async def stt(
    audio: UploadFile = File(...),
    language: Optional[str] = Form(None),         # 생략 시 파트 자체가 없다
):
    ...


class TtsRequest(BaseModel):
    text: str
    voice: Optional[str] = "TEACHER"
    speed: Optional[float] = 0.9


@app.post("/ai/tts")
async def tts(req: TtsRequest):
    ...


class FeedbackRequest(BaseModel):
    targetSentence: str
    recognizedText: Optional[str]        # null이 유효값
    scenario: Optional[str] = None
    nativeLanguage: Optional[str] = None


@app.post("/ai/feedback")
async def feedback(req: FeedbackRequest):
    ...
```

필드 이름이 camelCase다 (`nativeLanguage`, `targetSentence`). snake_case로 바꾸면 안 된다.

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
| `/ai/stt` `/ai/tts` `/ai/feedback` | **15초** |
| 연결(TCP) | 3초 |

OpenAI 호출이 느릴 수 있으니 FastAPI 쪽에서도 자체 타임아웃을 이보다
짧게 걸어두면 좋다. 그래야 게이트웨이가 끊기 전에 의미 있는 에러를 만들 수 있다.

---

## 7. 붙기 전에 혼자 확인하는 법

FastAPI를 8000번에 띄운 뒤, 게이트웨이 없이 직접 때려보면 된다.

```bash
# stt — 선택 필드 없이 (가장 흔한 실패 케이스)
curl -X POST http://localhost:8000/ai/stt -F "audio=@test.m4a;type=audio/m4a"

# chat
curl -X POST http://localhost:8000/ai/chat \
  -F "audio=@test.m4a;type=audio/m4a" \
  -F "scenario=LUNCH" \
  -F 'history=[]'

# tts
curl -X POST http://localhost:8000/ai/tts \
  -H 'Content-Type: application/json' \
  -d '{"text":"불고기 많이 줄까?"}'

# feedback — recognizedText가 null인 케이스
curl -X POST http://localhost:8000/ai/feedback \
  -H 'Content-Type: application/json' \
  -d '{"targetSentence":"많이 주세요.","recognizedText":null}'
```

그 다음 게이트웨이를 띄워 전체를 확인한다.

```bash
INFERENCE_BASE_URL=http://localhost:8000 ./gradlew bootRun
curl -X POST http://localhost:8080/api/v1/ai/stt -F "audio=@test.m4a"
```

게이트웨이가 `502 AI_SERVER_ERROR`를 내면 FastAPI가 4xx/5xx를 냈다는 뜻이고,
`504 AI_TIMEOUT`이면 제한 시간을 넘겼다는 뜻이다. 원인은 FastAPI 로그를 봐야 한다.
