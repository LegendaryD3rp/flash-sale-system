package com.flashsale.common.exception;

import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for all FlashSale microservices.
 * Catches BusinessException and common framework exceptions,
 * returning unified Result<T> responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * BusinessException → Result with the exception's own code + message.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusinessException(BusinessException ex) {
        log.warn("BusinessException: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.valueOf(ex.getCode() >= 1000 ? 500 : ex.getCode()))
                .body(Result.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * Validation errors (@Valid / @Validated) → 400.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation failed: {}", detail);
        return ResponseEntity
                .badRequest()
                .body(Result.error(ErrorCode.BAD_REQUEST, detail));
    }

    /**
     * Catch-all for unhandled exceptions → 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCode.INTERNAL_ERROR));
    }
}
