package com.zooearly.common.exception;

import com.zooearly.common.response.ApiResponse;
import com.zooearly.common.response.ErrorCode;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 모든 에러를 API 명세서 §1.2 포맷으로 통일한다.
 * FastAPI의 어떤 생(raw) 에러도 앱에 그대로 새지 않는다 — §0.1 게이트웨이 계약.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 게이트웨이 검증 실패 (400류) */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().status())
                .body(ApiResponse.error(e.getErrorCode(), e.getField()));
    }

    /** FastAPI가 §1.2 포맷으로 만든 에러(STT_FAILED, RATE_LIMITED)는 그대로 통과 */
    @ExceptionHandler(InferencePassthroughException.class)
    public ResponseEntity<String> handlePassthrough(InferencePassthroughException e) {
        return ResponseEntity.status(e.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(e.getBody());
    }

    /** FastAPI 연결 불가·타임아웃 → AI_TIMEOUT(504) / AI_SERVER_ERROR(502) */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceAccess(ResourceAccessException e) {
        boolean timeout = e.getCause() instanceof SocketTimeoutException;
        ErrorCode code = timeout ? ErrorCode.AI_TIMEOUT : ErrorCode.AI_SERVER_ERROR;
        log.warn("inference unreachable ({}): {}", code, e.getMessage());
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, null));
    }

    /** multipart 필수 파트 누락 → INVALID_PARAMETER */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(Exception e) {
        String field = e instanceof MissingServletRequestPartException p
                ? p.getRequestPartName()
                : ((MissingServletRequestParameterException) e).getParameterName();
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.status())
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER, field));
    }

    /** 요청 전체 용량 초과 → PAYLOAD_TOO_LARGE(413) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.status())
                .body(ApiResponse.error(ErrorCode.PAYLOAD_TOO_LARGE, "audio"));
    }

    /** 그 외 전부 → INTERNAL_ERROR(500) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("unhandled gateway error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, null));
    }
}
