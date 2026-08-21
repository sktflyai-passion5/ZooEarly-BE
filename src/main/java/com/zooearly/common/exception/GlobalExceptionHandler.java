package com.zooearly.common.exception;

import com.zooearly.common.response.ApiResponse;
import com.zooearly.common.response.ErrorCode;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 모든 에러를 API 명세서 §1.2 포맷으로 통일한다.
 * FastAPI의 어떤 생(raw) 에러도 앱에 그대로 새지 않는다 — §0.1 게이트웨이 계약.
 *
 * 핸들러를 고를 때 Spring은 "가장 구체적인 예외 타입"을 먼저 쓴다.
 * 따라서 맨 아래 Exception 핸들러는 진짜 예상 못 한 것만 받아야 하고,
 * 클라이언트 실수(405/415/body 누락)는 반드시 위에서 걸러야 한다 — 아니면 전부 500이 된다.
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

    /**
     * FastAPI 호출 실패 전부 → AI_TIMEOUT(504) 또는 AI_SERVER_ERROR(502). 명세 §0.4 / §1.3
     *
     * 예외 "타입"이 아니라 "원인 사슬"로 타임아웃을 판별하는 이유:
     * RestClient는 연결 단계에서 늦으면 ResourceAccessException을 던지지만,
     * 응답 body를 읽는 도중 늦으면 평범한 RestClientException으로 감싼다.
     * 타입만 보면 후자를 놓쳐 504가 500으로 나간다 (실제로 그랬다).
     * RestClientResponseException(FastAPI가 4xx/5xx를 준 경우)도 이 타입의 하위라 여기서 함께 받는다.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleInferenceFailure(RestClientException e) {
        ErrorCode code = causedByTimeout(e) ? ErrorCode.AI_TIMEOUT : ErrorCode.AI_SERVER_ERROR;
        log.warn("inference call failed ({}): {}", code, e.getMessage());
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, null));
    }

    /** multipart 필수 파트·파라미터 누락 → INVALID_PARAMETER. 어떤 필드인지 앱에 알려준다 */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(Exception e) {
        String field = e instanceof MissingServletRequestPartException p
                ? p.getRequestPartName()
                : ((MissingServletRequestParameterException) e).getParameterName();
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.status())
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER, field));
    }

    /**
     * 요청 전체 용량 초과 → PAYLOAD_TOO_LARGE(413).
     * 오디오 10MB 초과는 AiRelayService가 AUDIO_TOO_LARGE(400)로 먼저 잡는다.
     * 여기까지 왔다는 건 게이트웨이 검증 한계를 넘은 비정상 업로드라는 뜻이다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.status())
                .body(ApiResponse.error(ErrorCode.PAYLOAD_TOO_LARGE, "audio"));
    }

    /**
     * 잘못된 메서드·Content-Type·읽을 수 없는 body → INVALID_PARAMETER(400).
     *
     * 이 셋은 Spring이 던지는 "클라이언트가 잘못 보냈다" 예외다.
     * 잡지 않으면 아래 Exception 핸들러가 삼켜 500이 나가는데,
     * 500은 "서버가 죽었다"는 신호라 앱이 불필요한 재시도를 하고 장애 알람도 오염된다.
     */
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.debug("bad request: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.status())
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER, null));
    }

    /** 그 외 전부 → INTERNAL_ERROR(500). 여기에 찍히면 게이트웨이 버그로 보고 조사한다 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("unhandled gateway error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, null));
    }

    /** 원인 사슬을 끝까지 훑어 SocketTimeoutException을 찾는다 */
    private static boolean causedByTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                return true;
            }
            if (t.getCause() == t) {   // 자기 자신을 원인으로 갖는 예외 방어 (무한 루프)
                break;
            }
        }
        return false;
    }
}
