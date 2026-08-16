package io.yak.ops.business.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;

/** Resolves legacy tasks and catalog-bound immutable revisions into one workflow snapshot contract. */
final class WorkflowTaskBindingResolver {

  private final TaskRegistry taskRegistry;
  private final TaskCatalogService taskCatalogService;
  private final ObjectMapper objectMapper;

  WorkflowTaskBindingResolver(
      TaskRegistry taskRegistry,
      TaskCatalogService taskCatalogService,
      ObjectMapper objectMapper) {
    this.taskRegistry = taskRegistry;
    this.taskCatalogService = taskCatalogService;
    this.objectMapper = objectMapper;
  }

  TaskVersionSnapshot snapshot(WorkflowNodeSpec node) {
    if (!node.catalogBound()) {
      TaskVersionSnapshot snapshot = taskRegistry.snapshot(node.taskId());
      if (!"SYNC".equalsIgnoreCase(snapshot.type())) {
        throw new IllegalStateException("Legacy 工作流任务当前仅支持 SYNC：" + node.taskId());
      }
      return snapshot;
    }

    TaskAsset asset = requireCatalog().get(node.taskAssetId());
    WorkflowTaskEligibilityPolicy.requireEligible(asset);
    TaskAssetRevision resolved = requireCatalog().resolveRevision(
        node.taskAssetId(),
        node.taskRevisionId());
    if (resolved.revision().revisionNo() != node.taskRevisionNo()) {
      throw new IllegalStateException(
          "工作流固定任务版本号不匹配：node=" + node.id()
              + "，expected=v" + node.taskRevisionNo()
              + "，actual=v" + resolved.revision().revisionNo());
    }

    TaskDefinition definition = resolved.revision().definition();
    return new TaskVersionSnapshot(
        WorkflowNodeSpec.catalogTaskId(resolved.asset().id()),
        resolved.asset().name(),
        definition.taskType(),
        resolved.revision().revisionNo(),
        resolved.revision().checksum(),
        definitionJson(definition),
        definition.configJson());
  }

  BindingView describe(WorkflowNodeSpec node) {
    if (!node.catalogBound()) return null;
    if (taskCatalogService == null) {
      return BindingView.unresolved(node);
    }
    try {
      TaskAsset asset = taskCatalogService.get(node.taskAssetId());
      TaskRevisionRef latest = asset.currentRevision();
      return new BindingView(
          node.taskAssetId(),
          node.taskRevisionId(),
          node.taskRevisionNo(),
          asset.name(),
          asset.taskType(),
          asset.status().name(),
          latest.taskRevisionId(),
          latest.revisionNo(),
          latest.revisionNo() > node.taskRevisionNo());
    } catch (RuntimeException ignored) {
      return BindingView.unresolved(node);
    }
  }

  WorkflowNodeSpec upgradeToLatest(WorkflowNodeSpec node) {
    if (!node.catalogBound()) {
      throw new IllegalStateException("当前节点不是 TaskAsset 节点，不能升级任务版本");
    }
    TaskAsset asset = requireCatalog().get(node.taskAssetId());
    WorkflowTaskEligibilityPolicy.requireEligible(asset);
    if (asset.status() != TaskAssetStatus.ONLINE) {
      throw new IllegalStateException("任务资产已下线，不能升级到新版本：" + asset.name());
    }
    TaskRevisionRef latest = asset.currentRevision();
    if (latest.revisionNo() <= node.taskRevisionNo()) return node;
    // Resolve before mutating the draft so a stale/corrupt catalog pointer cannot be persisted.
    requireCatalog().resolveRevision(asset.id(), latest.taskRevisionId());
    return node.withTaskRevision(latest.taskRevisionId(), latest.revisionNo());
  }

  private TaskCatalogService requireCatalog() {
    if (taskCatalogService == null) {
      throw new IllegalStateException("Task Catalog 未启用，无法解析工作流 TaskAsset 绑定");
    }
    return taskCatalogService;
  }

  private String definitionJson(TaskDefinition definition) {
    try {
      return objectMapper.writeValueAsString(definition);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("任务版本快照序列化失败：" + definition.taskType(), exception);
    }
  }

  record BindingView(
      Long taskAssetId,
      Long taskRevisionId,
      Integer taskRevisionNo,
      String taskAssetName,
      String taskType,
      String taskAssetStatus,
      Long latestTaskRevisionId,
      Integer latestTaskRevisionNo,
      boolean updateAvailable) {

    static BindingView unresolved(WorkflowNodeSpec node) {
      return new BindingView(
          node.taskAssetId(),
          node.taskRevisionId(),
          node.taskRevisionNo(),
          null,
          null,
          "UNKNOWN",
          null,
          null,
          false);
    }
  }
}
