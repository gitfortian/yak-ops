package io.yak.ops.business.datasource.exception;

import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.ErrorCode;
import io.yak.ops.common.util.SensitiveUtils;

/**
 * Represents Datasource business failures across service and plugin boundaries.
 *
 * <p>Preserves a structured error code and masks credentials before exposing error messages.
 */
public class DataSourceException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Structured Datasource error code exposed to callers. */
    private final ErrorCode actualErrorCode;

    /** User-visible message after masking sensitive connection information. */
    private final String userMessage;

    public DataSourceException(ErrorCode errorCode) {
        super(errorCode);
        this.actualErrorCode = errorCode;
        this.userMessage = errorCode == null ? null : errorCode.getMessage();
    }

    public DataSourceException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    public DataSourceException(ErrorCode errorCode, String detail, Throwable cause) {
        super(buildMessage(errorCode, detail), cause);
        this.actualErrorCode = errorCode;
        this.userMessage = buildMessage(errorCode, detail);
    }

    @Override
    public ErrorCode getErrorCode() {
        return actualErrorCode;
    }

    /** @return safe user-visible message with sensitive values masked */
    public String getUserMessage() {
        return userMessage;
    }

    private static String buildMessage(ErrorCode errorCode, String detail) {
        String base = errorCode == null ? "数据源操作失败" : errorCode.getMessage();
        String message = detail == null || detail.trim().isEmpty() ? base : base + "：" + detail.trim();
        return SensitiveUtils.mask(message);
    }
}
