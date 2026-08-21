package com.zooearly.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 명세서 §1.2 응답 래퍼.
 * 성공: { "success": true, "data": {...} }
 * 실패: { "success": false, "error": { "code", "message", "field" } }
 *
 * 참고: 릴레이 성공 응답은 FastAPI가 이미 이 포맷으로 만들어 보내므로
 * 게이트웨이가 이 클래스로 성공 응답을 만들 일은 거의 없다.
 * 주 용도는 게이트웨이 발생 에러(GlobalExceptionHandler)다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public record ErrorBody(String code, String message, @JsonInclude(JsonInclude.Include.ALWAYS) String field) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(ErrorCode code, String field) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), code.defaultMessage(), field));
    }

    public static ApiResponse<Void> error(ErrorCode code, String message, String field) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), message, field));
    }
}
