package io.yak.ops.business.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.project.CurrentProject;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class JdbcBusinessAuditServiceTest {

  @Test
  void unavailableAuditStoreNeverBreaksBusinessCallerAndOperationIdIsNotTraceId() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("audit database unavailable"));
    CurrentProject currentProject = Optional::empty;
    JdbcBusinessAuditService service =
        new JdbcBusinessAuditService(
            dataSource,
            new DataSourceTransactionManager(dataSource),
            currentProject,
            new ObjectMapper());

    MDC.put("traceId", "trace-123");
    try {
      AuditOperationHandle handle =
          service.start(
              new AuditOperationRequest(
                  "TEST_OPERATION",
                  "Test operation",
                  "TEST_RESOURCE",
                  "42",
                  "resource",
                  "TEST",
                  Map.of()));

      assertThat(handle.operationId()).startsWith("AUD-").isNotEqualTo("trace-123");
      assertThatCode(
              () -> {
                handle.event(AuditEventType.RESOURCE_UPDATED, "updated", Map.of());
                handle.success("done");
                handle.failure("SHOULD_BE_NOOP", null);
              })
          .doesNotThrowAnyException();
    } finally {
      MDC.remove("traceId");
    }
  }
}
