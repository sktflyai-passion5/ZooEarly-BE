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

### 문서

| 문서 | 누구를 위한 것 |
|---|---|
| [`docs/zooearly-ai-api-spec.md`](docs/zooearly-ai-api-spec.md) | **앱 → 게이트웨이** 규약 (사람용). 맨 앞에 **변경 이력** 표가 있다 |
| [`docs/zooearly-ai-openapi.yaml`](docs/zooearly-ai-openapi.yaml) | 같은 규약의 OpenAPI. https://editor.swagger.io 에 붙여넣으면 UI로 본다 |
| [`docs/zooearly-ai-api.types.ts`](docs/zooearly-ai-api.types.ts) | 위 yaml에서 **자동 생성**된 TS 타입. 손으로 고치지 않는다 |
| [`docs/zooearly-gateway-to-fastapi.md`](docs/zooearly-gateway-to-fastapi.md) | **게이트웨이 → FastAPI** 규약. FastAPI 담당자에게 이걸 주면 된다 |
| [`docs/zooearly-screen-api-map.md`](docs/zooearly-screen-api-map.md) | **화면의 어떤 버튼이 어떤 API를 부르는지**. 앱 담당자와 기획용 |

계약을 바꿀 때는 **yaml을 고치고 → types.ts를 재생성하고 → 버전과 변경 이력을 올린다.**

```bash
npx openapi-typescript@7 docs/zooearly-ai-openapi.yaml -o docs/zooearly-ai-api.types.ts
```

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

동작 확인:

```bash
# 400 INVALID_PARAMETER가 §1.2 포맷으로 오면 정상
curl -X POST http://localhost:8080/api/v1/ai/tts \
  -H "Content-Type: application/json" -d '{}'
```

### FastAPI 없이 전 구간 돌려보기

진짜 추론 서버가 없으면 모든 요청이 `502 AI_SERVER_ERROR`로 떨어져 정상 화면을 볼 수 없다.
`tools/mock-inference/`의 가짜 추론 서버를 8000번에 띄우면 명세대로 생긴 200 응답이 온다.

```bash
python tools/mock-inference/mock_server.py    # 터미널 1 (표준 라이브러리만 씀)
./gradlew bootRun                              # 터미널 2
```

에러 화면 테스트용으로 강제 에러 토큰(`__slow__` `__stt_fail__` 등)도 지원한다.
자세한 건 [`tools/mock-inference/README.md`](tools/mock-inference/README.md).

> 게이트웨이 코드가 아니다. `src/`와 무관한 개발용 도구이고, 진짜 FastAPI가 뜨면 폴더째 지워도 된다.

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

### 브랜치 전략

상시 유지하는 브랜치는 `main`과 `dev` 둘뿐이다. 나머지는 전부 임시다.

| 브랜치 | 역할 | 규칙 |
|---|---|---|
| `main` | 시연·제출 가능한 안정판 | 직접 푸시 금지. `dev`에서만 PR로 들어온다 |
| `dev` | **기본 브랜치**, 개발 통합 지점 | 직접 푸시 금지. `feature/*`에서 PR로 들어온다 |
| `feature/작업내용` | 작업용 임시 | `dev`에서 따고 `dev`로 PR. **머지되면 즉시 삭제** |

```bash
git switch dev && git pull            # 항상 dev에서 시작
git switch -c feature/tts-endpoint
# ... 작업 & 커밋 ...
git push -u origin feature/tts-endpoint
# GitHub에서 PR 생성 (base: dev) → CI 통과 → 머지 → 브랜치 삭제
```

발표·배포 시점에만 `dev` → `main` PR을 올린다.
`main`을 비워두지 않는다 — 항상 "그 시점에 돌아가는 버전"이어야 안전판이 된다.

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
