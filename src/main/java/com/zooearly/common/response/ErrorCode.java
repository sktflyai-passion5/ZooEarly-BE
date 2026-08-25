package com.zooearly.common.response;

import org.springframework.http.HttpStatus;

/**
 * API 명세서 §1.3 에러 코드.
 * STT_FAILED / RATE_LIMITED 는 FastAPI가 직접 만들어 보내고 게이트웨이는 통과시킨다.
 */
public enum ErrorCode {

    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "필수 파라미터가 누락되었거나 형식이 잘못되었습니다."),
    UNSUPPORTED_AUDIO_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 오디오 포맷입니다. (m4a/wav/webm)"),
    AUDIO_TOO_LARGE(HttpStatus.BAD_REQUEST, "음성 파일이 10MB를 초과했습니다."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "요청 전체 용량이 초과되었습니다."),
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "추론 서버가 응답하지 않습니다."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "추론 시간이 초과되었습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 주소가 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "게이트웨이 내부 오류입니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
