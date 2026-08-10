package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.SqlDevelopmentModel.Definition;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Execution;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Version;
import io.yak.ops.business.development.domain.SqlParameterDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Durable SQL development repository. Persistence implementation details stay behind this boundary. */
public interface SqlDevelopmentRepository {

  Definition insertDefinition(
      String name,
      String description,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters);

  Optional<Definition> findDefinition(Long id);

  List<Definition> listDefinitions();

  boolean updateDraft(
      Long id,
      long baseRevision,
      String name,
      String description,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters);

  Optional<Definition> lockDefinition(Long id);

  Version insertVersion(
      Long taskId,
      int versionNo,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters,
      String contentDigest);

  boolean markPublished(Long taskId, Long versionId, int versionNo);

  Optional<Version> findVersion(Long versionId);

  Optional<Version> findPublishedVersion(Long taskId);

  List<Version> listVersions(Long taskId);

  Execution insertExecution(
      Long taskId,
      Long taskVersionId,
      Integer taskVersionNo,
      Long dataSourceId,
      String sql,
      Map<String, Object> input,
      String idempotencyKey);

  Optional<Execution> findExecution(Long executionId);

  Optional<Execution> findExecutionByIdempotencyKey(String idempotencyKey);

  boolean markRunning(Long executionId);

  boolean markSucceeded(Long executionId, long affectedRows, Map<String, Object> output);

  boolean markFailed(Long executionId, String errorMessage);

  boolean markCanceled(Long executionId);

  boolean markLost(Long executionId, String errorMessage);
}
