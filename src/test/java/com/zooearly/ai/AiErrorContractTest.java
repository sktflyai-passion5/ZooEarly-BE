package com.zooearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MultiValueMap;
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
    private static final String FEEDBACK = "/api/v1/ai/feedback";
    private static final String PRONUNCIATION = "/api/v1/ai/pronunciation";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InferenceClient inferenceClient;

    @Value("${inference.path.stt}")
    private String sttPath;

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

        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\",\"language\":\"KOREAN\"}"))
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

        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\",\"language\":\"KOREAN\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"));
    }

    @Test
    @DisplayName("FastAPI 연결 거부 → 502 AI_SERVER_ERROR")
    void connectionRefusedBecomesBadGateway() throws Exception {
        given(inferenceClient.postJson(anyString(), anyString()))
                .willThrow(new ResourceAccessException("refused",
                        new ConnectException("Connection refused")));

        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\",\"language\":\"KOREAN\"}"))
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
                        .param("scenario", "NOPE").param("history", "[]")
                        .param("nickname", "민수"))
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

    // ── nickname (선택 필드) — 명세 §2 / §5 ────────────────────

    @Test
    @DisplayName("chat: nickname을 보내면 FastAPI로 파트가 전달된다")
    void chatRelaysNickname() throws Exception {
        given(inferenceClient.postMultipartChat(anyString(), any())).willReturn("{\"success\":true}");

        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100))
                        .param("scenario", "LUNCH").param("history", "[]")
                        .param("nickname", "민수"))
                .andExpect(status().isOk());

        ArgumentCaptor<MultiValueMap<String, Object>> captor = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(inferenceClient).postMultipartChat(anyString(), captor.capture());
        assertThat(captor.getValue().getFirst("nickname")).isEqualTo("민수");
    }

    @Test
    @DisplayName("chat: nickname을 생략하면 400 + field=nickname")
    void chatRequiresNickname() throws Exception {
        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100))
                        .param("scenario", "LUNCH").param("history", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.error.field").value("nickname"));
    }

    @Test
    @DisplayName("chat: nickname이 공백뿐이면 400 — 호칭으로 쓸 수 없다")
    void chatRejectsBlankNickname() throws Exception {
        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100))
                        .param("scenario", "LUNCH").param("history", "[]")
                        .param("nickname", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("nickname"));
    }

    @Test
    @DisplayName("feedback: nickname 키가 없으면 400 + field=nickname")
    void feedbackRequiresNickname() throws Exception {
        String body = "{\"targetSentence\":\"많이 주세요.\",\"recognizedText\":null}";
        mvc.perform(post(FEEDBACK).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("nickname"));
    }

    @Test
    @DisplayName("chat: nickname이 20자를 넘으면 400 + field=nickname")
    void chatRejectsTooLongNickname() throws Exception {
        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100))
                        .param("scenario", "LUNCH").param("history", "[]")
                        .param("nickname", "가".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.error.field").value("nickname"));
    }

    @Test
    @DisplayName("feedback: nickname이 20자를 넘으면 400 + field=nickname")
    void feedbackRejectsTooLongNickname() throws Exception {
        String body = "{\"targetSentence\":\"많이 주세요.\",\"recognizedText\":null,\"nickname\":\""
                + "가".repeat(21) + "\"}";
        mvc.perform(post(FEEDBACK).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("nickname"));
    }

    @Test
    @DisplayName("feedback: nickname이 문자열이 아니면 400 + field=nickname")
    void feedbackRejectsNonTextNickname() throws Exception {
        String body = "{\"targetSentence\":\"많이 주세요.\",\"recognizedText\":null,\"nickname\":123}";
        mvc.perform(post(FEEDBACK).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("nickname"));
    }

    @Test
    @DisplayName("feedback: nickname은 body에 실려 그대로 통과한다")
    void feedbackPassesNicknameThrough() throws Exception {
        String body = "{\"targetSentence\":\"많이 주세요.\",\"recognizedText\":null,\"nickname\":\"민수\"}";
        given(inferenceClient.postJson(anyString(), anyString())).willReturn("{\"success\":true}");

        mvc.perform(post(FEEDBACK).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // body는 가공 없이 통과한다 — §0.1 릴레이 계약
        verify(inferenceClient).postJson(anyString(), eq(body));
    }

    // ── tts language (선택 필드) — 명세 §4 ─────────────────────

    @Test
    @DisplayName("tts: 정의되지 않은 language → 400 + field=language")
    void ttsRejectsUnknownLanguage() throws Exception {
        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"안녕\",\"language\":\"ENGLISH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("language"));
    }

    @Test
    @DisplayName("tts: 모국어 문장도 같은 엔드포인트로 통과한다 — body 가공 없음")
    void ttsRelaysNativeLanguageSentence() throws Exception {
        String body = "{\"text\":\"Cho mình nhiều nhé.\",\"language\":\"VIETNAMESE\"}";
        given(inferenceClient.postJson(anyString(), anyString())).willReturn("{\"success\":true}");

        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(inferenceClient).postJson(anyString(), eq(body));
    }

    @Test
    @DisplayName("tts: language를 생략하면 400 + field=language")
    void ttsRequiresLanguage() throws Exception {
        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"안녕\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.error.field").value("language"));
    }

    @Test
    @DisplayName("tts: 한국어 문장에도 language를 명시해야 통과한다")
    void ttsAcceptsKoreanWithExplicitLanguage() throws Exception {
        given(inferenceClient.postJson(anyString(), anyString())).willReturn("{\"success\":true}");

        mvc.perform(post(TTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"안녕\",\"language\":\"KOREAN\"}"))
                .andExpect(status().isOk());
    }

    // ── pronunciation (발음 채점) — 명세 §6 ────────────────────

    @Test
    @DisplayName("pronunciation: 오디오를 STT 없이 그대로 릴레이한다")
    void pronunciationRelaysAudioDirectly() throws Exception {
        String body = "{\"success\":true,\"data\":{\"targetWord\":\"지내자\"}}";
        given(inferenceClient.postMultipart(anyString(), any())).willReturn(body);

        mvc.perform(multipart(PRONUNCIATION).file(audio("a.m4a", 100))
                        .param("sentenceId", "arrival_2"))
                .andExpect(status().isOk())
                .andExpect(content().json(body));

        ArgumentCaptor<MultiValueMap<String, Object>> captor = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(inferenceClient).postMultipart(anyString(), captor.capture());
        assertThat(captor.getValue().getFirst("sentenceId")).isEqualTo("arrival_2");
        assertThat(captor.getValue().containsKey("audio")).isTrue();
    }

    @Test
    @DisplayName("pronunciation: sentenceId 누락 → 400 + field=sentenceId")
    void pronunciationRequiresSentenceId() throws Exception {
        mvc.perform(multipart(PRONUNCIATION).file(audio("a.m4a", 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("sentenceId"));
    }

    @Test
    @DisplayName("pronunciation: 오디오 검증은 다른 엔드포인트와 동일하다")
    void pronunciationValidatesAudio() throws Exception {
        mvc.perform(multipart(PRONUNCIATION).file(audio("voice.mp3", 100))
                        .param("sentenceId", "arrival_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_AUDIO_FORMAT"));

        mvc.perform(multipart(PRONUNCIATION).file(audio("big.m4a", 10 * 1024 * 1024 + 1))
                        .param("sentenceId", "arrival_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUDIO_TOO_LARGE"));
    }

    @Test
    @DisplayName("sentences: FastAPI 응답을 body 없이 그대로 GET 릴레이한다")
    void sentencesRelaysGetRequest() throws Exception {
        String body = "[{\"sentenceId\":\"arrival_1\",\"category\":\"arrival\",\"text\":\"안녕 나도 만나서 반가워 !\"}]";
        given(inferenceClient.get(anyString())).willReturn(body);

        mvc.perform(get("/api/v1/ai/pronunciation/sentences"))
                .andExpect(status().isOk())
                .andExpect(content().json(body));
    }

    @Test
    @DisplayName("FastAPI 경로는 설정에서 주입된다 — 앱 계약과 무관하게 바뀔 수 있다")
    void relayUsesConfiguredFastApiPath() throws Exception {
        given(inferenceClient.postMultipart(anyString(), any())).willReturn("{\"success\":true}");

        mvc.perform(multipart(STT).file(audio("a.m4a", 100)))
                .andExpect(status().isOk());

        // 앱은 /api/v1/ai/stt 로 부르지만, FastAPI 로는 설정된 경로로 나간다
        verify(inferenceClient).postMultipart(eq(sttPath), any());
    }

    // ── 정상 경로 ─────────────────────────────────────────────

    @Test
    @DisplayName("chat 성공: FastAPI body를 가공 없이 통과 + X-Audio-Retention — §2 계약 3")
    void chatSuccessPassesBodyThroughUntouched() throws Exception {
        String fastApiBody = "{\"success\":true,\"data\":{\"userText\":null,\"aiText\":\"응!\"}}";
        given(inferenceClient.postMultipartChat(anyString(), any())).willReturn(fastApiBody);

        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100))
                        .param("scenario", "LUNCH").param("history", "[]")
                        .param("nickname", "민수"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Audio-Retention", "none"))
                .andExpect(content().json(fastApiBody));
    }

    @Test
    @DisplayName("history가 빈 배열이면 유효한 값이다 — §2")
    void emptyHistoryIsValid() throws Exception {
        given(inferenceClient.postMultipartChat(anyString(), any())).willReturn("{\"success\":true}");

        mvc.perform(multipart(CHAT).file(audio("a.m4a", 100))
                        .param("scenario", "ARRIVAL").param("history", "[]")
                        .param("nickname", "민수"))
                .andExpect(status().isOk());
    }
}
