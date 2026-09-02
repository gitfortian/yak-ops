package io.yak.ops.business.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC implementation backed by the shared business database.
 *
 * <p>Every audit write uses REQUIRES_NEW and is swallowed on failure. This deliberately keeps audit
 * persistence outside business transactions: a rolled-back business action can still be explained,
 * while an unavailable audit store cannot turn an otherwise valid action into a business failure.</p>
 */
final class JdbcBusinessAuditService implements BusinessAuditService {

  private static final Logger log = LoggerFactory.getLogger(JdbcBusinessAuditService.class);
  private static final int SCHEMA_VERSION = 1;

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final CurrentProject currentProject;
  private final ObjectMapper objectMapper;

  JdbcBusinessAuditService(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      CurrentProject currentProject,
      ObjectMapper objectMapper) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.currentProject = currentProject;
    this.objectMapper = objectMapper;
  }

  @Override
  public AuditOperationHandle start(AuditOperationRequest request) {
    String operationId = "AUD-" + UUID.randomUUID();
    ActorSnapshot actor = actorSnapshot();
    ProjectContext project = currentProject.current().orElse(null);
    String traceId = MDC.get("traceId");
    LocalDateTime now = LocalDateTime.now();
    boolean persisted = safeWrite(() -> {
      jdbcTemplate.update(
          """
          INSERT INTO yak_audit_operation (
            operation_id, operation_type, operation_name, actor_id, actor_name,
            project_id, project_name, resource_type, resource_id, resource_name,
            status, source, started_at, root_trace_id, metadata_json, schema_version,
            created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?, ?)
          """,
          operationId,
          request.operationType(),
          request.operationName(),
          actor.id(),
          actor.name(),
          project == null ? null : project.projectId(),
          project == null ? null : project.projectName(),
          request.resourceType(),
          request.resourceId(),
          request.resourceName(),
          request.source(),
          now,
          traceId,
          json(request.metadata()),
          SCHEMA_VERSION,
          now,
          now);
      insertEvent(
          operationId,
          AuditEventType.OPERATION_STARTED,
          "INFO",
          actor,
          request.resourceType(),
          request.resourceId(),
          "Operation started",
          null,
          Map.of());
    });
    return new JdbcAuditOperationHandle(
        operationId,
        request.resourceType(),
        request.resourceId(),
        request.resourceName(),
        actor,
        persisted);
  }

  private final class JdbcAuditOperationHandle implements AuditOperationHandle {
    private final String operationId;
    private final String resourceType;
    private final ActorSnapshot actor;
    private final boolean persisted;
    private volatile String resourceId;
    private volatile String resourceName;

    private JdbcAuditOperationHandle(
        String operationId,
        String resourceType,
        String resourceId,
        String resourceName,
        ActorSnapshot actor,
        boolean persisted) {
      this.operationId = operationId;
      this.resourceType = resourceType;
      this.resourceId = resourceId;
      this.resourceName = resourceName;
      this.actor = actor;
      this.persisted = persisted;
    }

    @Override
    public String operationId() {
      return operationId;
    }

    @Override
    public void resource(String resourceId, String resourceName) {
      this.resourceId = resourceId;
      this.resourceName = resourceName;
      if (!persisted) return;
      safeWrite(() -> jdbcTemplate.update(
          "UPDATE yak_audit_operation SET resource_id = ?, resource_name = ?, updated_at = ? WHERE operation_id = ?",
          resourceId,
          resourceName,
          LocalDateTime.now(),
          operationId));
    }

    @Override
    public void event(AuditEventType type, String message, Map<String, ?> payload) {
      if (!persisted) return;
      safeWrite(() -> insertEvent(
          operationId,
          type,
          eventStatus(type),
          actor,
          resourceType,
          resourceId,
          message,
          null,
          payload));
    }

    @Override
    public void success(String summary) {
      if (!persisted) return;
      safeWrite(() -> {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
            """
            UPDATE yak_audit_operation
               SET status = 'SUCCEEDED', summary = ?, finished_at = ?, updated_at = ?
             WHERE operation_id = ? AND status = 'RUNNING'
            """,
            summary,
            now,
            now,
            operationId);
        if (updated > 0) {
          insertEvent(
              operationId,
              AuditEventType.OPERATION_SUCCEEDED,
              "SUCCESS",
              actor,
              resourceType,
              resourceId,
              summary == null ? "Operation succeeded" : summary,
              null,
              Map.of());
        }
      });
    }

    @Override
    public void failure(String reasonCode, Throwable cause) {
      if (!persisted) return;
      safeWrite(() -> {
        LocalDateTime now = LocalDateTime.now();
        String summary = safeErrorMessage(cause);
        int updated = jdbcTemplate.update(
            """
            UPDATE yak_audit_operation
               SET status = 'FAILED', error_code = ?, summary = ?, finished_at = ?, updated_at = ?
             WHERE operation_id = ? AND status = 'RUNNING'
            """,
            reasonCode,
            summary,
            now,
            now,
            operationId);
        if (updated > 0) {
          insertEvent(
              operationId,
              AuditEventType.OPERATION_FAILED,
              "FAILURE",
              actor,
              resourceType,
              resourceId,
              summary == null ? "Operation failed" : summary,
              reasonCode,
              cause == null ? Map.of() : Map.of("exceptionType", cause.getClass().getName()));
        }
      });
    }
  }

  private void insertEvent(
      String operationId,
      AuditEventType type,
      String eventStatus,
      ActorSnapshot actor,
      String resourceType,
      String resourceId,
      String message,
      String reasonCode,
      Map<String, ?> payload) {
    LocalDateTime now = LocalDateTime.now();
    jdbcTemplate.update(
        """
        INSERT INTO yak_audit_event (
          operation_id, event_type, event_category, event_status, occurred_at,
          actor_id, resource_type, resource_id, trace_id, span_id,
          reason_code, message, payload_json, schema_version, created_at
        ) VALUES (?, ?, 'BUSINESS', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        operationId,
        type.name(),
        eventStatus,
        now,
        actor.id(),
        resourceType,
        resourceId,
        MDC.get("traceId"),
        MDC.get("spanId"),
        reasonCode,
        message,
        json(payload),
        SCHEMA_VERSION,
        now);
  }

  private boolean safeWrite(Runnable write) {
    try {
      transactionTemplate.executeWithoutResult(status -> write.run());
      return true;
    } catch (RuntimeException exception) {
      log.warn("Business audit write failed; the business action will continue", exception);
      return false;
    }
  }

  private String json(Map<String, ?> value) {
    if (value == null || value.isEmpty()) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      log.debug("Unable to serialize audit payload", exception);
      return null;
    }
  }

  private ActorSnapshot actorSnapshot() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return new ActorSnapshot(null, null);
    }
    String name = authentication.getName();
    if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
      return new ActorSnapshot(null, null);
    }
    // Yak Security does not expose a stable numeric user id through Spring Security's generic API.
    // Keep actor_id nullable rather than fabricating one; actor_name is still a historical snapshot.
    return new ActorSnapshot(null, name);
  }

  private static String eventStatus(AuditEventType type) {
    return switch (type) {
      case RESOURCE_CREATED, RESOURCE_UPDATED, RESOURCE_DELETED, OPERATION_SUCCEEDED -> "SUCCESS";
      case OPERATION_FAILED -> "FAILURE";
      case OPERATION_STARTED -> "INFO";
    };
  }

  private static String safeErrorMessage(Throwable cause) {
    if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) return null;
    String message = cause.getMessage().replaceAll("[\\r\\n]+", " ");
    return message.length() <= 512 ? message : message.substring(0, 512);
  }

  private record ActorSnapshot(String id, String name) {}
}
