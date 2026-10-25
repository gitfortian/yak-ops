package io.yak.ops.common.exception;

import io.yak.ops.common.result.ErrorCode;

/**
 * 跨模块业务异常，保留结构化错误码以避免从异常文本反向解析。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, null, cause);
    }

    /**
     * Carries a human-readable context beyond the static message, e.g. the character position of a
     * parse failure; the exception handler appends it to the error message.
     */
    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    private BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(formatMessage(errorCode), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public BusinessException(String message) {
        super(message);
        this.errorCode = null;
        this.detail = null;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.detail = null;
    }

    public BusinessException(Throwable cause) {
        super(cause);
        this.errorCode = null;
        this.detail = null;
    }

    private static String formatMessage(ErrorCode errorCode) {
        if (errorCode == null) {
            return null;
        }
        return errorCode.getCode() + "-" + errorCode.getMessage();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Human-readable context beyond the static message, or null when absent. */
    public String getDetail() {
        return detail;
    }
}
