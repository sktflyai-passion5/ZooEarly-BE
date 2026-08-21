# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 자동으로 읽는 프로젝트 규칙이다.

## 프로젝트

중도입국 초등 1~2학년 학교생활 적응 앱 "쥬얼리(ZooEarly)"의 **백엔드 API 게이트웨이**.

```
React Native App ──HTTPS/REST──▶ 이 서버 (Spring) ──HTTP──▶ FastAPI ──▶ OpenAI API
```

- Java 17 / Spring Boot 3.5 / Gradle
- 상세 명세: `docs/zooearly-ai-api-spec.md` (사람용), `docs/zooearly-ai-openapi.yaml` (OpenAPI)

## 이 서버가 하는 일 — 딱 세 가지

1. **요청 검증** — 필수 파라미터, 오디오 포맷·용량. 잘못된 요청은 FastAPI까지 보내지 않고 400으로 끊는다
2. **전달** — 검증 통과한 요청을 FastAPI의 대응 경로로 넘기고, 응답을 **가공 없이** 되돌려준다
3. **에러 통일** — 어떤 실패든 앱에는 항상 공통 포맷으로 내려준다

## 절대 하지 말 것 ★

이 프로젝트에서 가장 중요한 제약이다. 아래를 어기면 설계가 무너진다.

- **DB를 붙이지 않는다.** JPA, 데이터소스, 엔티티, 리포지토리를 추가하지 않는다. 이 서버는 무상태(stateless)다
- **비즈니스 로직을 넣지 않는다.** 대화 이력 관리, 사용자 정보 조회, 별점 계산, 진행 상태 저장 — 전부 앱(React Native) 또는 FastAPI의 일이다
- **FastAPI 응답 body를 파싱·가공하지 않는다.** String으로 받아 그대로 통과시킨다. 파싱하는 순간 FastAPI 스키마가 바뀔 때마다 이 서버도 재배포해야 한다
- **인증·로그인을 추가하지 않는다.** 프로토타입 단계이며 명세에 없다
- **엔드포인트를 임의로 추가하지 않는다.** 4개(`chat`/`stt`/`tts`/`feedback`)가 전부다. 필요해 보이면 먼저 사람에게 확인한다

## 패키지 구조

```
src/main/java/com/zooearly/
├── ZooEarlyApplication.java
├── common/
│   ├── response/ApiResponse.java     성공/실패 공통 래퍼 (명세 §1.2)
│   ├── response/ErrorCode.java       에러 코드 enum (명세 §1.3)
│   └── exception/
│       ├── BusinessException.java              게이트웨이 검증 실패
│       ├── InferencePassthroughException.java   FastAPI 에러 그대로 통과 (422/429)
│       └── GlobalExceptionHandler.java          모든 에러를 공통 포맷으로 변환
└── ai/
    ├── AiController.java             4개 엔드포인트. 얇게 유지한다
    ├── AiRelayService.java           검증 로직은 대부분 여기
    └── client/InferenceClient.java   FastAPI 호출, 타임아웃, 에러 변환
```

새 검증 규칙은 `AiRelayService`에, 새 에러 코드는 `ErrorCode`에 추가한다.

## 엔드포인트

| 앱 → 이 서버 | → FastAPI | 형식 |
|---|---|---|
| `POST /api/v1/ai/chat` | `POST /ai/chat` | multipart (audio, scenario, history, nativeLanguage) |
| `POST /api/v1/ai/stt` | `POST /ai/stt` | multipart (audio, language) |
| `POST /api/v1/ai/tts` | `POST /ai/tts` | JSON |
| `POST /api/v1/ai/feedback` | `POST /ai/feedback` | JSON |

경로는 접두사만 다르게 1:1 미러링한다. 이 규칙을 깨지 않는다.

## 응답 포맷

성공 — FastAPI가 만든 것을 그대로 전달한다.

```json
{ "success": true, "data": { } }
```

실패 — 게이트웨이가 만든다.

```json
{ "success": false, "error": { "code": "AI_TIMEOUT", "message": "...", "field": null } }
```

에러 코드: `INVALID_PARAMETER` `UNSUPPORTED_AUDIO_FORMAT` `AUDIO_TOO_LARGE` `PAYLOAD_TOO_LARGE` `AI_SERVER_ERROR`(502) `AI_TIMEOUT`(504) `INTERNAL_ERROR`
FastAPI가 직접 만들어 통과시키는 것: `STT_FAILED`(422) `RATE_LIMITED`(429)

## 설계 계약 — 코드를 고칠 때 지킬 것

- **STT 인식 실패는 에러가 아니다.** `text: null` / `userText: null` 로 `200`이 나간다. 422는 STT 엔진 자체가 죽었을 때만
- **에러 메시지에 "틀렸어요"류 문구를 쓰지 않는다.** 사용자가 초등 1~2학년이다. 화면 문구는 앱이 담당하지만 서버 메시지도 이 톤을 지킨다
- **오디오 원본을 저장하지 않는다.** `/chat` 응답에 `X-Audio-Retention: none` 헤더를 유지한다
- **타임아웃**: chat 30초(STT+LLM+TTS 3단), 나머지 15초, 연결 3초. `application.yml`에서 관리한다

## 명령어 (Windows)

```cmd
gradlew.bat build          REM 빌드 + 테스트
gradlew.bat bootRun        REM 실행 (8080 포트)
gradlew.bat test           REM 테스트만
```

Mac/Linux는 `./gradlew`.

FastAPI 주소는 환경변수 `INFERENCE_BASE_URL`로 주입한다 (기본값 `http://localhost:8000`).

## 코드 스타일

- 주석은 한국어로. **무엇을 하는지가 아니라 왜 그런지**를 적는다
- 명세의 조항을 근거로 삼을 때는 `// 명세 §1.4` 처럼 참조를 남긴다
- 검증 실패는 `BusinessException`을 던진다. 컨트롤러에서 try-catch로 처리하지 않는다 — `GlobalExceptionHandler`가 받는다

## Git

- `main`에 직접 푸시하지 않는다. `feature/작업내용` 브랜치 → PR → 머지
- 커밋 메시지: `feat:` `fix:` `refactor:` `docs:` `test:` `chore:` 중 하나로 시작
- **API 키·비밀값을 커밋하지 않는다.** `application.yml`에는 `${환경변수}` 참조만 둔다

## 작업할 때 부탁

- 코드를 생성하거나 수정하면 **왜 그렇게 했는지 한국어로 짧게 설명**해달라. 이 프로젝트는 팀 발표가 있어서 담당자가 코드를 설명할 수 있어야 한다
- 명세(`docs/zooearly-ai-api-spec.md`)와 어긋나는 구현이 필요해 보이면, 먼저 그 사실을 알리고 확인을 받는다
- 위 "절대 하지 말 것"에 걸리는 요청을 받으면, 그대로 따르지 말고 왜 문제인지 먼저 말해달라
