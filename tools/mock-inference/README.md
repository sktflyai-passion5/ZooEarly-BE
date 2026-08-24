# 가짜 추론 서버 (mock inference)

진짜 FastAPI가 준비되기 전에 **앱 ↔ 게이트웨이 ↔ 추론 서버** 전 구간을 돌려보기 위한 도구다.

> **게이트웨이 코드가 아니다.** `src/`와 무관한 개발용 하네스이고, 진짜 FastAPI가
> 뜨면 이 폴더는 지워도 된다. 추론을 하지 않으며 응답은 전부 고정값이다.

## 왜 필요한가

FastAPI가 없으면 게이트웨이는 연결에 실패해 **모든 요청에 `502 AI_SERVER_ERROR`** 를 준다.
검증 실패(400)는 확인할 수 있지만 정상 화면은 하나도 못 본다.
이 서버가 8000번에 앉아 명세대로 생긴 200 응답을 돌려준다.

## 실행

파이썬 표준 라이브러리만 쓴다. `pip install`이 필요 없다.

```bash
# 1) 가짜 추론 서버 (8000)
python tools/mock-inference/mock_server.py

# 2) 게이트웨이 (8080) — 다른 터미널에서
gradlew.bat bootRun          REM Windows
./gradlew bootRun            # Mac/Linux
```

게이트웨이는 기본으로 `http://localhost:8000`을 본다. 포트를 바꿨다면:

```bash
set INFERENCE_BASE_URL=http://localhost:9000     REM Windows
export INFERENCE_BASE_URL=http://localhost:9000  # Mac/Linux
```

살아있는지 확인:

```bash
curl http://localhost:8000/
# 아는 경로 목록이 나온다 (FastAPI 실제 경로 + 옛 /ai/* 둘 다 받는다)
```

## 앱에서 접속할 주소

앱은 `localhost`로 게이트웨이를 못 찾는다. 실행 환경마다 다르다.

| 환경 | 주소 |
|---|---|
| Android 에뮬레이터 | `http://10.0.2.2:8080` |
| iOS 시뮬레이터 | `http://localhost:8080` |
| 실기기 (같은 Wi-Fi) | `http://<서버PC의 LAN IP>:8080` |

`10.0.2.2`는 Android 에뮬레이터가 호스트 PC를 가리키는 특수 주소다.
실기기는 서버 PC와 **같은 Wi-Fi**에 있어야 하고, Windows 방화벽에서 8080 인바운드가 열려 있어야 한다.

### 평문 HTTP 허용 (개발 중에만)

Android 9+와 iOS는 `https`가 아닌 통신을 기본 차단한다. 개발 빌드에만 예외를 준다.

- **Android** — `android/app/src/main/AndroidManifest.xml`의 `<application>`에
  `android:usesCleartextTraffic="true"`
- **iOS** — `ios/<앱>/Info.plist`에 `NSAppTransportSecurity` → `NSAllowsLocalNetworking`

> 배포 빌드에는 넣지 않는다. 실제 서버는 HTTPS를 쓴다 (명세 §0 프로토콜: HTTPS only).

## 돌려주는 값

전부 고정값이다. 형식만 명세와 같다.

| 엔드포인트 | 응답 |
|---|---|
| `/internal/v1/feedback/speaking` | 발음 채점. 세 번째 어절을 "가장 약한 곳"으로 고정해 `targetWord`·`quizSentence` 생성 |
| `/ai/chat` | `userText` 고정, `aiText`에 **보낸 `nickname`을 넣어 되돌려준다**, 무음 mp3 1초 |
| `/ai/stt` | `text: "많이 주세여"`, `confidence: 0.92` |
| `/ai/tts` | 무음 mp3 1초 |
| `/ai/feedback` | `matched: true` 피드백. `nativeLanguage`가 `KOREAN`이면 `translation: null` |

**mp3는 진짜 무음 파일이다.** 앱의 재생 배선을 확인할 수 있다 — 소리는 안 나지만
플레이어가 파일을 받아 재생 완료까지 가는지 볼 수 있다.

`nickname`을 `aiText`에 되돌려주므로 **앱이 값을 제대로 보내고 있는지 화면에서 바로 확인**된다.

## 에러 화면 테스트

앱은 어떤 에러에서도 아이에게 "오류"를 보여주지 않고 **"괜찮아, 다시 해볼까?"로 폴백**해야 한다 (명세 §0.4).
그 화면을 보려면 에러를 일부러 내야 한다.

**텍스트나 오디오 파일명에 아래 토큰을 넣으면** 해당 상황이 재현된다.

| 토큰 | 재현되는 상황 | 앱이 받는 것 |
|---|---|---|
| `__stt_fail__` | STT 엔진 자체가 죽음 | `422 STT_FAILED` |
| `__rate_limit__` | OpenAI 쿼터 초과 | `429 RATE_LIMITED` |
| `__server_err__` | FastAPI 5xx | `502 AI_SERVER_ERROR` |
| `__slow__` | 추론이 40초 걸림 | `504 AI_TIMEOUT` (chat 30초 / 그 외 15초 뒤) |

```bash
# 예) TTS에서 타임아웃 화면 보기
curl -X POST http://localhost:8080/api/v1/ai/tts \
  -H 'Content-Type: application/json' \
  -d '{"text":"__slow__","language":"KOREAN"}'
# 15초 뒤 → {"success":false,"error":{"code":"AI_TIMEOUT",...}}

# 예) STT 실패 화면 보기 — 파일명에 토큰을 넣는다
cp voice.m4a __stt_fail__.m4a
curl -X POST http://localhost:8080/api/v1/ai/stt -F "audio=@__stt_fail__.m4a"
```

> **인식 실패는 에러가 아니다.** 아이가 우물거려서 못 알아들은 경우는 `200` + `text: null`이다.
> `__stt_fail__`은 **엔진이 죽었을 때**를 재현하는 것이라 성격이 다르다 (명세 §3).

## 확인해볼 것

앱을 붙이고 이 순서로 보면 배선이 다 맞는지 확인된다.

1. **`nickname`이 전달되나** — 대화 응답에 아이 이름이 나오면 성공
2. **오디오가 재생되나** — 무음이지만 재생 완료 이벤트가 뜨는지
3. **모국어 음성** — 피드백 화면 아랫상자 탭 시 `language`를 넣어 보내는지
   (안 넣으면 `400 INVALID_PARAMETER` field=language)
4. **에러 폴백** — 위 토큰 4개로 "괜찮아, 다시 해볼까?" 화면이 뜨는지
5. **서버 콘솔** — 앱이 실제로 보낸 필드가 그대로 찍힌다. 값이 이상하면 여기서 바로 보인다

## 한계

- **진짜 추론이 아니다.** 뭐라고 말하든 STT 결과는 `"많이 주세여"` 고정이다
- **소리가 안 난다.** 무음 mp3다. 실제 음성은 FastAPI가 붙어야 들린다
- 앱 화면 흐름과 통신 배선 확인용이며, **AI 품질 확인에는 쓸 수 없다**
