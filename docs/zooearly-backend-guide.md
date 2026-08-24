# 쥬얼리 (ZooEarly) — 백엔드 개발 가이드

> **v1.0 · 2026-08-21**
> GitHub 세팅 → VS Code 개발 환경 → 개발 흐름 → Docker · Azure Container Apps 배포
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
| 5 | Docker · Azure Container Apps 배포 | 2~3시간 (첫 배포) |

### 0.3 미리 알아둘 용어

| 용어 | 쉬운 설명 |
|---|---|
| **JDK** | 자바 프로그램을 만들고 실행하는 도구 모음. 없으면 아무것도 안 됨 |
| **Gradle** | 라이브러리를 자동으로 받아오고 빌드해주는 도구. `build.gradle`이 설정 파일 |
| **jar 파일** | 프로젝트 전체를 하나로 압축한 실행 파일. 배포할 때 이것만 서버에 올린다 |
| **빌드(build)** | 소스코드 → 실행 가능한 jar로 변환하는 것 |
| **컨테이너(Docker)** | 앱과 실행 환경을 통째로 묶은 상자. 내 PC에서 돌던 게 서버에서 그대로 돈다 |
| **Container Apps** | Azure가 관리해주는 컨테이너 실행 환경. 이미지를 올리면 서버 관리 없이 24시간 돌아간다 |
| **ACR** | Azure의 컨테이너 이미지 보관소. 빌드한 이미지를 여기 올리면 Container Apps가 가져다 쓴다 |
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
| (말로) 서버 주소 | 개발 중엔 `http://localhost:8080`, 배포 후엔 Container Apps FQDN (§5.11) | 요청 보낼 곳 |
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
## 5. Azure Container Apps 배포 (Docker)

### 5.1 왜 Azure인가, 왜 Container Apps인가

- **FastAPI/모델 쪽이 이미 Azure Container Apps(`koreacentral`)로 배포한다** (`DEPLOY_azure.md`). 게이트웨이도 같은 클라우드·같은 리전·같은 방식으로 두면 두 서버 사이 지연이 줄고, **팀 전체가 배포 방식 하나만 익히면 된다.**
- 컨테이너로 만들면 "내 컴퓨터에서는 되는데" 문제가 사라진다. 로컬에서 돌린 이미지가 서버에서 그대로 돈다.
- 다만 **리소스는 FastAPI보다 훨씬 작게 잡는다.** FastAPI는 700MB대 모델을 메모리에 올려야 해서 2vCPU/4GiB가 필요하지만, 이 게이트웨이는 모델이 없는 얇은 릴레이라 **0.5vCPU / 1Gi로 충분**하다.
- Container Apps는 `https://<이름>.<지역>.azurecontainerapps.io` HTTPS 주소를 기본으로 준다. 인증서를 따로 붙일 필요가 없다.

### 5.2 가격 — 요금 사고 막는 습관

| 항목 | 대략 |
|---|---|
| 무료 체험 | 신규 계정 **$200 크레딧**(30일) |
| Container Apps (0.5vCPU/1Gi, min-replicas 1) | 월 1~2만원 |
| **Azure for Students** | **$100 크레딧 · 신용카드 불필요.** 위 비용이 여기서 차감된다 |
| Azure Container Registry **Basic** | 월 약 $5 |

- Cost Management → **Budgets**에서 월 예산 알림을 먼저 걸어둔다. 이걸 안 하면 다음 달 카드값을 보고 알게 된다.
- 프로젝트가 끝나면 **리소스 그룹째 삭제**한다 (`az group delete --name zooearly-rg`). 그래야 요금이 완전히 끊긴다.

> **ACR 대신 ghcr.io(GitHub Container Registry)를 쓰면 이미지 저장이 무료다.** 대신 프라이빗 레포라 Container Apps에 pull 인증을 따로 걸어야 해서 한 단계가 늘어난다. 월 $5를 아낄지, 설정을 줄일지의 선택이다.

### 5.3 배포 전 테스트 — 로컬에서 다 끝낸다

CD 자동화가 붙기 전이든 후든, **확인 순서는 아래 4단계로 같다.** 앞 단계를 통과 못 하면 다음으로 넘어가지 않는다. 뒤로 갈수록 문제 원인을 찾기 어려워지기 때문이다.

| 단계 | 무엇을 확인하나 | 언제 |
|---|---|---|
| ① 자동 테스트 | 검증 규칙·에러 매핑이 명세대로인가 | 커밋할 때마다 (CI 자동) |
| ② 이미지 단독 기동 | 컨테이너가 뜨고 응답하는가 | 이미지를 고쳤을 때 |
| ③ 목 서버 연동 | 전 구간이 뚫리고 에러 계약이 맞는가 | 배포 직전 |
| ④ 진짜 FastAPI | 실제 연동이 되는가 | FastAPI가 떴을 때 |

#### ① 자동 테스트 — CI가 알아서 돈다

```bash
gradlew.bat test          # Mac/Linux는 ./gradlew test
```

`dev`/`main`으로 가는 PR·푸시마다 GitHub Actions(`build.yml`)가 같은 걸 돌린다. **여기서 빨간불이 뜨면 머지하지 않는다.**

#### ② 이미지 단독 기동

레포 루트에 `Dockerfile`과 `.dockerignore`가 들어있다. **멀티스테이지 빌드**라 최종 이미지에 JDK·소스·Gradle 캐시가 남지 않는다.

```bash
docker build -t zooearly-gateway:local .
docker run --rm -p 8080:8080 zooearly-gateway:local
```

`Started ZooEarlyApplication`이 뜨면 성공이다. 다른 터미널에서:

```bash
curl -X POST http://localhost:8080/api/v1/ai/tts \
  -H "Content-Type: application/json" -d '{}'
# {"success":false,"error":{"code":"INVALID_PARAMETER",...,"field":"text"}}
```

FastAPI 주소를 안 줬으므로 **여기서는 400(검증 실패)까지만 확인**한다. 정상 200을 보려면 ③으로 간다.

#### ③ 목 서버를 붙여 전 구간 확인 ★

진짜 FastAPI 없이도 정상 응답과 에러 화면을 전부 볼 수 있다. 터미널 두 개가 필요하다.

```bash
# 터미널 1 — 가짜 추론 서버
python tools/mock-inference/mock_server.py

# 터미널 2 — 게이트웨이 컨테이너
docker run --rm -p 8080:8080 \
  -e INFERENCE_BASE_URL=http://host.docker.internal:8000 \
  zooearly-gateway:local
```

> ### ⚠️ `localhost`가 아니라 `host.docker.internal`이다
>
> **컨테이너 안에서 `localhost`는 컨테이너 자기 자신을 가리킨다.** 내 PC가 아니다.
> `INFERENCE_BASE_URL=http://localhost:8000`으로 주면 목 서버가 멀쩡히 떠 있어도
> **전부 `502 AI_SERVER_ERROR`가 난다.** 컨테이너에서 호스트(내 PC)를 부르려면
> `host.docker.internal`을 쓴다. (Docker Desktop 기준. 로컬 개발에서만 쓰는 주소이고
> 배포된 Container Apps에서는 진짜 FastAPI 주소가 들어간다.)

준비됐으면 스모크 테스트를 돌린다.

```bash
./tools/smoke-test.sh http://localhost:8080
```

```
  OK   검증실패           400 INVALID_PARAMETER
  OK   정상                 200 -
  OK   STT엔진죽음        422 STT_FAILED
  OK   쿼터초과           429 RATE_LIMITED
  OK   추론서버5xx        502 AI_SERVER_ERROR
  OK   타임아웃           504 AI_TIMEOUT
  통과 6 / 실패 0
```

**6개가 전부 OK여야 배포한다.** 정상 경로뿐 아니라 **에러 계약**(422/429는 FastAPI 응답을 그대로 통과, 나머지는 게이트웨이가 공통 포맷으로 감쌈)까지 한 번에 확인된다. 타임아웃 케이스가 있어 15초쯤 걸린다.

#### ④ 진짜 FastAPI 연동

`INFERENCE_BASE_URL`을 실제 주소로 바꾸고, 인증 키가 필요하면 같이 준다.

```bash
docker run --rm -p 8080:8080 \
  -e INFERENCE_BASE_URL=https://<FastAPI 주소> \
  -e INFERENCE_API_KEY=<키> \
  zooearly-gateway:local
```

강제 에러 토큰(`__slow__` 등)은 **목 서버만 이해한다.** 진짜 FastAPI에서는 정상 경로와 검증 실패만 의미가 있다.

> **배포한 뒤에도 같은 스크립트를 쓴다.** 주소만 바꾸면 된다 —
> `./tools/smoke-test.sh https://<FQDN>` (§5.7)

**서버에 올리기 전에 이걸 먼저 통과시킨다.** 로컬에서 안 되는 이미지는 Azure에서도 안 된다.

> **실측 참고** — 최종 이미지 **342MB**, 기동 시간 **약 2.5초**.
> 런타임 베이스로 `jre-alpine`을 골랐다. `jre-jammy`로 만들면 460MB가 나오는데, 둘 다 정상 동작을 확인한 뒤 더 작은 쪽을 택했다. 이 서버는 네이티브 라이브러리를 쓰지 않아 alpine에서 문제가 될 지점이 없다.

### 5.4 Azure 리소스 만들기

[Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli)를 설치하고 `az login` 후 진행한다.

> ### ⚠️ 학생 구독은 리전이 제한된다 — 실제로 겪은 것
>
> **Azure for Students는 `koreacentral`에 리소스를 만들 수 없다.** 시도하면 이렇게 막힌다.
>
> ```
> (RequestDisallowedByAzure) Resource '...' was disallowed by Azure:
> This policy maintains a set of best available regions where your
> subscription can deploy resources.
> ```
>
> 리소스 그룹은 `koreacentral`로 만들어져서 되는 줄 알았다가, 실제 리소스에서 막힌다.
> **`japaneast`가 허용되고 한국에서 지연이 가장 적은 대안**이라 이걸 썼다.
> 아래 명령의 리전은 전부 `japaneast` 기준이다.
>
> 아동 음성을 국내에서 처리하는 편이 좋지만(FastAPI 쪽 `DEPLOY_azure.md`의 논의),
> 학생 구독으로는 선택지가 없다. 유료 구독으로 옮기면 `koreacentral`을 쓸 수 있다.

```bash
# 확장 설치 (최초 1회)
az extension add --name containerapp --upgrade
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.OperationalInsights

# 리소스 그룹 — FastAPI와 같은 리전에 둔다
az group create --name zooearly-rg --location japaneast

# 컨테이너 레지스트리 (이미지 보관소). 이름은 소문자·숫자만, 전역에서 유일해야 한다
# 이름은 소문자·숫자만, 전역에서 유일해야 한다 (이미 쓰이면 숫자를 붙인다)
az acr create --name zooearlyacr --resource-group zooearly-rg --sku Basic --admin-enabled true --location japaneast

# Container Apps 실행 환경
az containerapp env create \
  --name zooearly-env \
  --resource-group zooearly-rg \
  --location japaneast
```

### 5.5 이미지 올리고 앱 만들기

```bash
# ACR에 로그인하고 이미지를 빌드해서 푸시한다
az acr login --name zooearlyacr
docker build -t zooearlyacr.azurecr.io/zooearly-gateway:v1 .
docker push zooearlyacr.azurecr.io/zooearly-gateway:v1

# Container App 생성
az containerapp create \
  --name zooearly-gateway \
  --resource-group zooearly-rg \
  --environment zooearly-env \
  --image zooearlyacr.azurecr.io/zooearly-gateway:v1 \
  --registry-server zooearlyacr.azurecr.io \
  --target-port 8080 \
  --ingress external \
  --cpu 0.5 --memory 1Gi \
  --min-replicas 1 --max-replicas 3 \
  --env-vars INFERENCE_BASE_URL="https://<FastAPI 주소>" INFERENCE_API_KEY=secretref:inference-api-key \
  --secrets inference-api-key="<FastAPI가 발급한 키>"
```

**`--min-replicas 1`이 중요하다.** 0으로 두면 요청이 없을 때 인스턴스가 완전히 내려가고, 그 뒤 첫 요청이 기동을 기다리느라 느려진다. 발표 중에 이런 일이 나면 곤란하다. (FastAPI 쪽도 같은 이유로 1을 쓴다 — 그쪽은 모델 로딩 때문에 30초가 넘어서 훨씬 절박하다.)

**주소 확인:**

```bash
az containerapp show --name zooearly-gateway --resource-group zooearly-rg \
  --query properties.configuration.ingress.fqdn -o tsv
# zooearly-gateway.xxxx.japaneast.azurecontainerapps.io
```

### 5.6 비밀값 다루기 ⚠️

`INFERENCE_API_KEY`는 FastAPI가 `X-API-Key` 헤더로 인증을 검사하기 때문에 필요하다 (`DEPLOY_azure.md` §4). **이 값이 없으면 FastAPI가 401을 돌려준다.**

- 위 명령의 `--secrets`처럼 **Container Apps 시크릿으로 넣고 `secretref:`로 참조**한다. 평문 `--env-vars`에 직접 쓰지 않는다 — 그러면 `az containerapp show` 출력에 그대로 찍힌다
- **`application.yml`이나 코드에는 절대 넣지 않는다.** 거기엔 `${환경변수}` 참조만 둔다 (CLAUDE.md 비밀값 규칙)
- 값을 나중에 바꾸려면:

```bash
az containerapp secret set --name zooearly-gateway --resource-group zooearly-rg \
  --secrets inference-api-key="<새 키>"
```

FastAPI 쪽 경로를 바꿨다면 `INFERENCE_PATH_*` 값들도 같이 넣는다 (기본값은 `application.yml` 참고).

### 5.7 확인

```bash
FQDN=$(az containerapp show --name zooearly-gateway --resource-group zooearly-rg \
  --query properties.configuration.ingress.fqdn -o tsv)

curl -X POST https://$FQDN/api/v1/ai/tts \
  -H "Content-Type: application/json" -d '{}'
```

400 에러가 명세 포맷으로 오면 **배포 성공이다.** 스모크 테스트도 같은 주소로 돌릴 수 있다.

```bash
./tools/smoke-test.sh https://$FQDN
```

단, 강제 에러 토큰은 목 서버만 이해하므로 **진짜 FastAPI를 붙인 상태에서는 `검증실패`·`정상` 두 개만 통과하는 게 정상**이다 (§5.3 ④).

로그 보기:

```bash
az containerapp logs show --name zooearly-gateway --resource-group zooearly-rg --follow
```

### 5.8 헬스체크 (probe)

**헬스체크는 Azure가 "이 서버 아직 살아있냐?"를 주기적으로 물어보는 것이다.** 프로세스가 떠 있어도 실제로는 응답을 못 하는 상태가 있을 수 있어서, 포트만 보고 판단하지 않으려는 장치다.

**결정: 별도 probe를 걸지 않고 Container Apps 기본 TCP 확인을 쓴다.** 8080 포트가 열려 있는지를 보고, 컨테이너가 죽으면 자동으로 재시작한다. 데모·발표 규모에서는 이걸로 충분하다.

Spring Boot Actuator를 넣어 `/actuator/health`를 쓰는 선택지도 있었지만 **택하지 않았다.** 엔드포인트가 하나 늘어나는 변경인데(CLAUDE.md: 엔드포인트를 임의로 추가하지 않는다), 그만큼의 이득이 이 규모에서는 크지 않다고 판단했다.

| 방식 | 잡아내는 것 | 비용 |
|---|---|---|
| **TCP** ✅ 채택 | 프로세스가 죽거나 포트가 닫힘 | 설정 0 |
| Actuator `/actuator/health` | 위 + Spring이 정상 기동했는지, 요청 받을 준비가 됐는지 | 의존성 1줄 + 설정 |

> **나중에 바꿀 만한 시점**: 트래픽이 늘어 인스턴스를 여러 개 굴리게 되거나, 재배포 중 순간적인 502가 눈에 띄기 시작하면 그때 Actuator를 넣는다. 그 전까지는 TCP로 충분하다.

### 5.9 코드를 수정했을 때 (재배포)

```bash
docker build -t zooearlyacr.azurecr.io/zooearly-gateway:v2 .
docker push zooearlyacr.azurecr.io/zooearly-gateway:v2

az containerapp update --name zooearly-gateway --resource-group zooearly-rg \
  --image zooearlyacr.azurecr.io/zooearly-gateway:v2
```

**태그를 `v1`, `v2`처럼 올려가며 쓴다.** `latest`로 덮어쓰면 "지금 서버에 뭐가 떠 있는지" 알 수 없어지고, 문제가 생겼을 때 되돌릴 수도 없다. 태그를 나눠두면 이전 버전으로 즉시 롤백할 수 있다:

```bash
az containerapp update --name zooearly-gateway --resource-group zooearly-rg \
  --image zooearlyacr.azurecr.io/zooearly-gateway:v1     # 되돌리기
```

> 이 과정은 **5.10의 GitHub Actions로 자동화하는 것이 최종 목표**다. 위 명령들은 자동화가 붙기 전, 그리고 문제가 생겨 수동으로 손봐야 할 때 쓴다.

### 5.10 CI/CD (GitHub Actions)

브랜치 전략(`main` = 시연 가능한 안정판)에 그대로 맞춘다.

| 트리거 | 하는 일 | 파일 |
|---|---|---|
| `dev`·`main`으로 가는 PR / 푸시 | 빌드 + 테스트 | `build.yml` |
| **`main` 머지** | 테스트 → 이미지 빌드 → ACR 푸시 → 배포 → 스모크 확인 | `deploy.yml` |

**`main`에 머지 = 배포**로 두면 규칙이 새로 늘지 않는다. `main`은 원래 "그 시점에 돌아가는 버전"이어야 하므로, 그게 곧 배포 대상이다. `dev`는 배포하지 않는다.

#### 인증은 OIDC — 비밀번호를 저장하지 않는다

GitHub Actions가 Azure에 접속할 때 **비밀번호(client secret)를 GitHub에 저장하지 않는 방식**이다. Actions가 발급받은 단기 토큰을 Azure가 검증하고, 그 토큰은 **이 레포의 `main` 브랜치에서 실행될 때만** 발급된다. "비밀값을 레포에 두지 않는다"는 원칙과 결이 같다.

아래는 **이미 실행해둔 설정**이다. 리소스를 새로 만들거나 다른 레포에 붙일 때 참고한다.

```bash
# ① 앱 등록 + 서비스 주체
appId=$(az ad app create --display-name "zooearly-gh-deploy" --query appId -o tsv)
az ad sp create --id $appId

# ② 페더레이션 자격증명 — 이 레포의 main 브랜치에만 묶는다.
#    subject가 정확히 일치해야 토큰이 발급된다. 브랜치를 바꾸면 여기도 바꿔야 한다.
cat > fedcred.json <<'JSON'
{
  "name": "github-main",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:sktflyai-passion5/ZooEarly-BE:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}
JSON
az ad app federated-credential create --id $appId --parameters fedcred.json

# ③ 권한 — 필요한 최소한만 준다
sub=$(az account show --query id -o tsv)
az role assignment create --assignee $appId --role AcrPush   --scope "/subscriptions/$sub/resourceGroups/zooearly-rg/providers/Microsoft.ContainerRegistry/registries/zooearlyacr2408"
az role assignment create --assignee $appId --role Contributor   --scope "/subscriptions/$sub/resourceGroups/zooearly-rg/providers/Microsoft.App/containerApps/zooearly-gateway"
az role assignment create --assignee $appId --role Reader   --scope "/subscriptions/$sub/resourceGroups/zooearly-rg"
```

권한을 이렇게 쪼갠 이유는 **구독 전체에 쓰기 권한을 주지 않기 위해서다.**

| 역할 | 범위 | 왜 |
|---|---|---|
| `AcrPush` | ACR 하나 | 이미지 푸시만. 레지스트리 삭제 권한은 없다 |
| `Contributor` | Container App 하나 | 이 앱만 갱신. 다른 리소스는 못 건드린다 |
| `Reader` | 리소스 그룹 | 배포 시 환경 정보를 읽어야 해서 **읽기만** |

#### GitHub Secrets

레포 `Settings` → `Secrets and variables` → `Actions`에 아래 3개가 등록되어 있다.

| 이름 | 값 | 비밀인가 |
|---|---|---|
| `AZURE_CLIENT_ID` | ① 의 `appId` | 식별자 (비밀번호 아님) |
| `AZURE_TENANT_ID` | `az account show --query tenantId -o tsv` | 식별자 |
| `AZURE_SUBSCRIPTION_ID` | `az account show --query id -o tsv` | 식별자 |

**셋 다 비밀번호가 아니다.** OIDC의 보안은 ②의 `subject`가 정확히 일치할 때만 토큰이 나오는 데서 온다. 그래도 관례상 Secrets에 둔다.

#### 워크플로가 하는 일

`.github/workflows/deploy.yml` 이 `main` 푸시에서 순서대로 실행한다.

```
테스트 → 이미지 빌드 → ACR 푸시 → 배포 → 기동 대기 → 스모크 → (실패 시) 자동 롤백
```

설계에서 눌러둔 것들:

- **테스트 잡과 배포 잡을 분리**했다. `./gradlew test`는 레포 코드와 외부 의존성을 JVM에서 실행하는데, 이걸 Azure 토큰이 켜진 잡에서 돌리면 그 코드가 토큰을 받아낼 수 있다. **강한 권한과 남의 코드를 같은 러너에 두지 않는다.**
- **이미지는 태그가 아니라 digest로 고정**해 배포한다. 태그는 나중에 다른 이미지를 가리키도록 덮어쓸 수 있어서, 롤백 좌표로 삼으면 거짓말이 된다.
- **앱 설정을 건드리지 않는다.** `--set-env-vars`·`--secrets`를 넘기지 않으므로 `INFERENCE_BASE_URL`·`CORS_ALLOWED_ORIGINS`는 Azure 쪽 소유로 남는다. 배포할 때마다 초기화되면 곤란하다.
- **스모크는 400 INVALID_PARAMETER만 확인**한다. FastAPI가 아직 없어 정상 요청은 502가 나오므로, 그걸 실패로 보면 배포가 늘 실패한다.
- **문서만 고친 머지는 배포하지 않는다** (`paths-ignore`). 어차피 `.dockerignore`가 제외해 이미지에 들어가지도 않는다.
- 서드파티 액션은 **커밋 SHA로 고정**한다. `v4` 같은 이동 태그는 업스트림에서 다른 커밋을 가리키게 바뀔 수 있다.

**롤백은 GitHub 화면에서 한다.** Actions → `deploy` → `Run workflow` → 되돌릴 태그(`sha-1a2b3c4`) 입력. 발표장에서 az CLI 없이 브라우저만으로 되돌릴 수 있어야 해서 이렇게 뒀다. **브랜치는 반드시 `main`을 골라야 한다** — 이 레포 기본 브랜치가 `dev`라 그대로 누르면 OIDC 로그인부터 실패한다.

### 5.11 프론트에 알려줄 주소

**현재 배포된 주소** (2026-08-24 기준):

```
https://zooearly-gateway.politesmoke-47da854d.japaneast.azurecontainerapps.io/api/v1
```

프론트 `.env`에 이 값을 넣는다.

```env
EXPO_PUBLIC_SERVICE_API_URL=https://zooearly-gateway.politesmoke-47da854d.japaneast.azurecontainerapps.io/api/v1
```

> **`/api/v1`까지 붙여서 준다.** 프론트 코드가 `/ai/tts` 같은 경로를 뒤에 붙이는 구조라,
> 빼고 주면 전부 404가 난다.

**리소스를 지우지 않는 한 이 주소는 고정**이므로 한 번만 알려주면 된다.
LAN IP처럼 WiFi가 바뀔 때마다 다시 알려줄 필요가 없다 — 배포하는 이유 중 하나다.

### 5.11-1 프론트 도메인 CORS 허용 ⚠️

프론트가 **브라우저에서 도는 웹으로 배포**되면(Azure Static Website 등) 게이트웨이가 그 도메인을 CORS로 허용해야 한다. 안 하면 preflight가 `403 Invalid CORS request`로 끊겨 **프론트의 모든 호출이 실패한다.**

```bash
az containerapp update -n zooearly-gateway -g zooearly-rg   --set-env-vars CORS_ALLOWED_ORIGINS="https://stzooearlyfe.z12.web.core.windows.net"
```

**현재 허용된 오리진**: `https://stzooearlyfe.z12.web.core.windows.net`

- 여러 개면 **쉼표로 구분**한다 (`https://a.com,https://b.com`)
- 오리진에 **경로나 끝 슬래시를 붙이지 않는다** — `https://example.com` 형태여야 한다
- `--set-env-vars`는 지정한 변수만 더하거나 바꾼다. `INFERENCE_BASE_URL` 같은 기존 값은 유지된다
- **프론트 도메인이 바뀌면 이 값도 바꿔야 한다.** 코드 재배포는 필요 없다

확인하는 법:

```bash
curl -i -X OPTIONS https://<FQDN>/api/v1/ai/tts   -H "Origin: https://stzooearlyfe.z12.web.core.windows.net"   -H "Access-Control-Request-Method: POST"   -H "Access-Control-Request-Headers: content-type"
# → 200 + access-control-allow-origin 헤더가 있어야 정상
```

> 허용하지 않은 오리진은 계속 403으로 막힌다. 와일드카드(`*`)를 쓰지 않기 때문에 아무 사이트나 이 API를 부를 수 없다.

### 5.12 FastAPI 연결

**현재 FastAPI 주소** (2026-08-24 기준):

```
https://zooearly-ai.icypebble-8de5ca89.japaneast.azurecontainerapps.io
```

게이트웨이에 이미 적용해뒀다. 인증 키는 아직 필요 없다 — FastAPI가 `API_KEY`를 비워둔 상태다.

```bash
az containerapp update -n zooearly-gateway -g zooearly-rg   --set-env-vars INFERENCE_BASE_URL="https://zooearly-ai.icypebble-8de5ca89.japaneast.azurecontainerapps.io"
```

> **이 주소를 여기 적어두는 이유**: 연동이 안 될 때 FastAPI를 직접 찔러봐야 원인이 게이트웨이인지
> FastAPI인지 가려진다. 주소를 아무 데도 안 적어두면 그 확인을 아무도 혼자 못 한다.

**FastAPI만 직접 호출해보기** (게이트웨이 우회):

```bash
FA=https://zooearly-ai.icypebble-8de5ca89.japaneast.azurecontainerapps.io
curl $FA/health                                   # 200이면 살아있다
curl $FA/internal/v1/feedback/sentences           # 문장 10개
```

### 5.12-1 아직 안 붙는다 — 필드명이 안 맞는다 ⚠️

2026-08-24 실측. **연결은 됐지만 대부분 422다.**

| 엔드포인트 | 결과 |
|---|---|
| `GET /feedback/sentences` | **200** (단 응답이 봉투 없는 배열 + snake_case) |
| `POST /speech/transcribe` | 422 — `audio`→`audio_file`, `language`→`language_code` |
| `POST /speech/synthesize` | 422 — `language`→`language_code`. 응답도 바이너리 MP3 |
| `POST /feedback/speaking` | 422 — `audio`→`audio_file`, `sentenceId`→`sentence_id` |
| `POST /story/generate` | 422 — `childName`→`child_name` 등. **snake_case로 보내면 200** |
| `POST /feedback/expression` | 502 — 경로 자체가 없음 |

**응답 형식은 FastAPI만 고칠 수 있다.** 게이트웨이는 body를 파싱하지 않으므로(§0.1)
봉투·필드명·TTS 형식을 손댈 방법이 없다. 요청 필드명은 어느 쪽이 맞춰도 된다 —
자세한 역할 나누기는 [zooearly-gateway-to-fastapi.md](zooearly-gateway-to-fastapi.md) §1-1 참고.

### 5.12-2 연결이 끝나면



지금은 `INFERENCE_BASE_URL`이 임시값이라 **추론이 필요한 요청은 502가 난다.**
게이트웨이 자체 검증(400)은 정상 동작하므로 앱 화면 배선은 지금도 확인할 수 있다.

FastAPI 주소와 키가 나오면 아래 한 번으로 연결된다.

```bash
az containerapp update --name zooearly-gateway --resource-group zooearly-rg   --set-env-vars INFERENCE_BASE_URL="https://<FastAPI 주소>"                  INFERENCE_API_KEY=secretref:inference-api-key

az containerapp secret set --name zooearly-gateway --resource-group zooearly-rg   --secrets inference-api-key="<FastAPI가 발급한 키>"
```

연결 후 확인:

```bash
./tools/smoke-test.sh https://zooearly-gateway.politesmoke-47da854d.japaneast.azurecontainerapps.io
```

---

## 6. 배포 체크리스트

발표·데모 전에 확인한다.

- [ ] `./gradlew build` 가 로컬에서 통과한다
- [ ] `docker build` → `docker run`으로 **로컬에서 먼저** 400 응답을 확인했다 (§5.3)
- [ ] `az containerapp logs show`에 `Started ZooEarlyApplication`이 찍혔다
- [ ] 외부에서 `curl https://<FQDN>/api/v1/ai/tts`로 API가 응답한다 (400/502라도 응답하면 OK)
- [ ] FastAPI가 떠 있고, `INFERENCE_BASE_URL`이 그 주소를 가리킨다
- [ ] `INFERENCE_API_KEY`가 **Container Apps 시크릿**으로 들어가 있다 (없으면 FastAPI가 401을 준다)
- [ ] 실제 요청 하나가 200으로 끝까지 통한다 (앱 → 게이트웨이 → FastAPI → OpenAI)
- [ ] `--min-replicas 1`이다 (발표 중 첫 요청 지연 방지)
- [ ] 배포한 이미지 태그를 알고 있다 (`latest` 말고 `v1`/`v2`) — 문제 생기면 즉시 롤백
- [ ] Azure Cost Management → Budgets 알림이 설정되어 있다
- [ ] 프론트 담당자가 서버 주소(FQDN)를 알고 있다
- [ ] **API 키가 GitHub에 올라가지 않았다** (`git log -p | grep -i "key\|secret"` 로 확인)

---

## 7. 참고 링크

- [Spring Boot in Visual Studio Code](https://code.visualstudio.com/docs/java/java-spring-boot) — VS Code 공식 Spring Boot 가이드
- [Java extensions for Visual Studio Code](https://code.visualstudio.com/docs/java/extensions) — 확장팩 목록
- [Azure Container Apps 문서](https://learn.microsoft.com/azure/container-apps/) — Container Apps 공식 가이드
- [Container Apps에 컨테이너 배포하기](https://learn.microsoft.com/azure/container-apps/quickstart-code-to-cloud) — 이미지 빌드 → 배포 퀵스타트
- [Azure CLI 설치](https://learn.microsoft.com/cli/azure/install-azure-cli) — `az` 명령 설치
- [GitHub Actions에서 Azure OIDC 인증](https://learn.microsoft.com/azure/developer/github/connect-from-azure-openid-connect) — 시크릿 없이 배포 붙이기
- [Azure 가격 계산기](https://azure.microsoft.com/pricing/calculator/) — 최신 요금 확인
- [Eclipse Temurin JDK 17](https://adoptium.net/temurin/releases/?version=17) — JDK 다운로드
