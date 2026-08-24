package com.zooearly.ai.client;

import com.zooearly.common.exception.InferencePassthroughException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * FastAPI 추론 서버 호출 전담. 타임아웃은 명세 §0.4.
 * - 기본 클라이언트: connect 3s / read 15s  (tts, feedback, pronunciation)
 * - stt  클라이언트: connect 3s / read 90s (FastAPI STT가 35~70초 걸린다 — 임시값)
 * - chat 클라이언트: connect 3s / read 30s  (STT+LLM+TTS 3단이라 길다)
 * - story 클라이언트: connect 3s / read 60s (4개 장면을 한 번에 생성해 가장 오래 걸린다)
 *
 * body는 가공하지 않고 String으로 통과시킨다 — §0.1 릴레이 계약.
 * FastAPI 응답 스키마가 바뀌어도 게이트웨이는 재배포할 필요가 없다.
 *
 * 인증: FastAPI가 X-API-Key 헤더를 검사한다 (FastAPI 쪽 DEPLOY_azure.md §4).
 * inference.api-key가 비어 있으면 헤더를 아예 안 보낸다 — FastAPI도 API_KEY가
 * 비어 있으면 인증을 안 하므로(로컬 전용) 로컬 개발·목 서버는 그대로 동작한다.
 */
@Component
public class InferenceClient {

    private final RestClient defaultClient;
    private final RestClient chatClient;
    private final RestClient sttClient;
    private final RestClient storyClient;

    public InferenceClient(
            @Value("${inference.base-url}") String baseUrl,
            @Value("${inference.api-key:}") String apiKey,
            @Value("${inference.timeout.connect-seconds}") long connectSeconds,
            @Value("${inference.timeout.read-seconds}") long readSeconds,
            @Value("${inference.timeout.chat-read-seconds}") long chatReadSeconds,
            @Value("${inference.timeout.stt-read-seconds}") long sttReadSeconds,
            @Value("${inference.timeout.story-read-seconds}") long storyReadSeconds) {
        this.defaultClient = build(baseUrl, apiKey, connectSeconds, readSeconds);
        this.chatClient = build(baseUrl, apiKey, connectSeconds, chatReadSeconds);
        this.sttClient = build(baseUrl, apiKey, connectSeconds, sttReadSeconds);
        this.storyClient = build(baseUrl, apiKey, connectSeconds, storyReadSeconds);
    }

    private RestClient build(String baseUrl, String apiKey, long connectSeconds, long readSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }
        return builder.build();
    }

    /** tts, feedback — JSON body 그대로 전달 */
    public String postJson(String path, String rawJsonBody) {
        return exchange(defaultClient, path, MediaType.APPLICATION_JSON, rawJsonBody);
    }

    /** pronunciation/sentences — 문장 목록 조회. body 없음 */
    public String get(String path) {
        try {
            return defaultClient.get().uri(path).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    /** story — JSON body 그대로 전달, 60s 타임아웃 */
    public String postJsonStory(String path, String rawJsonBody) {
        return exchange(storyClient, path, MediaType.APPLICATION_JSON, rawJsonBody);
    }

    /** stt — multipart 전달, 90s 타임아웃 (FastAPI STT가 느리다) */
    public String postMultipartStt(String path, MultiValueMap<String, Object> parts) {
        return exchange(sttClient, path, MediaType.MULTIPART_FORM_DATA, parts);
    }

    /** pronunciation — multipart 전달 */
    public String postMultipart(String path, MultiValueMap<String, Object> parts) {
        return exchange(defaultClient, path, MediaType.MULTIPART_FORM_DATA, parts);
    }

    /** chat — multipart 전달, 30s 타임아웃 */
    public String postMultipartChat(String path, MultiValueMap<String, Object> parts) {
        return exchange(chatClient, path, MediaType.MULTIPART_FORM_DATA, parts);
    }

    private String exchange(RestClient client, String path, MediaType contentType, Object body) {
        try {
            return client.post()
                    .uri(path)
                    .contentType(contentType)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    /**
     * 422/429만 FastAPI가 만든 body를 그대로 통과시킨다.
     * 나머지 4xx/5xx는 손대지 않고 그대로 올려보내면
     * GlobalExceptionHandler가 AI_SERVER_ERROR(502)로 감싼다 — §1.3
     */
    private RuntimeException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 422 || status == 429) {
            return new InferencePassthroughException(status, e.getResponseBodyAsString());
        }
        return e;
    }

}
