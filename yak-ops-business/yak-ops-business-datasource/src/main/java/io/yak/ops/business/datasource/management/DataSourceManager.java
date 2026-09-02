package io.yak.ops.business.datasource.management;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.connection.DataSourceConnectionResolver;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceChangedEvent;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Owns datasource aggregate lifecycle commands. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceManager {

  private static final Pattern CREDENTIAL_FIELD =
      Pattern.compile("(?i)\\\"(?:password|passwd|pwd|token|secret|credential|access[_-]?key|secret[_-]?key)\\\"\\s*:");

  private final DataSourceRepository repository;
  private final DataSourceReader reader;
  private final DataSourceConnectionResolver connectionResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final BusinessAuditService auditService;

  @Transactional(
      transactionManager = "opsDataSourceTransactionManager",
      rollbackFor = Exception.class)
  public boolean create(DataSourceConfigurationCommand command) {
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "DATASOURCE_CREATE",
                "Create datasource",
                "DATASOURCE",
                null,
                command.name(),
                "APPLICATION",
                Map.of("dbType", command.dbType().name(), "environment", command.environment().name())));
    try {
      ensureNameAvailable(command.name(), null);
      ConnectionProfile connectionProfile =
          connectionResolver.normalize(command.dbType(), command.connectionJson());
      DataSourceDefinition definition =
          DataSourceDefinition.create(
              command.name(),
              command.dbType(),
              connectionProfile,
              command.environment(),
              command.remark());
      if (!repository.insert(definition)) {
        throw new DataSourceException(DataSourceErrorCode.CREATE_FAILED);
      }
      completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Datasource created",
          Map.of("dbType", command.dbType().name(), "environment", command.environment().name()),
          "Datasource created");
      return true;
    } catch (RuntimeException exception) {
      audit.failure("DATASOURCE_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(
      transactionManager = "opsDataSourceTransactionManager",
      rollbackFor = Exception.class)
  public boolean update(Long id, DataSourceConfigurationCommand command) {
    DataSourceDefinition existing = reader.require(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "DATASOURCE_UPDATE",
                "Update datasource",
                "DATASOURCE",
                String.valueOf(id),
                existing.getName(),
                "APPLICATION",
                Map.of()));
    try {
      ensureNameAvailable(command.name(), id);
      assertTypeUnchanged(existing, command);
      ConnectionProfile connectionProfile =
          connectionResolver.mergeStoredSecrets(existing, command.connectionJson());
      Map<String, Object> changes = safeChanges(existing, command, connectionProfile);
      existing.updateConfiguration(
          command.name(),
          command.dbType(),
          connectionProfile,
          command.environment(),
          command.remark());
      if (!repository.update(existing)) {
        throw new DataSourceException(DataSourceErrorCode.UPDATE_FAILED);
      }
      eventPublisher.publishEvent(new DataSourceChangedEvent(id));
      audit.resource(String.valueOf(id), command.name());
      completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Datasource configuration updated",
          changes,
          "Datasource updated");
      return true;
    } catch (RuntimeException exception) {
      audit.failure("DATASOURCE_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(
      transactionManager = "opsDataSourceTransactionManager",
      rollbackFor = Exception.class)
  public boolean delete(Long id) {
    DataSourceDefinition existing = reader.require(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "DATASOURCE_DELETE",
                "Delete datasource",
                "DATASOURCE",
                String.valueOf(id),
                existing.getName(),
                "APPLICATION",
                Map.of()));
    try {
      if (!repository.delete(existing.getId())) {
        throw new DataSourceException(DataSourceErrorCode.DELETE_FAILED);
      }
      eventPublisher.publishEvent(new DataSourceChangedEvent(existing.getId()));
      completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Datasource deleted",
          Map.of(
              "dbType", existing.getDbType().name(),
              "environment", existing.getEnvironment().name()),
          "Datasource deleted");
      return true;
    } catch (RuntimeException exception) {
      audit.failure("DATASOURCE_DELETE_FAILED", exception);
      throw exception;
    }
  }

  private void ensureNameAvailable(String name, Long excludeId) {
    if (repository.existsByName(name, excludeId)) {
      throw new DataSourceException(DataSourceErrorCode.DUPLICATE_NAME);
    }
  }

  private void assertTypeUnchanged(
      DataSourceDefinition existing,
      DataSourceConfigurationCommand command) {
    try {
      existing.assertTypeUnchanged(command.dbType());
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_DB_TYPE,
          exception.getMessage(),
          exception);
    }
  }

  private static Map<String, Object> safeChanges(
      DataSourceDefinition existing,
      DataSourceConfigurationCommand command,
      ConnectionProfile connectionProfile) {
    Map<String, Object> changes = new LinkedHashMap<>();
    addValueChange(changes, "name", existing.getName(), command.name());
    addValueChange(
        changes,
        "environment",
        existing.getEnvironment().name(),
        command.environment().name());
    if (!Objects.equals(existing.getRemark(), normalize(command.remark()))) {
      changes.put("remarkChanged", true);
    }
    if (!Objects.equals(existing.getOriginalJson(), connectionProfile.originalJson())) {
      changes.put("connectionChanged", true);
    }
    if (containsCredentialField(command.connectionJson())) {
      changes.put("credentialChanged", true);
    }
    return Map.copyOf(changes);
  }

  private static void addValueChange(
      Map<String, Object> changes, String field, Object before, Object after) {
    if (!Objects.equals(before, after)) {
      changes.put(field, Map.of("before", before, "after", after));
    }
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static boolean containsCredentialField(String json) {
    return json != null && CREDENTIAL_FIELD.matcher(json).find();
  }

  private static void completeOnCommit(
      AuditOperationHandle audit,
      AuditEventType eventType,
      String message,
      Map<String, ?> payload,
      String summary) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      audit.event(eventType, message, payload);
      audit.success(summary);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            audit.event(eventType, message, payload);
            audit.success(summary);
          }

          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              audit.failure("TRANSACTION_ROLLED_BACK", null);
            }
          }
        });
  }
}
