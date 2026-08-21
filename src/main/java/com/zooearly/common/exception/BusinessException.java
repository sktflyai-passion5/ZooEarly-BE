package com.zooearly.common.exception;

import com.zooearly.common.response.ErrorCode;

/** 게이트웨이 검증 단계에서 던지는 예외. GlobalExceptionHandler가 §1.2 포맷으로 변환한다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;

    public BusinessException(ErrorCode errorCode, String field) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.field = field;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getField() {
        return field;
    }
}
