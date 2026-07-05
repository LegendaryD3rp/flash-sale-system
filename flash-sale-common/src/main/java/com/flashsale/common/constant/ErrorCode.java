package com.flashsale.common.constant;

/**
 * Error codes used across all FlashSale services.
 * Maps to standard HTTP status semantics where appropriate.
 */
public enum ErrorCode {

    SUCCESS(0, "Success"),
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Unauthorized — missing or invalid token"),
    FORBIDDEN(403, "Forbidden — insufficient permissions"),
    NOT_FOUND(404, "Resource not found"),
    CONFLICT(409, "Conflict — resource already exists or state conflict"),
    TOO_MANY_REQUESTS(429, "Too many requests — rate limited"),
    INTERNAL_ERROR(500, "Internal server error"),
    SERVICE_UNAVAILABLE(503, "Service temporarily unavailable");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
