package io.yak.ops.business.development.workflow;

import io.yak.ops.business.development.domain.SqlDevelopmentModel.Definition;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Version;
import io.yak.ops.business.development.domain.SqlTaskSnapshot;
import io.yak.ops.business.development.repository.SqlDevelopmentRepository;
import io.yak.ops.business.development.support.SqlDevelopmentJsonCodec;
import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskProvider;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

/** Projects immutable published SQL versions into the generic workflow task library. */
@Component
public class SqlTaskProvider implements TaskProvider {

  private final SqlDevelopmentRepository repository;
  private final SqlDevelopmentJsonCodec jsonCodec;

  public SqlTaskProvider(
      SqlDevelopmentRepository repository,
      SqlDevelopmentJsonCodec jsonCodec) {
    this.repository = repository;
    this.jsonCodec = jsonCodec;
  }

  @Override
  public List<TaskDefinition> list() {
    return repository.listDefinitions().stream()
        .filter(definition -> definition.publishedVersionId() != null)
        .map(definition -> new TaskDefinition(
            workflowTaskId(definition.id()), definition.name(), "SQL"))
        .toList();
  }

  @Override
  public TaskVersionSnapshot snapshot(String taskId) {
    Long id = parseTaskId(taskId);
    Definition definition = repository.findDefinition(id)
        .orElseThrow(() -> new IllegalArgumentException("SQL 任务不存在：" + taskId));
    Version version = repository.findPublishedVersion(id)
        .orElseThrow(() -> new IllegalArgumentException("SQL 任务尚未发布：" + taskId));
    return new TaskVersionSnapshot(
        workflowTaskId(id),
        definition.name(),
        "SQL",
        version.versionNo(),
        version.contentDigest(),
        jsonCodec.write(new SqlTaskSnapshot.Definition(version.sql(), version.parameters())),
        jsonCodec.write(new SqlTaskSnapshot.ExecutionConfig(version.dataSourceId(), version.id())));
  }

  private String workflowTaskId(Long id) {
    return "SQL:" + id;
  }

  private Long parseTaskId(String taskId) {
    if (taskId == null || !taskId.startsWith("SQL:")) {
      throw new IllegalArgumentException("SQL 工作流任务 ID 不合法：" + taskId);
    }
    try {
      long id = Long.parseLong(taskId.substring("SQL:".length()));
      if (id <= 0L) throw new NumberFormatException(taskId);
      return id;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("SQL 工作流任务 ID 不合法：" + taskId, exception);
    }
  }
}
