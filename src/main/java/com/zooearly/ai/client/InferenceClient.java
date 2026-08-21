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
 * - 기본 클라이언트: connect 3s / read 15s  (stt, tts, feedback)
 * - chat 클라이언트: connect 3s / read 30s  (STT+LLM+TTS 3단이라 길다)
 *
 * body는 가공하지 않고 String으로 통과시킨다 — §0.1 릴레이 계약.
 * FastAPI 응답 스키마가 바뀌어도 게이트웨이는 재배포할 필요가 없다.
 */
@Component
public class InferenceClient {

    private final RestClient defaultClient;
    private final RestClient chatClient;

    public InferenceClient(
            @Value("${inference.base-url}") String baseUrl,
            @Value("${inference.timeout.connect-seconds}") long connectSeconds,
            @Value("${inference.timeout.read-seconds}") long readSeconds,
            @Value("${inference.timeout.chat-read-seconds}") long chatReadSeconds) {
        this.defaultClient = build(baseUrl, connectSeconds, readSeconds);
        this.chatClient = build(baseUrl, connectSeconds, chatReadSeconds);
    }

    private RestClient build(String baseUrl, long connectSeconds, long readSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** tts, feedback — JSON body 그대로 전달 */
    public String postJson(String path, String rawJsonBody) {
        return exchange(defaultClient, path, MediaType.APPLICATION_JSON, rawJsonBody);
    }

    /** stt — multipart 전달 */
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
            // 422/429만 FastAPI가 만든 body를 그대로 통과시킨다.
            // 나머지 4xx/5xx는 손대지 않고 그대로 올려보내면
            // GlobalExceptionHandler가 AI_SERVER_ERROR(502)로 감싼다 — §1.3
            int status = e.getStatusCode().value();
            if (status == 422 || status == 429) {
                throw new InferencePassthroughException(status, e.getResponseBodyAsString());
            }
            throw e;
        }
    }

}
