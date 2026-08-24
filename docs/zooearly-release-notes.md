# 릴리스 노트 — ZooEarly 게이트웨이

배포한 것과 그때의 판단을 기록한다. 나중에 "왜 이렇게 되어 있지?"를 되짚을 때 본다.

---

## 2026-08-24 · 첫 배포 (이미지 `v1`)

게이트웨이를 **Azure Container Apps에 처음 올렸다.** 이전까지는 각자 PC에서 `bootRun`으로 띄우고 같은 Wi-Fi에서만 붙을 수 있었다.

### 한 줄 요약

```
https://zooearly-gateway.politesmoke-47da854d.japaneast.azurecontainerapps.io/api/v1
```

HTTPS로 24시간 떠 있다. 프론트는 이 주소만 알면 되고, **Wi-Fi가 바뀌어도 주소가 안 바뀐다.**

### 무엇이 달라졌나

| | 이전 | 지금 |
|---|---|---|
| 접속 | 같은 Wi-Fi + LAN IP (`192.168.x.x:8080`) | 어디서나 HTTPS |
| IP | Wi-Fi 바뀔 때마다 다시 공유 | 고정 |
| 방화벽 | 포트 8080 인바운드 규칙 필요 | 불필요 |
| 서버 | 내 PC가 켜져 있어야 함 | 항상 |
| 프로토콜 | 평문 HTTP | HTTPS |

프론트 개발자에게 공유했던 **방화벽 규칙은 이제 지워도 된다.**

```powershell
Remove-NetFirewallRule -DisplayName "ZooEarly Gateway 8080"
```

---

## 배포 정보

| 항목 | 값 |
|---|---|
| 구독 | Azure for Students |
| 리전 | `japaneast` |
| 리소스 그룹 | `zooearly-rg` |
| 이미지 | `zooearlyacr2408.azurecr.io/zooearly-gateway:v1` |
| 활성 리비전 | `zooearly-gateway--0000003` |
| 리소스 | 0.5 vCPU / 1 GiB, min-replicas 1 / max 3 |

### 만들어진 리소스

| 이름 | 역할 |
|---|---|
| `zooearlyacr2408` | 컨테이너 이미지 보관소 (ACR Basic) |
| `zooearly-env` | Container Apps 실행 환경 |
| `zooearly-gateway` | 게이트웨이 앱 |
| `workspace-zooearlyrgL3t2` | 로그 저장소 (환경 생성 시 자동 생성) |

### ⚠️ 리전이 `koreacentral`이 아닌 이유

**학생 구독은 `koreacentral`에 리소스를 만들 수 없다.** 정책으로 막혀 있다.

```
(RequestDisallowedByAzure) This policy maintains a set of best available
regions where your subscription can deploy resources.
```

리소스 그룹은 `koreacentral`로 만들어져서 되는 줄 알았다가 **실제 리소스 생성에서 막혔다.** 허용되는 리전 중 한국에서 지연이 가장 적은 `japaneast`를 골랐다.

FastAPI 쪽 배포 계획(`DEPLOY_azure.md`)은 `koreacentral` 기준이다. **그쪽도 학생 구독이면 같은 벽에 부딪힌다.** 두 서버가 다른 리전이면 그만큼 왕복 지연이 붙으므로 **`japaneast`로 맞추는 편이 낫다.**

아동 음성을 국내에서 처리하는 편이 바람직하다는 논의가 있었으나 학생 구독으로는 선택지가 없다. 유료 구독으로 옮기면 `koreacentral`이 열린다.

---

## 이번에 들어간 변경

| 커밋 | 내용 |
|---|---|
| `cf7b7b5` | Dockerfile 추가 + 배포 방식을 Azure Container Apps로 확정 |
| `a81a76c` | 배포 전 테스트 4단계 정리 + `tools/smoke-test.sh` |
| `bcf62d1` | CORS 설정 (환경변수 기반, 기본 비활성) |
| `b5c4cf3` | 요청 접근 로그 |
| `5e207df` | 문서: 실제 배포 결과 반영 |
| `3ca1792` | 문서: 프론트 도메인 CORS 허용 |

> **실행 중인 이미지는 `b5c4cf3` 시점의 코드다.** 이후 두 커밋은 주석·문서만 고쳐서 동작이 같다. 그래서 재배포하지 않았다.

### Dockerfile — 베이스 이미지는 실측으로 골랐다

멀티스테이지 빌드로 최종 이미지에서 빌드 도구를 뺐다.

| 런타임 베이스 | 크기 | 기동 |
|---|---|---|
| `eclipse-temurin:17-jre-jammy` | 460 MB | 정상 |
| **`eclipse-temurin:17-jre-alpine`** ✅ | **342 MB** | 정상 (2.5초) |

둘 다 기동·응답을 확인한 뒤 작은 쪽을 택했다. 이 서버는 네이티브 라이브러리를 쓰지 않아 alpine(musl libc)에서 문제될 지점이 없다.

`MaxRAMPercentage=75`를 준 이유는 JVM이 기본적으로 컨테이너 메모리의 25%만 힙으로 잡기 때문이다. 1 GiB에서 힙이 256 MB가 되어버린다.

### 접근 로그를 넣은 이유

스프링은 정상 요청을 로그에 남기지 않는다. 그래서 앱을 붙여놓고도 **"지금 통신이 오고 있나"를 서버에서 확인할 방법이 없었다.**

```
10:27:24 INFO  POST /api/v1/ai/tts → 200 (127ms) from 172.17.0.1
10:27:24 WARN  POST /api/v1/ai/tts → 400 (74ms) from 172.17.0.1
```

호출 IP를 함께 남긴다. 어느 기기에서 붙었는지 봐야 연동 문제를 가릴 수 있다.
**본문은 남기지 않는다** — 아이 음성과 발화 내용이 로그에 쌓이면 안 된다. "오디오 원본을 저장하지 않는다"는 설계와 같은 이유다.

### CORS

프론트가 **Azure Static Website로 배포되면서** 실서버 필수 설정이 됐다. React Native 네이티브였다면 브라우저가 아니라서 필요 없었을 값이다.

```
CORS_ALLOWED_ORIGINS=https://stzooearlyfe.z12.web.core.windows.net
```

**와일드카드(`*`)를 쓰지 않는다.** 허용하지 않은 오리진은 preflight에서 403으로 막힌다. 허용 오리진은 배포 환경마다 다르므로 코드에 박지 않고 환경변수로만 주입한다 — **프론트 주소가 바뀌어도 재배포가 필요 없다.**

---

## 검증 결과

배포된 주소로 실제 확인한 것.

| 확인 | 결과 |
|---|---|
| 앱 실행 상태 | `Running`, 인스턴스 1개 |
| 검증 실패 응답 (400) | 명세 포맷 그대로, `field: "text"`까지 정확 |
| 응답 시간 | 0.15초 |
| CORS preflight | 200 + `access-control-allow-origin` |
| 허용 안 한 오리진 | 403 (차단) |
| 이미지 갱신 시 환경변수 | **보존됨** |

마지막 항목이 중요하다. `az containerapp update --image`가 환경변수를 날렸다면 **코드를 배포할 때마다 FastAPI 연결이 끊겼을 것이다.** 실제로 같은 명령을 돌려 확인했다.

---

## 알려진 제약

### FastAPI가 아직 없다 → 추론 요청은 502

`INFERENCE_BASE_URL`이 임시값이라 추론이 필요한 요청은 `502 AI_SERVER_ERROR`가 난다. **의도된 정상 동작이다.**

| 요청 | 지금 |
|---|---|
| 필수값 누락 | **400** — 게이트웨이 검증 정상 |
| 정상 요청 | **502** — FastAPI 미배포 |

앱은 502에서 `"괜찮아, 다시 해볼까?"` 폴백을 띄우면 된다. **화면 전환과 에러 처리 배선은 지금 상태로도 전부 확인할 수 있다.**

FastAPI 주소가 나오면 이렇게 연결한다.

```bash
az containerapp update -n zooearly-gateway -g zooearly-rg \
  --set-env-vars INFERENCE_BASE_URL="https://<FastAPI 주소>" \
                 INFERENCE_API_KEY=secretref:inference-api-key

az containerapp secret set -n zooearly-gateway -g zooearly-rg \
  --secrets inference-api-key="<FastAPI가 발급한 키>"
```

연결 후 `./tools/smoke-test.sh https://<FQDN>` 로 6개 시나리오를 한 번에 확인한다.

### CD는 아직 붙지 않았다

`deploy.yml`이 아직 없어서 **배포는 수동이다.** Azure 인증(OIDC)은 이미 설정을 마쳤다.

---

## 운영

### 재배포 (수동)

```bash
az acr login --name zooearlyacr2408
docker build -t zooearlyacr2408.azurecr.io/zooearly-gateway:v2 .
docker push zooearlyacr2408.azurecr.io/zooearly-gateway:v2
az containerapp update -n zooearly-gateway -g zooearly-rg \
  --image zooearlyacr2408.azurecr.io/zooearly-gateway:v2
```

**태그를 `v1`, `v2`로 올려간다. `latest`를 쓰지 않는다** — 지금 서버에 뭐가 떠 있는지 알 수 없어지고 되돌릴 수도 없다.

### 롤백

```bash
az containerapp update -n zooearly-gateway -g zooearly-rg \
  --image zooearlyacr2408.azurecr.io/zooearly-gateway:v1
```

### 상태 확인

```bash
az containerapp show -n zooearly-gateway -g zooearly-rg --query properties.runningStatus -o tsv
az containerapp logs show -n zooearly-gateway -g zooearly-rg --follow
```

포털의 `Log stream`에서도 같은 로그를 볼 수 있다.

---

## 비용

| 항목 | 대략 |
|---|---|
| Container Apps (0.5 vCPU / 1 GiB, min-replicas 1) | 월 1~2만원 |
| ACR Basic | 월 약 $5 |

Azure for Students **$100 크레딧**에서 차감된다. 몇 달은 충분하다.

`min-replicas`를 0이 아니라 **1로 둔 이유**는, 0이면 요청이 없을 때 인스턴스가 내려가고 그 뒤 첫 요청이 기동을 기다려 느려지기 때문이다. **발표 중에 이런 일이 나면 곤란하다.**

- Cost Management → **Budgets**에서 예산 알림을 걸어둘 것
- 프로젝트가 끝나면 `az group delete --name zooearly-rg` 로 한 번에 정리한다

---

## 다음

- [ ] `deploy.yml` 추가 — `main` 머지 시 자동 배포
- [ ] FastAPI 배포 후 `INFERENCE_BASE_URL` · `INFERENCE_API_KEY` 연결
- [ ] 예산 알림 설정
- [ ] 동화 API — 명세 나오면 엔드포인트 추가 (사전 확인 필요)
