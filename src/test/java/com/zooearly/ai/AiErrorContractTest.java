package com.zooearly.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zooearly.ai.client.InferenceClient;
import com.zooearly.common.exception.InferencePassthroughException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * 명세 §1.2 / §1.3 에러 계약 회귀 테스트.
 *
 * 앱이 실제로 의존하는 건 "실패했을 때 어떤 코드가 오느냐"다.
 * 정상 경로만 테스트하면 여기 담긴 버그들(504가 500으로 나가던 것 등)을 놓친다.
 *
 * InferenceClient를 목으로 바꿔 FastAPI 없이 장애 상황을 재현한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiErrorContractTest {

    private static final String CHAT = "/api/v1/ai/chat";
    private static final String STT = "/api/v1/ai/stt";
    private static final String TTS = "/api/v1/ai/tts";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InferenceClient inferenceClient;

    private MockMultipartFile audio(String filename, int bytes) {
        return new MockMultipartFile("audio", filename, "audio/m4a", new byte[bytes]);
    }

    // ── FastAPI 장애 상황 ─────────────────────────────────────

    @Test
    @DisplayName("응답 body를 읽는 중 read timeout → 504 AI_TIMEOUT (500이 아니다)")
    void readTimeoutBecomesGatewayTimeout() throws Exception {
        // RestClient는 body를 읽는 중 타임아웃이면 ResourceAccessException이 아니라
        // 평범한 RestClientException으로 감싼다. 이 경우를 놓쳐서 500이 나갔었다
        given(inferenceClient.postJson(anyString(), anyString()))
                .willThrow(new RestClientException("Error while extracting response",
                        new SocketTimeoutException("Read timed out")));

        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"));
    }

    @Test
    @DisplayName("연결 단계 타임아웃 → 504 AI_TIMEOUT")
    void connectTimeoutBecomesGatewayTimeout() throws Exception {
        given(inferenceClient.postJson(anyString(), anyString()))
                .willThrow(new ResourceAccessException("timeout",
                        new SocketTimeoutException("Connect timed out")));

        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"));
    }

    @Test
    @DisplayName("FastAPI 연결 거부 → 502 AI_SERVER_ERROR")
    void connectionRefusedBecomesBadGateway() throws Exception {
        given(inferenceClient.postJson(anyString(), anyString()))
                .willThrow(new ResourceAccessException("refused",
                        new ConnectException("Connection refused")));

        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("AI_SERVER_ERROR"));
    }

    @Test
    @DisplayName("FastAPI의 422 STT_FAILED는 body를 그대로 통과시킨다 — §1.3")
    void passthroughKeepsFastApiBody() throws Exception {
        String body = "{\"success\":false,\"error\":{\"code\":\"STT_FAILED\",\"message\":\"엔진 오류\",\"field\":null}}";
        given(inferenceClient.postMultipart(anyString(), any()))
                .willThrow(new InferencePassthroughException(422, body));

        mvc.perform(multipart(STT).file(audio("a.m4a", 100)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json(body));
    }

    // ── 클라이언트가 잘못 보낸 요청 (전부 500이 아니어야 한다) ──

    @Test
    @DisplayName("허용하지 않는 메서드 → 400 INVALID_PARAMETER (500이 아니다)")
    void wrongMethodIsClientError() throws Exception {
        mvc.perform(get(TTS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("잘못된 Content-Type → 400 INVALID_PARAMETER (500이 아니다)")
    void wrongContentTypeIsClientError() throws Exception {
        mvc.perform(post(TTS).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("body 없는 POST → 400 INVALID_PARAMETER (500이 아니다)")
    void missingBodyIsClientError() throws Exception {
        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("깨진 JSON → 400 INVALID_PARAMETER")
    void malformedJsonIsClientError() throws Exception {
        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON).content("{\"text\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    // ── 게이트웨이 검증 (§1.4) ────────────────────────────────

    @Test
    @DisplayName("오디오 10MB 초과 → 400 AUDIO_TOO_LARGE (413이 아니다)")
    void oversizedAudioIsAudioTooLarge() throws Exception {
        mvc.perform(multipart(STT).file(audio("big.m4a", 10 * 1024 * 1024 + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUDIO_TOO_LARGE"))
                .andExpect(jsonPath("$.error.field").value("audio"));
    }

    @Test
    @DisplayName("지원하지 않는 오디오 포맷 → 400 UNSUPPORTED_AUDIO_FORMAT")
    void unsupportedAudioFormat() throws Exception {
        mvc.perform(multipart(STT).file(audio("voice.mp3", 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_AUDIO_FORMAT"));
    }

    @Test
    @DisplayName("audio 파트 누락 → 400 INVALID_PARAMETER + field=audio")
    void missingAudioPart() throws Exception {
        mvc.perform(multipart(STT).param("language", "KOREAN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.error.field").value("audio"));
    }

    @Test
    @DisplayName("chat: history 누락 → 400 + field=history")
    void chatMissingHistory() throws Exception {
        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100)).param("scenario", "LUNCH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("history"));
    }

    @Test
    @DisplayName("chat: 정의되지 않은 scenario → 400 + field=scenario")
    void chatInvalidScenario() throws Exception {
        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100))
                        .param("scenario", "NOPE").param("history", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("scenario"));
    }

    @Test
    @DisplayName("tts: text 누락 / speed 범위 밖 → 400 + 어떤 필드인지 알려준다")
    void ttsFieldValidation() throws Exception {
        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("text"));

        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"안녕\",\"speed\":9.9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("speed"));
    }

    // ── 정상 경로 ─────────────────────────────────────────────

    @Test
    @DisplayName("chat 성공: FastAPI body를 가공 없이 통과 + X-Audio-Retention — §2 계약 3")
    void chatSuccessPassesBodyThroughUntouched() throws Exception {
        String fastApiBody = "{\"success\":true,\"data\":{\"userText\":null,\"aiText\":\"응!\"}}";
        given(inferenceClient.postMultipartChat(anyString(), any())).willReturn(fastApiBody);

        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100))
                        .param("scenario", "LUNCH").param("history", "[]"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Audio-Retention", "none"))
                .andExpect(content().json(fastApiBody));
    }

    @Test
    @DisplayName("history가 빈 배열이면 유효한 값이다 — §2")
    void emptyHistoryIsValid() throws Exception {
        given(inferenceClient.postMultipartChat(anyString(), any())).willReturn("{\"success\":true}");

        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100))
                        .param("scenario", "ARRIVAL").param("history", "[]"))
                .andExpect(status().isOk());
    }
}
