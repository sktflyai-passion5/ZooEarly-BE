# 쥬얼리 (ZooEarly) — 백엔드 개발 가이드

> **v1.0 · 2026-08-21**
> GitHub 세팅 → VS Code 개발 환경 → 개발 흐름 → Azure App Service 배포
> 대상: Spring Boot API Gateway 담당자

---

## 0. 전체 그림

### 0.1 지금 만드는 것

```
React Native App ──HTTPS/REST──▶ Spring Gateway ──HTTP──▶ FastAPI ──▶ OpenAI API
   (프론트 담당)                    ★ 내 담당              (다른 팀원 담당)
```

**게이트웨이가 하는 일은 세 가지뿐이다.** 요청 검증 → FastAPI로 전달 → 에러 포맷 통일.
DB 없음, 로그인 없음, 비즈니스 로직 없음. 이게 이 프로젝트의 핵심 설계 결정이다.

### 0.2 전체 로드맵

| 단계 | 하는 일 | 예상 소요 |
|---|---|---|
| 1 | 개발 환경 설치 (JDK, VS Code, Git) | 1시간 |
| 2 | GitHub 레포 생성 + 초기 세팅 | 30분 |
| 3 | 로컬에서 실행·테스트 | 30분 |
| 4 | 기능 개발 (브랜치 → PR → 머지) | 반복 |
| 5 | Azure App Service 배포 | 1~2시간 (첫 배포) |

### 0.3 미리 알아둘 용어

| 용어 | 쉬운 설명 |
|---|---|
| **JDK** | 자바 프로그램을 만들고 실행하는 도구 모음. 없으면 아무것도 안 됨 |
| **Gradle** | 라이브러리를 자동으로 받아오고 빌드해주는 도구. `build.gradle`이 설정 파일 |
| **jar 파일** | 프로젝트 전체를 하나로 압축한 실행 파일. 배포할 때 이것만 서버에 올린다 |
| **빌드(build)** | 소스코드 → 실행 가능한 jar로 변환하는 것 |
| **App Service** | Azure가 관리해주는 웹 앱 실행 환경. jar만 올리면 서버 관리 없이 24시간 돌아간다 |
| **az CLI** | 터미널에서 Azure 리소스를 만들고 배포하는 명령어 도구 |
| **브랜치** | 코드의 평행세계. 작업하다 망해도 원본은 안전하다 |

---

## 1. 개발 환경 설치

### 1.1 VS Code로 Spring Boot 개발, 괜찮은가?

**결론: 괜찮다.** Microsoft 공식 문서가 "Spring Boot 개발에 이상적인 가벼운 개발 환경"이라고 명시하고 있고, 확장팩 두 개만 설치하면 IntelliJ에서 하는 일의 대부분이 가능하다.

| 기능 | VS Code | 비고 |
|---|---|---|
| 코드 자동완성 (Spring 전용) | ✅ | Spring Boot Tools가 담당 |
| 실행 / 디버깅 (F5) | ✅ | 중단점 찍고 변수 확인 가능 |
| `application.yml` 자동완성 | ✅ | 오타로 설정이 안 먹는 사고 방지 |
| Spring Boot Dashboard | ✅ | 버튼으로 서버 시작·중지 |
| Gradle 연동 | ✅ | |
| 무거운 리팩터링 도구 | △ | IntelliJ Ultimate보다 약함 |

**IntelliJ Community와 비교하면**: 개인 취향 차이 수준이다. VS Code는 가볍고 프론트(React Native)와 같은 편집기를 쓸 수 있다는 장점이 있고, IntelliJ는 자바 전용 도구가 더 촘촘하다. **팀 프로젝트 규모에서는 VS Code로 충분하다.**

### 1.2 설치 순서

**① JDK 17 설치**

이미 Java 17을 쓰고 있다면 건너뛰어도 된다. 확인:

```bash
java -version
```

`openjdk version "17.x.x"` 이 나오면 OK. 안 나오면 [Eclipse Temurin JDK 17](https://adoptium.net/temurin/releases/?version=17) 에서 설치한다.

> **왜 17인가.** Spring Boot 3.x의 최소 요구 버전이 Java 17이다. 21도 되지만 팀원 전부가 같은 버전을 쓰는 게 중요하다 — 버전이 다르면 "내 컴퓨터에선 되는데" 사고가 난다.

**② VS Code 설치**

[code.visualstudio.com](https://code.visualstudio.com/) 에서 설치.

**③ 확장팩 2개 설치**

VS Code를 열고:

1. 왼쪽 세로 막대(사이드바)에서 **네모 4개가 조각난 모양**의 아이콘을 클릭한다. (단축키 `Ctrl+Shift+X`, Mac은 `Cmd+Shift+X`)
2. 맨 위 검색창에 아래 이름을 입력한다
3. 검색 결과 첫 번째 항목의 **Install** 버튼을 누른다
4. 두 번째 것도 같은 방법으로 설치한다

| 검색어 | 게시자 확인 | 역할 |
|---|---|---|
| `Extension Pack for Java` | **Microsoft** | 자바 개발의 기본. 이게 없으면 아무것도 안 됨 |
| `Spring Boot Extension Pack` | **VMware** | Spring 전용 자동완성·대시보드 |

> **게시자 이름을 꼭 확인한다.** 비슷한 이름의 다른 확장이 많다. Microsoft / VMware가 맞는지 보고 설치할 것.
>
> 이 둘은 각각 여러 확장을 한 번에 설치하는 **묶음(pack)** 이라, 이 둘만 설치하면 아래 것들이 자동으로 함께 깔린다: Language Support for Java, Debugger for Java, Test Runner, Maven, Project Manager, Gradle, Spring Boot Tools, Spring Initializr, Spring Boot Dashboard.

**④ 설치 확인**

설치가 끝나면 VS Code를 한 번 껐다 켠다. 프로젝트 폴더를 열었을 때 아래 두 가지가 보이면 정상이다.

- 왼쪽 사이드바에 **스프링 잎사귀 아이콘** 이 생김 (Spring Boot Dashboard)
- `.java` 파일을 열면 **글자에 색이 입혀지고**, 메서드 위에 `Run | Debug` 라는 작은 글씨가 뜸

> **아무것도 안 보이고 빨간 줄만 잔뜩이면** 아직 프로젝트를 읽는 중이다. 화면 하단 상태바에 진행 표시가 사라질 때까지 1~2분 기다린다.

**⑤ Git 설치**

```bash
git --version
```

안 나오면 [git-scm.com](https://git-scm.com/downloads) 에서 설치한다. 설치 후 최초 1회 본인 정보를 등록한다.

```bash
git config --global user.name "본인이름"
git config --global user.email "본인@이메일.com"
```

> **이 이메일은 GitHub 계정 이메일과 같아야 한다.** 다르면 커밋에 내 프로필 사진이 안 뜨고 잔디(contribution)도 안 심긴다.

---

## 2. GitHub 세팅

### 2.1 레포 정보

이미 팀 조직에 레포가 만들어져 있다. (2026-08-21 기준 **비어 있는 상태**)

| 항목 | 값 |
|---|---|
| 레포 주소 | https://github.com/sktflyai-passion5/ZooEarly-BE |
| 조직(Organization) | `sktflyai-passion5` |
| 레포 이름 | `ZooEarly-BE` |
| 현재 상태 | 비어 있음 — 첫 푸시를 기다리는 중 |

> **비어 있는 레포라 첫 푸시가 깔끔하게 들어간다.** GitHub에서 README나 .gitignore를 나중에 추가하지 말 것 — 이미 프로젝트에 들어 있어서 충돌 원인이 된다.

### 2.2 프로젝트 올리기

#### 먼저 — 받은 zip이 뭔가

`zooearly-gateway.zip`은 **Spring 프로젝트 파일들을 압축해둔 것**이다. 안에 `build.gradle`, `src/`, `README.md` 등이 들어 있다. 압축을 푼다 = 그 파일들을 꺼낸다.

**꺼낸 파일을 어디에 둘지는 완전히 자유다.** 바탕화면이든 `문서`든 `C:\dev\`든 상관없고, 폴더 이름도 바꿔도 된다. **위치와 폴더명은 빌드에 아무 영향을 주지 않는다.**

#### 준비 — 터미널 여는 법

아래 명령들은 **터미널(명령어를 입력하는 검은 창)** 에 친다. VS Code 안에서 열어도 되고, 컴퓨터 기본 터미널을 써도 된다. **같은 명령이 똑같이 동작한다.**

**VS Code에서 열기**

- 상단 메뉴 `Terminal` → `New Terminal`
- 또는 단축키 `` Ctrl + ` `` (백틱 = 숫자 1 왼쪽, Tab 위에 있는 키. Mac도 동일)

**컴퓨터 기본 터미널**

- **Mac**: `Cmd + Space` → `터미널` 검색
- **Windows**: 시작 메뉴 → `명령 프롬프트` 또는 `PowerShell`

> **Windows에서 VS Code 터미널의 기본 셸은 PowerShell이다.** 아래 명령들(`dir`, `gradlew.bat`, `git ...`)은 PowerShell에서도 그대로 동작하므로 신경 쓰지 않아도 된다.
>
> 혹시 명령이 이상하게 동작하면, 터미널 우측 `+` 옆 **∨(아래 화살표)** → `Command Prompt` 를 선택해 cmd 창을 새로 열면 된다.

아래는 **Windows 기준**이며, 코드를 `C:\Users\User\Desktop\skflyai` 에 두는 경우다. 다른 위치를 쓰려면 이 경로만 바꾸면 된다.

#### ① 압축 풀기

`zooearly-gateway.zip` 을 **`C:\Users\User\Desktop\skflyai`** 안에 푼다.
(zip 파일 우클릭 → `압축 풀기` → 대상 폴더를 `C:\Users\User\Desktop\skflyai` 로 지정)

#### ② 폴더 이름 바꾸기

압축을 풀면 `zooearly-gateway` 폴더가 생긴다. 이걸 **`ZooEarly-BE`** 로 이름을 바꾼다.
(폴더 우클릭 → `이름 바꾸기`)

결과가 이렇게 되면 된다.

```
C:\Users\User\Desktop\skflyai\ZooEarly-BE\
    ├── build.gradle
    ├── gradlew.bat
    ├── README.md
    ├── settings.gradle
    └── src\
```

> **이름을 레포와 맞추는 이유는 헷갈리지 않기 위해서일 뿐이다.** 폴더명은 빌드에 아무 영향이 없으니 다른 이름이어도 동작은 한다.

#### ③ 파일이 제대로 들어갔는지 확인

**압축 프로그램에 따라 폴더가 한 겹 더 생기는 경우가 있다.** (`ZooEarly-BE\zooearly-gateway\build.gradle` 처럼) 이러면 빌드가 안 되므로 확인이 필요하다.

VS Code에서 `File` → `Open Folder` → `C:\Users\User\Desktop\skflyai\ZooEarly-BE` 를 연다.
터미널을 열고(`` Ctrl + ` ``) 확인한다.

```cmd
dir /a
```

**`build.gradle`, `gradlew.bat`, `src`, `.gitignore`, `.github` 가 바로 보이면 정상이다.**

- `zooearly-gateway` 폴더 하나만 덩그러니 보이면 → 한 겹 더 들어간 것이다. 그 안의 내용물을 전부 위로 꺼낸다
- `.gitignore` 와 `.github` 가 안 보이면 → 압축이 제대로 안 풀린 것이다. 다시 푼다

#### ④ 빌드 확인

```cmd
gradlew.bat build
```

`BUILD SUCCESSFUL` 이 나와야 한다. **여기서 실패하면 다음 단계로 넘어가지 말고 먼저 해결한다.**

#### ⑤ GitHub에 올리기

> **"main에 직접 푸시하지 말라면서요?"** — 맞다. 하지만 **첫 푸시만 예외다.**
>
> 지금 레포는 완전히 비어 있어서 **main 브랜치 자체가 없다.** PR은 "이 브랜치를 main에 합쳐달라"는 요청이므로, 합칠 대상인 main이 없으면 PR을 만들 수가 없다. 첫 푸시가 그 main을 만들어주는 역할이다.
>
> ```
> ① 첫 푸시 → main 생성 + 코드 업로드      ← 지금. 직접 푸시가 맞다
> ② 브랜치 보호 설정 (2.3)                 ← 이때부터 main 잠금
> ③ 이후 모든 작업 → 브랜치 + PR (4.1)     ← 여기부터 규칙 적용
> ```
>
> **②를 ①보다 먼저 하면 첫 푸시가 막혀서 아무것도 못 올린다.** 그래서 이 순서다.

```cmd
git init -b main
git add -A
git commit -m "chore: 프로젝트 초기 세팅"
git remote add origin https://github.com/sktflyai-passion5/ZooEarly-BE.git
git push -u origin main
```

푸시가 끝나면 https://github.com/sktflyai-passion5/ZooEarly-BE 를 새로고침해서 파일이 올라갔는지 확인한다.

> **push할 때 비밀번호를 물으면** GitHub 비밀번호가 아니라 **Personal Access Token**이 필요하다. GitHub → `Settings` → `Developer settings` → `Personal access tokens` → `Tokens (classic)` → `Generate new token` → `repo` 체크 → 생성된 토큰을 비밀번호 자리에 붙여넣는다. (한 번만 보여주니 메모해둘 것)
>
> 요즘은 브라우저 로그인 창이 대신 뜨기도 한다. 그러면 그냥 로그인하면 된다.

<details>
<summary><b>Mac / Linux를 쓰는 팀원이라면</b></summary>

```bash
cd ~/Desktop            # 원하는 위치
unzip ~/Downloads/zooearly-gateway.zip
mv zooearly-gateway ZooEarly-BE
cd ZooEarly-BE

ls -a                   # .gitignore, .github 가 보이는지 확인
chmod +x gradlew        # 실행 권한 부여
./gradlew build

git init -b main
git add -A
git commit -m "chore: 프로젝트 초기 세팅"
git remote add origin https://github.com/sktflyai-passion5/ZooEarly-BE.git
git push -u origin main
```

</details>

### 2.3 레포 보호 설정

푸시가 끝나면 GitHub 레포 페이지에서 두 가지를 설정한다.

**① main 브랜치 보호** — 실수로 main에 직접 푸시하는 걸 막는다.

**반드시 2.2의 첫 푸시가 끝난 뒤에 설정한다.** 먼저 걸면 첫 푸시가 막힌다.

`Settings` → `Branches` → `Add branch ruleset` (또는 `Add rule`)
- Branch name pattern: `main`
- **Require a pull request before merging** 체크

> **혼자 개발할 때도 PR을 거쳐야 해서 번거롭다면**, 규칙에서 `Require approvals`는 체크하지 않으면 된다. 그러면 PR은 만들되 본인이 바로 머지할 수 있다 — 변경 이력이 PR 단위로 남아 나중에 "이 코드 왜 이렇게 됐지"를 추적하기 쉬워진다.

**② 팀원 초대**

조직(`sktflyai-passion5`) 레포이므로 두 가지 경로가 있다.

- **조직 멤버가 이미 있다면**: 레포 `Settings` → `Collaborators and teams` → `Add teams` 로 팀 단위 추가
- **개별 초대**: 같은 화면의 `Add people` → 팀원 GitHub 계정 입력

> 조직 레포는 소유자(Owner) 권한이 있어야 위 설정이 보인다. 안 보이면 조직 관리자에게 요청한다.

### 2.4 팀원이 코드 받아가기

내가 푸시한 뒤, 다른 팀원은 zip을 받을 필요 없이 clone하면 된다.

```bash
git clone https://github.com/sktflyai-passion5/ZooEarly-BE.git
cd ZooEarly-BE
./gradlew build
```

> **폴더 이름과 jar 이름은 별개다.** clone하면 폴더는 `ZooEarly-BE`가 되지만, 빌드 결과물은 `settings.gradle`의 `rootProject.name`이 정하므로 항상 `zooearly-gateway-0.0.1-SNAPSHOT.jar`다. 이름이 달라 보여도 정상이다.

### 2.5 프론트 담당자에게 보낼 것

파일 3개 + 말로 전달할 것 2개.

| 항목 | 무엇 | 왜 필요한가 |
|---|---|---|
| `zooearly-ai-api-spec.md` | 사람용 명세서 | 앱이 지켜야 할 규칙이 여기 있다 (history 관리, 에러 폴백, 캐싱 등) |
| `zooearly-ai-openapi.yaml` | Swagger 명세 | editor.swagger.io에 붙여넣으면 UI로 볼 수 있다 |
| `zooearly-ai-api.types.ts` | TypeScript 타입 | 그대로 import해서 쓰면 필드 오타가 컴파일 에러로 잡힌다 |
| (말로) 서버 주소 | 개발 중엔 `http://localhost:8080`, 배포 후엔 `https://zooearly-gateway.azurewebsites.net` | 요청 보낼 곳 |
| (말로) 연동 가능 시점 | 언제부터 실제 호출 테스트가 되는지 | 프론트가 mock으로 갈지 실서버로 갈지 판단 |

---

## 3. 로컬에서 실행하기

### 3.1 VS Code로 열기

```bash
code C:\Users\User\Desktop\skflyai\ZooEarly-BE
```

또는 VS Code에서 `File` → `Open Folder` → 프로젝트 폴더 선택.

처음 열면 오른쪽 아래에 "Java 프로젝트 가져오는 중" 알림이 뜨고 1~2분 걸린다. **이게 끝날 때까지 기다린다.** 끝나기 전에 실행하면 이상한 에러가 난다.

### 3.2 실행

**방법 A — Spring Boot Dashboard (편함)**

왼쪽 사이드바에 스프링 잎사귀 아이콘이 생겼을 것이다. 클릭하면 프로젝트 이름이 보이고, 옆의 ▶ 버튼을 누르면 실행된다.

**방법 B — 터미널**

```cmd
gradlew.bat bootRun
```

> **Windows는 `gradlew.bat`, Mac/Linux는 `./gradlew`** 다. 이 문서에서 `./gradlew` 로 적힌 명령은 Windows에서 전부 `gradlew.bat` 으로 바꿔 읽으면 된다.

**방법 C — 디버깅 (중단점 사용)**

`ZooEarlyApplication.java` 파일을 열고 `main` 메서드 위의 **Debug** 링크를 클릭하거나 `F5`.

콘솔에 아래 줄이 보이면 성공이다.

```
Tomcat started on port 8080 (http)
Started ZooEarlyApplication in 2.3 seconds
```

### 3.3 동작 확인

FastAPI가 아직 없어도 **게이트웨이의 검증 로직까지는 확인할 수 있다.** 다른 터미널을 열고:

```bash
# 필수 파라미터 누락 → 400 에러가 명세 §1.2 포맷으로 오면 정상
curl -X POST http://localhost:8080/api/v1/ai/tts \
  -H "Content-Type: application/json" \
  -d '{}'
```

기대 응답:

```json
{"success":false,"error":{"code":"INVALID_PARAMETER","message":"...","field":"text"}}
```

```bash
# 정상 요청 → FastAPI가 없으므로 502 AI_SERVER_ERROR가 나오는 게 정상
curl -X POST http://localhost:8080/api/v1/ai/tts \
  -H "Content-Type: application/json" \
  -d '{"text":"안녕하세요","voice":"TEACHER"}'
```

> **502가 나오면 성공이다.** "검증은 통과했고, FastAPI에 전달하려 했는데 서버가 없더라"는 뜻이다. 게이트웨이가 제 역할을 하고 있다는 증거다.

### 3.4 FastAPI 담당자와 맞춰야 할 것

게이트웨이는 FastAPI에 이렇게 보낸다. 이 규약을 FastAPI 담당자와 공유한다.

| 게이트웨이가 보내는 곳 | 형식 | 보내는 필드 |
|---|---|---|
| `POST /ai/chat` | multipart | `audio`(파일), `scenario`, `history`, `nativeLanguage` |
| `POST /ai/stt` | multipart | `audio`(파일), `language` |
| `POST /ai/tts` | JSON | 앱이 보낸 body 그대로 |
| `POST /ai/feedback` | JSON | 앱이 보낸 body 그대로 |

**FastAPI가 지켜야 할 두 가지:**

1. **응답을 `{"success": true, "data": {...}}` 포맷으로 만든다.** 게이트웨이는 body를 가공하지 않고 그대로 앱에 전달하므로, FastAPI가 만드는 모양이 곧 앱이 받는 모양이다.
2. **422(STT 엔진 실패) / 429(쿼터 초과)는 `{"success": false, "error": {...}}` 포맷으로 직접 만든다.** 이 둘만 게이트웨이가 그대로 통과시키고, 나머지 에러는 `502 AI_SERVER_ERROR`로 덮어버린다.

FastAPI 주소가 `localhost:8000`이 아니라면 환경변수로 알려준다.

```bash
export INFERENCE_BASE_URL=http://실제주소:포트
./gradlew bootRun
```

---

## 4. 개발 흐름

### 4.1 브랜치 → PR → 머지 (GitHub Flow)

**main에는 직접 푸시하지 않는다.** 항상 브랜치를 파고 PR로 합친다.

```bash
# ① 최신 main 받아오기 (작업 시작 전 항상)
git switch main
git pull

# ② 작업용 브랜치 생성
git switch -c feature/tts-relay

# ③ 코드 작성 후 커밋
git add -A
git commit -m "feat: tts 엔드포인트 릴레이 구현"

# ④ 원격에 올리기
git push -u origin feature/tts-relay
```

⑤ GitHub 레포 페이지에 가면 **"Compare & pull request"** 버튼이 떠 있다. 클릭 → PR 템플릿을 채워서 생성 → 팀원 리뷰 → **Merge pull request**.

```bash
# ⑥ 머지 후 정리
git switch main
git pull
git branch -d feature/tts-relay
```

### 4.2 커밋 메시지 규칙

`타입: 설명` 형식.

| 타입 | 언제 | 예시 |
|---|---|---|
| `feat` | 기능 추가 | `feat: feedback 엔드포인트 릴레이 구현` |
| `fix` | 버그 수정 | `fix: history 빈 배열이 400으로 떨어지는 문제` |
| `refactor` | 동작 그대로, 코드 정리 | `refactor: 검증 헬퍼 메서드 분리` |
| `docs` | 문서만 | `docs: README 실행법 보완` |
| `test` | 테스트만 | `test: chat 검증 실패 케이스 추가` |
| `chore` | 빌드·설정 | `chore: gradle 의존성 추가` |

### 4.3 어디에 코드를 쓰나

```
src/main/java/com/zooearly/
├── ZooEarlyApplication.java        건드릴 일 없음
├── common/
│   ├── response/  ApiResponse, ErrorCode      에러 코드 추가할 때만
│   └── exception/ GlobalExceptionHandler      새 예외 처리 추가할 때만
└── ai/
    ├── AiController.java      엔드포인트 추가·수정
    ├── AiRelayService.java    ★ 검증 로직은 대부분 여기
    └── client/InferenceClient.java   FastAPI 호출 방식 바꿀 때
```

> **게이트웨이에 비즈니스 로직을 넣지 않는다.** DB 조회, 사용자 정보 가공, 대화 이력 저장 — 이런 게 필요해 보이면 설계를 다시 확인한다. 지금 구조에서 그건 전부 앱이나 FastAPI의 일이다.

### 4.4 자주 겪는 문제

| 증상 | 원인 | 해결 |
|---|---|---|
| `Port 8080 was already in use` | 서버가 이미 떠 있음 | 이전 실행을 중지하거나, `application.yml`의 `server.port`를 8081로 변경 |
| VS Code에서 빨간 줄이 잔뜩 | 프로젝트 로딩 미완료 | 하단 상태바 확인 후 대기. 그래도면 `Ctrl+Shift+P` → `Java: Clean Java Language Server Workspace` |
| `Could not resolve ...` (빌드 실패) | 인터넷·프록시 문제 | 네트워크 확인 후 `./gradlew build --refresh-dependencies` |
| 502만 계속 나옴 | FastAPI 미실행 | 정상 동작이다. FastAPI가 떠야 200이 나온다 |
| 한글이 `???`로 깨짐 | 인코딩 | 파일 저장 시 UTF-8 확인 |

---

## 5. Azure App Service 배포

### 5.1 왜 Azure인가, 왜 App Service인가

- **FastAPI/모델 쪽이 이미 Azure로 배포한다** (Container Apps, `koreacentral` 리전 — `DEPLOY_azure.md`). 게이트웨이도 같은 클라우드·같은 리전에 두면 두 서버 사이 지연이 줄고, 방화벽·네트워크 설정을 따로 두 클라우드에서 익힐 필요가 없다.
- FastAPI는 700MB대 모델을 메모리에 올려야 해서 Container Apps(Docker, 2vCPU/4GiB)가 필요하지만, **이 게이트웨이는 모델이 없는 얇은 릴레이**다. 그만큼 무거운 선택지는 필요 없다 — **App Service(Linux, Java 17 내장 런타임)** 로 충분하다. Dockerfile을 만들 필요도 없다. jar 하나만 올리면 된다.
- App Service는 기본으로 `https://<이름>.azurewebsites.net` HTTPS 주소를 준다. EC2 방식이었다면 인증서를 직접 붙이기 전까지 앱에 cleartext 예외(`usesCleartextTraffic`, `NSAllowsLocalNetworking`)를 걸어둬야 했는데, Azure는 처음부터 https라 배포 시점엔 그 설정을 뺄 수 있다.

### 5.2 가격 — 요금 사고 막는 습관

| | 내용 |
|---|---|
| 무료 체험 | 신규 계정 **$200 크레딧**(30일) — 데모 준비 기간엔 이걸로 충분 |
| 상시 운영 | App Service **Basic B1**(1 vCore/1.75GB) 기준 월 대략 $13 내외. 정확한 값은 [Azure 가격 계산기](https://azure.microsoft.com/pricing/calculator/)에서 리전(`Korea Central`)·SKU로 직접 확인한다 |
| 테스트용 무료 티어 | **F1(Free)** — 계속 무료지만 하루 CPU 60분 제한이 있고 5.4의 Always On을 못 켠다. 개발 중 연결 확인용으로는 충분, **발표 당일엔 B1 이상 권장** |

- Cost Management → Budgets에서 월 예산 알림을 걸어둔다 (AWS Budgets와 같은 개념).
- 안 쓸 때는 `az webapp stop`으로 중지한다. 프로젝트가 완전히 끝나면 리소스 그룹째 삭제해서 요금을 완전히 끊는다.

### 5.3 리소스 만들기 (CLI)

[Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli)를 설치하고 `az login` 후 진행한다.

```bash
# 리소스 그룹 — FastAPI와 같은 리전에 둔다 (DEPLOY_azure.md와 맞춤)
az group create --name zooearly-rg --location koreacentral

# App Service Plan — Basic B1, Linux
az appservice plan create \
  --name zooearly-plan \
  --resource-group zooearly-rg \
  --location koreacentral \
  --sku B1 \
  --is-linux

# Web App — Java 17
az webapp create \
  --name zooearly-gateway \
  --resource-group zooearly-rg \
  --plan zooearly-plan \
  --runtime "JAVA:17-java17"
```

`zooearly-gateway`는 Azure 전역에서 유일한 이름이어야 한다 (도메인의 일부라서). 이미 쓰이고 있으면 `zooearly-gateway-<팀명>`처럼 바꾼다. 주소는 `https://<이름>.azurewebsites.net`이 된다.

### 5.4 환경변수 설정

EC2에서 `export`나 systemd `Environment=`로 넣던 것을 **App Settings**로 넣는다. **비밀값이 실제로 들어가는 유일한 곳이다 — `application.yml`이나 코드에는 절대 넣지 않는다** (CLAUDE.md 비밀값 규칙).

```bash
az webapp config appsettings set \
  --name zooearly-gateway \
  --resource-group zooearly-rg \
  --settings \
    INFERENCE_BASE_URL="https://<FastAPI 주소>" \
    INFERENCE_API_KEY="<FastAPI가 발급한 키>"
```

`INFERENCE_API_KEY`는 FastAPI가 `X-API-Key` 헤더로 인증을 검사하기 때문에 필요하다 (`DEPLOY_azure.md` §4, [zooearly-gateway-to-fastapi.md](zooearly-gateway-to-fastapi.md) 참고). FastAPI 쪽 경로를 바꿨다면 `INFERENCE_PATH_*` 값들도 같이 넣는다 (기본값은 `application.yml` 참고).

Always On 켜기 (B1 이상에서만 가능 — 요청이 없어도 프로세스를 안 내려서, 한동안 조용하다가 들어온 첫 요청이 느려지는 걸 막는다):

```bash
az webapp config set --name zooearly-gateway --resource-group zooearly-rg --always-on true
```

### 5.5 jar 빌드 & 배포

서버에 자바를 따로 설치할 필요가 없다 — App Service의 Java 17 런타임이 이미 올라가 있다. **내 컴퓨터에서 빌드한 jar를 그대로 올리기만 하면 된다.**

```cmd
REM ① 빌드 — build\libs\ 에 jar가 생긴다  (Mac/Linux는 ./gradlew clean build)
gradlew.bat clean build

REM ② 배포
az webapp deploy ^
  --name zooearly-gateway ^
  --resource-group zooearly-rg ^
  --src-path build\libs\zooearly-gateway-0.0.1-SNAPSHOT.jar ^
  --type jar
```

배포 로그로 기동 확인:

```bash
az webapp log tail --name zooearly-gateway --resource-group zooearly-rg
```

`Started ZooEarlyApplication`이 보이면 성공이다.

### 5.6 확인

```bash
curl -X POST https://zooearly-gateway.azurewebsites.net/api/v1/ai/tts \
  -H "Content-Type: application/json" -d '{}'
```

400 에러가 명세 포맷으로 오면 **배포 성공이다.** SSH 접속이나 방화벽(보안 그룹) 설정이 따로 필요 없다 — EC2 5.3~5.4에 해당하는 작업 전체가 사라졌다.

### 5.7 계속 돌아가게 하기

EC2는 SSH 연결이 끊기면 프로세스도 같이 죽어서 nohup이나 systemd 유닛이 필요했다. **App Service는 그 개념 자체가 없다** — `az webapp deploy`로 올린 순간부터 관리형 프로세스로 계속 실행되고, 죽으면 플랫폼이 자동으로 재시작한다. 5.4의 Always On은 "죽었을 때 재시작"이 아니라 "유휴 상태에서도 안 내려가게" 하는 별개의 설정이다.

### 5.8 코드를 수정했을 때 (재배포)

```cmd
gradlew.bat clean build
az webapp deploy --name zooearly-gateway --resource-group zooearly-rg --src-path build\libs\zooearly-gateway-0.0.1-SNAPSHOT.jar --type jar
```

EC2의 "빌드 → scp로 전송 → SSH 접속 → systemctl restart" 네 단계가 빌드 한 번 + `az webapp deploy` 한 줄로 끝난다.

### 5.9 프론트에 알려줄 주소

```
https://zooearly-gateway.azurewebsites.net
```

EC2의 퍼블릭 IP는 인스턴스를 껐다 켜면 바뀌어서 Elastic IP를 따로 고정해야 했다. App Service는 **리소스를 지우지 않는 한 이 도메인이 그대로 고정**이라 그 작업이 필요 없다.

---

## 6. 배포 체크리스트

발표·데모 전에 확인한다.

- [ ] `./gradlew build` 가 로컬에서 통과한다
- [ ] `az webapp deploy`로 올린 뒤 `az webapp log tail`에 `Started ZooEarlyApplication`이 찍혔다
- [ ] 외부에서 `curl https://zooearly-gateway.azurewebsites.net/...`로 API가 응답한다 (400/502라도 응답하면 OK)
- [ ] FastAPI가 떠 있고, `INFERENCE_BASE_URL`이 그 주소를 가리킨다
- [ ] `INFERENCE_API_KEY`가 App Settings에 설정되어 있다 (없으면 FastAPI가 401을 준다)
- [ ] 실제 요청 하나가 200으로 끝까지 통한다 (앱 → 게이트웨이 → FastAPI → OpenAI)
- [ ] Azure Cost Management → Budgets 알림이 설정되어 있다
- [ ] 프론트 담당자가 서버 주소(`https://zooearly-gateway.azurewebsites.net`)를 알고 있다
- [ ] Always On이 켜져 있다 (B1 이상, 발표 중 첫 요청 지연 방지)
- [ ] **API 키가 GitHub에 올라가지 않았다** (`git log -p | grep -i "key\|secret"` 로 확인)

---

## 7. 참고 링크

- [Spring Boot in Visual Studio Code](https://code.visualstudio.com/docs/java/java-spring-boot) — VS Code 공식 Spring Boot 가이드
- [Java extensions for Visual Studio Code](https://code.visualstudio.com/docs/java/extensions) — 확장팩 목록
- [Deploy a Java app to Azure App Service](https://learn.microsoft.com/azure/app-service/quickstart-java) — App Service Java 배포 공식 가이드
- [Azure CLI 설치](https://learn.microsoft.com/cli/azure/install-azure-cli) — `az` 명령 설치
- [Azure 가격 계산기](https://azure.microsoft.com/pricing/calculator/) — 최신 요금 확인
- [Eclipse Temurin JDK 17](https://adoptium.net/temurin/releases/?version=17) — JDK 다운로드
