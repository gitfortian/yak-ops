package io.yak.ops.boot.exception;

import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.enums.modeling.LayerErrorCode;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.enums.modeling.publication.PublicationErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.ErrorCode;
import io.yak.ops.common.result.Result;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskValidationException;
import io.yak.ops.security.enums.SecurityErrorCode;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** All Controllers share this HTTP exception boundary. */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TaskPluginException.class)
    public ResponseEntity<Result<Void>> handleTaskParameters(TaskPluginException exception) {
        // The exception chain can contain JDBC SQL, connection details or raw JSON. Do not log it.
        Throwable cause = exception.getCause();
        LOG.warn(
                "Task plugin validation rejected, stage={}, exceptionType={}, causeType={}",
                exception instanceof TaskValidationException validation ? validation.stage() : "CONFIG_OR_RESOURCE",
                exception.getClass().getSimpleName(),
                cause == null ? "NONE" : cause.getClass().getSimpleName());
        String publicMessage =
                exception instanceof TaskValidationException validation ? validation.publicMessage() : null;
        return ResponseEntity.badRequest()
                .body(
                        publicMessage == null
                                ? Result.fail(CommonErrorCode.PARAM_NOT_VALID)
                                : Result.fail(CommonErrorCode.PARAM_NOT_VALID.getCode(), publicMessage));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode == null) {
            LOG.warn(
                    "Business exception has no error code, exceptionType={}",
                    exception.getClass().getSimpleName());
            String message = exception.getMessage();
            Result<Object> body = message == null || message.isBlank()
                    ? Result.fail(CommonErrorCode.PARAM_NOT_VALID)
                    : Result.fail(CommonErrorCode.PARAM_NOT_VALID.getCode(), message);
            return ResponseEntity.badRequest().body(body);
        }

        HttpStatus status = businessStatus(errorCode);
        if (status.is5xxServerError())
            LOG.error("Business request failed, errorCode={}", errorCode.getCode(), exception);
        Result<Object> body;
        if (exception instanceof DataSourceException dataSourceException) {
            body = Result.fail(errorCode.getCode(), dataSourceException.getUserMessage());
        } else if (exception.getDetail() != null) {
            body = Result.fail(errorCode.getCode(), errorCode.getMessage() + ": " + exception.getDetail());
        } else {
            body = Result.fail(errorCode);
        }
        if (exception instanceof DataSourceException datasource
                && errorCode == DataSourceErrorCode.INVALID_CONNECTION_PARAMS
                && !datasource.getValidationErrors().isEmpty()) {
            body.setData(datasource.getValidationErrors());
        }
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<Result<Void>> handleInvalidRequest(Exception exception) {
        LOG.debug(
                "Request validation failed, exceptionType={}",
                exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(Result.fail(CommonErrorCode.PARAM_NOT_VALID));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.BAD_REQUEST : status;
        ErrorCode errorCode =
                switch (resolved) {
                    case NOT_FOUND -> CommonErrorCode.RESOURCE_NOT_EXISTS;
                    case CONFLICT -> CommonErrorCode.RESOURCE_DUPLICATION;
                    default ->
                        resolved.is4xxClientError() ? CommonErrorCode.PARAM_NOT_VALID : CommonErrorCode.COMMON_FAIL;
                };
        if (resolved.is5xxServerError()) {
            LOG.error("HTTP status exception handling failed, status={}", resolved.value(), exception);
        }
        return ResponseEntity.status(resolved).body(Result.fail(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        LOG.error("Unexpected exception while processing request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(CommonErrorCode.COMMON_FAIL));
    }

    private static HttpStatus businessStatus(ErrorCode errorCode) {
        if (errorCode instanceof StandardErrorCode code) {
            return switch (code) {
                case INVALID_CODE,
                        INVALID_OWNER,
                        INVALID_PRECISION,
                        DEPTH_LIMIT,
                        INVALID_PARENT_STANDARD,
                        INVALID_LOOKUP_VALUE -> HttpStatus.BAD_REQUEST;
                // The set's kind is locked by a record that already exists, so the request is
                // well-formed but conflicts with committed state.
                case DUPLICATE_CODE, HAS_DEPENDENCIES, REVISION_CONFLICT, REFERENCED, STANDARD_SET_KIND_LOCKED ->
                    HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof ModelingErrorCode code) {
            return switch (code) {
                case INVALID_CODE, INVALID_OWNER -> HttpStatus.BAD_REQUEST;
                case DUPLICATE_CODE, HAS_DEPENDENCIES, REVISION_CONFLICT -> HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof LayerErrorCode code) {
            return switch (code) {
                case INVALID_CODE, INVALID_DATASOURCE -> HttpStatus.BAD_REQUEST;
                // A missing binding is a state precondition for enabling, not a malformed request.
                case DATASOURCE_REQUIRED -> HttpStatus.UNPROCESSABLE_ENTITY;
                // A rule the layer cannot satisfy yet: no dictionary, an unfit head, or models that
                // still do not conform and were not acknowledged.
                case INVALID_NAMING_RULE -> HttpStatus.UNPROCESSABLE_ENTITY;
                case DUPLICATE_CODE, BUILTIN_IMMUTABLE, BUILTIN_UNDELETABLE, HAS_DEPENDENCIES, REVISION_CONFLICT ->
                    HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof DimensionModelingErrorCode code) {
            return switch (code) {
                case INVALID_CODE,
                        INVALID_SCOPE,
                        INVALID_FIELD,
                        INVALID_MODEL,
                        INVALID_TABLE_NAME,
                        UNKNOWN_MODEL_TYPE,
                        PARTITION_RULE,
                        NAMING_RULE_VIOLATION -> HttpStatus.BAD_REQUEST;
                // A layer that is disabled or unbound is a state precondition, not a malformed request.
                case LAYER_NOT_SELECTABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
                // The physical table exists; only reading it back failed, so this is an upstream fault.
                case METADATA_COLLECTION_FAILED -> HttpStatus.BAD_GATEWAY;
                case DUPLICATE_CODE, DUPLICATE_TABLE_NAME, HAS_DEPENDENCIES, REVISION_CONFLICT -> HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof MetricErrorCode code) {
            return switch (code) {
                case INVALID_CODE,
                        INVALID_OWNER,
                        INVALID_ATOMIC_METRIC,
                        INVALID_DERIVED_METRIC,
                        INVALID_METRIC_EXPRESSION -> HttpStatus.BAD_REQUEST;
                case DUPLICATE_CODE, REVISION_CONFLICT, REFERENCED -> HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof PublicationErrorCode code) {
            return switch (code) {
                // The request is well formed; the record simply is not in a state that allows it.
                case INVALID_PUBLICATION_STATE -> HttpStatus.CONFLICT;
                // Publishing needs the user to accept the loss the plan carries.
                case DANGEROUS_OPERATION_UNCONFIRMED -> HttpStatus.UNPROCESSABLE_ENTITY;
                // The layer has no target bound for this environment yet.
                case TARGET_NOT_BOUND -> HttpStatus.UNPROCESSABLE_ENTITY;
                // The model or data source cannot be published at all until something else changes.
                case UNSUPPORTED_PUBLICATION -> HttpStatus.UNPROCESSABLE_ENTITY;
            };
        }
        if (errorCode == CommonErrorCode.PARAM_NOT_VALID) return HttpStatus.BAD_REQUEST;
        if (errorCode == CommonErrorCode.RESOURCE_NOT_EXISTS) return HttpStatus.NOT_FOUND;
        if (errorCode == CommonErrorCode.RESOURCE_DUPLICATION) return HttpStatus.CONFLICT;
        if (errorCode == CommonErrorCode.COMMON_FAIL) return HttpStatus.INTERNAL_SERVER_ERROR;

        if (errorCode instanceof DataSourceErrorCode code) {
            return switch (code) {
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case DUPLICATE_NAME, IN_USE -> HttpStatus.CONFLICT;
                case INVALID_DB_TYPE,
                        INVALID_ENVIRONMENT,
                        INVALID_CONNECTION_PARAMS,
                        INVALID_BATCH_OPERATION,
                        INVALID_CONNECTION_STATUS -> HttpStatus.BAD_REQUEST;
                // A refused connection answers the submitted settings, not a server fault: the
                // caller gets 400 together with the masked driver reason.
                case CONNECT_FAILED -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }

        if (errorCode instanceof WorkspaceErrorCode code) {
            return switch (code) {
                case NOT_FOUND, MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case ACCESS_DENIED, ROLE_OPERATION_DENIED -> HttpStatus.FORBIDDEN;
                case MEMBER_ALREADY_EXISTS, LAST_OWNER_REQUIRED -> HttpStatus.CONFLICT;
                case INVALID_WORKSPACE -> HttpStatus.BAD_REQUEST;
            };
        }

        if (errorCode instanceof ScheduleErrorCode code) {
            return switch (code) {
                case INVALID_TIME_RULE, NOT_SCHEDULABLE, NO_FUTURE_FIRE -> HttpStatus.BAD_REQUEST;
                case TARGET_UNPUBLISHED -> HttpStatus.CONFLICT;
                case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
                case REVISION_CONFLICT, MUST_BE_DISABLED, TARGET_ALREADY_SCHEDULED -> HttpStatus.CONFLICT;
            };
        }

        if (errorCode instanceof SecurityErrorCode code) {
            return switch (code) {
                case USER_EMAIL_FORMAT_ERROR, USER_ID_CANNOT_BE_NULL, USER_PHONE_FORMAT_ERROR, USER_NAME_FORMAT_ERROR ->
                    HttpStatus.BAD_REQUEST;
                case USER_NOT_LOGIN,
                        USER_ACCOUNT_EXPIRED,
                        USER_CREDENTIALS_ERROR,
                        USER_CREDENTIALS_EXPIRED,
                        USER_ACCOUNT_LOCKED,
                        USER_ACCOUNT_USE_BY_OTHERS -> HttpStatus.UNAUTHORIZED;
                case USER_ACCOUNT_DISABLE, NO_PERMISSION -> HttpStatus.FORBIDDEN;
                case USER_ACCOUNT_NOT_EXIST, USER_NOT_EXISTS -> HttpStatus.NOT_FOUND;
                case USER_ACCOUNT_ALREADY_EXIST, USER_PHONE_EXIST, USER_EMAIL_EXIST, USER_NAME_EXISTS ->
                    HttpStatus.CONFLICT;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
