package com.zooearly.common.exception;

/**
 * FastAPI가 §1.2 포맷으로 직접 만든 에러(422 STT_FAILED, 429 RATE_LIMITED)를
 * 상태 코드·body 그대로 앱에 통과시키기 위한 예외.
 */
public class InferencePassthroughException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public InferencePassthroughException(int statusCode, String body) {
        super("inference error passthrough: " + statusCode);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
