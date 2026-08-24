package com.zooearly.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * InferenceClient가 FastAPI로 실제로 X-API-Key 헤더를 붙이는지 확인한다.
 *
 * FastAPI 쪽 배포 가이드(DEPLOY_azure.md §4)가 이 헤더로 인증하도록 정해뒀는데,
 * 게이트웨이가 안 보내면 배포 즉시 모든 요청이 401로 죽는다 — 실서버 캡처로
 * 한 번 확인한 것을 회귀 테스트로 고정해둔다.
 *
 * JDK 내장 HttpServer만 쓴다. WireMock 같은 테스트 의존성을 새로 추가할 만큼
 * 복잡한 검증이 아니다.
 */
class InferenceClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServerCapturing(AtomicReference<String> capturedHeader) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            capturedHeader.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            byte[] body = "{\"success\":true,\"data\":{}}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    @DisplayName("api-key가 설정되면 X-API-Key 헤더를 붙인다")
    void sendsApiKeyHeaderWhenConfigured() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        String baseUrl = startServerCapturing(captured);

        InferenceClient client = new InferenceClient(baseUrl, "secret-abc-123", 3, 5, 5, 5);
        client.postJson("/tts", "{}");

        assertThat(captured.get()).isEqualTo("secret-abc-123");
    }

    @Test
    @DisplayName("api-key가 비어 있으면 헤더 자체를 안 보낸다 — 로컬 개발·목 서버 호환")
    void omitsHeaderWhenApiKeyBlank() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        String baseUrl = startServerCapturing(captured);

        InferenceClient client = new InferenceClient(baseUrl, "", 3, 5, 5, 5);
        client.postJson("/tts", "{}");

        assertThat(captured.get()).isNull();
    }

    @Test
    @DisplayName("GET(문장 목록)에도 같은 헤더가 실린다")
    void sendsApiKeyHeaderOnGetToo() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        String baseUrl = startServerCapturing(captured);

        InferenceClient client = new InferenceClient(baseUrl, "secret-abc-123", 3, 5, 5, 5);
        client.get("/sentences");

        assertThat(captured.get()).isEqualTo("secret-abc-123");
    }
}
