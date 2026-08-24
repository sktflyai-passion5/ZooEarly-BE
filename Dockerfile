# 멀티스테이지 빌드 — 최종 이미지에 JDK·소스·Gradle 캐시를 남기지 않기 위해서다.
# 한 스테이지로 만들면 빌드 도구가 전부 이미지에 들어가 1GB에 가까워진다.

# ── 1단계: 빌드 ────────────────────────────────────────────────
FROM gradle:8.10-jdk17 AS builder

WORKDIR /build

# 의존성 파일을 소스보다 먼저 복사한다.
# 이 레이어는 build.gradle이 바뀔 때만 다시 실행되므로,
# 코드만 고친 재배포에서는 의존성 다운로드를 통째로 건너뛴다.
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true

COPY src ./src
# 테스트는 CI(GitHub Actions)에서 이미 돌렸다. 여기서 또 돌리면 배포만 느려진다.
RUN gradle bootJar --no-daemon -x test

# ── 2단계: 실행 ────────────────────────────────────────────────
# jre(실행 전용) + alpine을 쓴다. 컨테이너 안에서 컴파일할 일이 없으므로 jdk는 낭비다.
# jammy 대신 alpine을 고른 이유는 실측 결과다 — 460MB → 342MB.
# 두 이미지 모두 정상 기동·응답을 확인했고, 이 서버는 네이티브 라이브러리를 쓰지 않아
# alpine(musl libc)에서 문제가 될 지점이 없다.
FROM eclipse-temurin:17-jre-alpine

# root로 돌리지 않는다 — 컨테이너가 뚫렸을 때 피해 범위를 줄인다.
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar
RUN chown spring:spring /app/app.jar

USER spring

EXPOSE 8080

# MaxRAMPercentage — JVM은 기본적으로 컨테이너 메모리의 25%만 힙으로 쓴다.
# 1Gi 컨테이너에서 힙이 256MB가 되어버리므로 75%로 올린다.
# 오디오 multipart가 스트리밍이라 힙을 크게 안 먹지만, 여유는 둔다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
