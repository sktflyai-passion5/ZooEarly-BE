# 쥬얼리 (ZooEarly) — API Gateway

> 레포: https://github.com/sktflyai-passion5/ZooEarly-BE

중도입국 초등 1~2학년 학교생활 적응 앱의 백엔드 게이트웨이.
React Native 앱과 FastAPI 추론 서버(STT / LLM / TTS) 사이의 **릴레이 전용 서버**다.

```
React Native App ──HTTPS/REST──▶ 이 서버 (Gateway) ──HTTP──▶ FastAPI ──▶ OpenAI API
 (UI/시나리오/게임/로컬 상태)      (검증·전달·에러 통일)      (STT/LLM/TTS)
```

이 서버가 하는 일은 세 가지뿐이다: **요청 검증, FastAPI로 전달, 에러 포맷 통일.**
DB 없음, 무상태(stateless). 비즈니스 로직을 여기에 추가하지 않는다.

## API

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/v1/ai/chat` | 음성 대화 (STT→LLM→TTS 통합) |
| POST | `/api/v1/ai/stt` | 음성 → 텍스트 |
| POST | `/api/v1/ai/tts` | 텍스트 → 음성 |
| POST | `/api/v1/ai/feedback` | 발화 피드백 생성 |

상세 명세: 프로젝트 문서 `zooearly-ai-api-spec.md` / Swagger: `zooearly-ai-openapi.yaml`
(yaml을 https://editor.swagger.io 에 붙여넣으면 UI로 볼 수 있다)

> 위 두 문서는 **앱 → 게이트웨이** 규약이다.
> **게이트웨이 → FastAPI** 규약은 [`docs/zooearly-gateway-to-fastapi.md`](docs/zooearly-gateway-to-fastapi.md)에 따로 있다.
> FastAPI 담당자에게는 그 문서를 주면 된다.

## 기술 스택

- Java 17 / Spring Boot 3.5 / Gradle 8.14
- 의존성: spring-web, spring-validation — **JPA·DB 없음 (의도된 것)**

## 실행

```bash
# FastAPI 주소는 환경변수로 주입 (기본값 http://localhost:8000)
export INFERENCE_BASE_URL=http://localhost:8000

./gradlew bootRun
```

빌드·테스트:

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew test       # 테스트만
```

동작 확인 (FastAPI 없이도 검증 로직까지는 확인 가능):

```bash
# 400 INVALID_PARAMETER가 §1.2 포맷으로 오면 정상
curl -X POST http://localhost:8080/api/v1/ai/tts \
  -H "Content-Type: application/json" -d '{}'
```

## 패키지 구조

```
src/main/java/com/zooearly/
├── ZooEarlyApplication.java
├── common/
│   ├── response/   ApiResponse(§1.2 래퍼), ErrorCode(§1.3)
│   └── exception/  GlobalExceptionHandler — 모든 에러를 공통 포맷으로
└── ai/
    ├── AiController.java      4개 엔드포인트
    ├── AiRelayService.java    검증 + 전달 (여기에 로직을 늘리지 말 것)
    └── client/InferenceClient.java  FastAPI 호출, 타임아웃(§0.4), 에러 변환
```

## 협업 규칙

### 브랜치 전략 — GitHub Flow

- `main` 은 항상 동작하는 상태를 유지한다. **main에 직접 푸시 금지.**
- 작업은 `feature/작업내용` 브랜치에서 하고 PR로 합친다.

```bash
git switch -c feature/tts-endpoint   # 브랜치 생성
# ... 작업 & 커밋 ...
git push -u origin feature/tts-endpoint
# GitHub에서 PR 생성 → 리뷰 → main에 머지 → 브랜치 삭제
```

### 커밋 컨벤션

`타입: 설명` 형식. 타입은 아래 중 하나.

| 타입 | 용도 | 예시 |
|---|---|---|
| `feat` | 기능 추가 | `feat: tts 엔드포인트 릴레이 구현` |
| `fix` | 버그 수정 | `fix: history 검증에서 빈 배열 허용` |
| `refactor` | 동작 변화 없는 정리 | `refactor: 검증 헬퍼 분리` |
| `docs` | 문서만 변경 | `docs: README 실행법 추가` |
| `test` | 테스트만 변경 | `test: chat 검증 실패 케이스 추가` |
| `chore` | 빌드·설정 | `chore: gradle 8.14로 업그레이드` |

### PR 규칙

- PR은 작게 — 엔드포인트 하나, 수정 하나 단위로.
- 템플릿(`.github/PULL_REQUEST_TEMPLATE.md`)의 체크리스트를 채운다.
- 최소 1명 리뷰 후 머지.

## 주의사항

- **API 키·비밀값을 커밋하지 않는다.** `application.yml`에는 `${환경변수}` 참조만 두고, 실제 값은 로컬 환경변수나 `application-local.yml`(.gitignore 됨)에 둔다.
- FastAPI 응답 body를 게이트웨이에서 파싱·가공하지 않는다. 가공하는 순간 FastAPI 스키마가 바뀔 때마다 이 서버도 재배포해야 한다.
- 에러 응답에 "틀렸어요"류 문구를 넣지 않는다 — 아이 화면 폴백 정책은 앱 담당.
