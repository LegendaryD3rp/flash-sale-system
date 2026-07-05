package com.flashsale.common.exception;

import com.flashsale.common.constant.ErrorCode;

/**
 * Custom business exception with an error code.
 * GlobalExceptionHandler translates this into a Result<Object> response.
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
