package io.yak.ops.boot.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.configuration.validation.ConfigValidationError;
import io.yak.ops.configuration.validation.OptionValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Datasource field-level errors carry safe paths without exposing configuration values. */
class DataSourceValidationResponseTest {

    @Test
    void returnsStructuredOptionErrorsWithoutRawCredentials() {
        OptionValidationException cause = new OptionValidationException(
                List.of(new ConfigValidationError("sshTunnel.password", "REQUIRED", "Option group constraint failed")));
        DataSourceException failure =
                new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, null, cause);

        var response = new GlobalExceptionHandler().handleBusinessException(failure);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(DataSourceErrorCode.INVALID_CONNECTION_PARAMS.getCode(), response.getBody().getCode());
        List<?> data = assertInstanceOf(List.class, response.getBody().getData());
        ConfigValidationError error = assertInstanceOf(ConfigValidationError.class, data.getFirst());
        assertEquals("sshTunnel.password", error.path());
        assertFalse(response.getBody().getMessage().contains("private-secret"));
    }

    @Test
    void reportsARejectedConnectionAsABadRequestWithTheDriverReason() {
        var response = new GlobalExceptionHandler().handleBusinessException(new DataSourceException(
                DataSourceErrorCode.CONNECT_FAILED,
                "Access denied for user 'root'@'localhost' (using password: YES)"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(DataSourceErrorCode.CONNECT_FAILED.getCode(), response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Access denied"));
    }

    @Test
    void ordinaryDatasourceErrorsKeepTheExistingResponseEnvelope() {
        var response = new GlobalExceptionHandler().handleBusinessException(
                new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getData() == null);
    }
}
