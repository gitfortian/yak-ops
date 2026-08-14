package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.domain.WorkflowRunInputScope;
import io.yak.ops.business.workflow.domain.WorkflowScheduleLaunchBindingScope;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.persistence.WorkflowExecutionTriggerRecorder;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 工作流统一启动入口：Trigger -> Launch -> Definition/Runtime。 */
@Service
public class WorkflowLaunchService {
  private static final Logger log = LoggerFactory.getLogger(WorkflowLaunchService.class);

  private final WorkflowDefinitionService definitionService;
  private final WorkflowRuntimeService runtimeService;
  private final WorkflowExecutionTriggerRecorder triggerRecorder;
  private final WorkflowPublishedVersionRunner publishedVersionRunner;

  @Autowired
  public WorkflowLaunchService(
      WorkflowDefinitionService definitionService,
      WorkflowRuntimeService runtimeService,
      WorkflowExecutionTriggerRecorder triggerRecorder,
      WorkflowPublishedVersionRunner publishedVersionRunner) {
    this.definitionService = definitionService;
    this.runtimeService = runtimeService;
    this.triggerRecorder = triggerRecorder;
    this.publishedVersionRunner = publishedVersionRunner;
  }

  /** Focused tests retain the Stage 4 constructor. */
  WorkflowLaunchService(
      WorkflowDefinitionService definitionService,
      WorkflowRuntimeService runtimeService,
      WorkflowExecutionTriggerRecorder triggerRecorder) {
    this(definitionService, runtimeService, triggerRecorder, null);
  }

  /** 正式执行当前启用的已发布版本；手工/API 启动仍保持单实例安全默认。 */
  public WorkflowDefinitionVO runPublished(
      String workflowId,
      WorkflowTriggerContext triggerContext) {
    String id = required(workflowId, "工作流 ID 不能为空");
    return launch(
        "PUBLISHED",
        id,
        triggerContext,
        () -> definitionService.run(id),
        WorkflowDefinitionVO::latestExecutionId);
  }

  public WorkflowDefinitionVO runScheduledPublished(
      String workflowId,
      WorkflowTriggerContext triggerContext) {
    return runScheduledPublished(workflowId, triggerContext, Map.of());
  }

  /**
   * 正常调度始终 FOLLOW_ACTIVE；运行参数只覆盖本次 engine.start，不修改发布版本。
   */
  public WorkflowDefinitionVO runScheduledPublished(
      String workflowId,
      WorkflowTriggerContext triggerContext,
      Map<String, Object> runtimeInput) {
    String id = required(workflowId, "工作流 ID 不能为空");
    try (var binding = WorkflowScheduleLaunchBindingScope.open(triggerContext.triggerId());
         var input = WorkflowRunInputScope.open(runtimeInput)) {
      return launch(
          "SCHEDULED_PUBLISHED",
          id,
          triggerContext,
          () -> definitionService.runConcurrent(id),
          WorkflowDefinitionVO::latestExecutionId);
    }
  }

  /**
   * Backfill 执行创建批次时固定的不可变发布版本，而不是跟随之后变更的 activeVersion。
   * 工作流下线仍阻止新的补数实例启动，但重新发布 V6 不会把 V5 补数批次切换到 V6。
   */
  public WorkflowInstanceVO runBackfillPublished(
      String workflowId,
      String workflowVersionId,
      WorkflowTriggerContext triggerContext,
      Map<String, Object> runtimeInput) {
    String id = required(workflowId, "工作流 ID 不能为空");
    String versionId = required(workflowVersionId, "Backfill workflowVersionId 不能为空");
    WorkflowDefinitionVO current = definitionService.get(id);
    if (!"ONLINE".equals(current.status())) {
      throw new IllegalStateException("工作流已下线，不能启动新的 Backfill 实例");
    }
    if (publishedVersionRunner == null) {
      throw new IllegalStateException("WorkflowPublishedVersionRunner 不可用");
    }
    try (var binding = WorkflowScheduleLaunchBindingScope.open(triggerContext.triggerId());
         var input = WorkflowRunInputScope.open(runtimeInput)) {
      return launch(
          "BACKFILL_PUBLISHED",
          id + "@" + versionId,
          triggerContext,
          () -> publishedVersionRunner.run(id, versionId),
          WorkflowInstanceVO::id);
    }
  }

  /** 测试执行当前草稿，不改变已发布版本。 */
  public WorkflowDefinitionVO testRunDraft(
      String workflowId,
      WorkflowTriggerContext triggerContext) {
    String id = required(workflowId, "工作流 ID 不能为空");
    return launch(
        "DRAFT_TEST",
        id,
        triggerContext,
        () -> definitionService.testRun(id),
        WorkflowDefinitionVO::latestExecutionId);
  }

  /** 兼容直接提交 DAG 的底层运行接口，同时纳入统一 Trigger 入口。 */
  public WorkflowInstanceVO runAdHoc(
      WorkflowRunDTO request,
      WorkflowTriggerContext triggerContext) {
    Objects.requireNonNull(request, "workflow run request");
    return launch(
        "AD_HOC",
        request.name(),
        triggerContext,
        () -> runtimeService.run(request),
        WorkflowInstanceVO::id);
  }

  /** 整实例重新运行会创建新的 WorkflowExecution，因此也经过 Launch。 */
  public WorkflowInstanceVO restart(
      String executionId,
      WorkflowTriggerContext triggerContext) {
    String id = required(executionId, "工作流实例 ID 不能为空");
    return launch(
        "RESTART",
        id,
        triggerContext,
        () -> runtimeService.restart(id),
        WorkflowInstanceVO::id);
  }

  /** 从指定节点重跑会创建新的 WorkflowExecution，因此也经过 Launch。 */
  public WorkflowInstanceVO rerunFromNode(
      String executionId,
      String nodeId,
      WorkflowTriggerContext triggerContext) {
    String safeExecutionId = required(executionId, "工作流实例 ID 不能为空");
    String safeNodeId = required(nodeId, "工作流节点 ID 不能为空");
    return launch(
        "RERUN_FROM_NODE",
        safeExecutionId + "/" + safeNodeId,
        triggerContext,
        () -> runtimeService.rerunFromNode(safeExecutionId, safeNodeId),
        WorkflowInstanceVO::id);
  }

  private <T> T launch(
      String mode,
      String target,
      WorkflowTriggerContext triggerContext,
      Supplier<T> action,
      Function<T, String> executionIdExtractor) {
    Objects.requireNonNull(triggerContext, "workflow trigger context");
    log.info(
        "[workflow-launch] start mode={}, target={}, triggerType={}, triggerId={}, scheduleId={}, backfillId={}, plannedFireTime={}",
        mode,
        target,
        triggerContext.triggerType(),
        triggerContext.triggerId(),
        triggerContext.scheduleId(),
        triggerContext.backfillId(),
        triggerContext.plannedFireTime());
    try {
      T result = action.get();
      String executionId = result == null ? null : executionIdExtractor.apply(result);
      triggerRecorder.record(executionId, triggerContext);
      log.info(
          "[workflow-launch] created mode={}, target={}, triggerType={}, triggerId={}, execution={}",
          mode,
          target,
          triggerContext.triggerType(),
          triggerContext.triggerId(),
          executionId);
      return result;
    } catch (RuntimeException exception) {
      log.warn(
          "[workflow-launch] failed mode={}, target={}, triggerType={}, triggerId={}, message={}",
          mode,
          target,
          triggerContext.triggerType(),
          triggerContext.triggerId(),
          exception.getMessage());
      throw exception;
    }
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
