package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.persistence.WorkflowDefinitionPersistence;
import io.yak.ops.business.workflow.persistence.WorkflowDefinitionPersistence.VersionRecord;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import org.springframework.stereotype.Service;

/**
 * 直接执行指定不可变发布版本。
 *
 * <p>正常 Cron 始终 FOLLOW_ACTIVE；Backfill 在创建批次时固定 workflowVersionId，
 * 因此后续发布新版本不会改变已经创建的补数批次语义。</p>
 */
@Service
public class WorkflowPublishedVersionRunner {
  private final WorkflowRuntimeService runtimeService;
  private final WorkflowDefinitionPersistence persistence;

  public WorkflowPublishedVersionRunner(
      WorkflowRuntimeService runtimeService,
      WorkflowDefinitionPersistence persistence) {
    this.runtimeService = runtimeService;
    this.persistence = persistence;
  }

  public WorkflowInstanceVO run(String workflowId, String workflowVersionId) {
    String id = required(workflowId, "工作流 ID 不能为空");
    String versionId = required(workflowVersionId, "工作流版本 ID 不能为空");
    VersionRecord version = persistence.loadVersions(id).stream()
        .filter(candidate -> versionId.equals(candidate.id()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "Backfill 固定的工作流发布版本不存在：" + versionId));

    WorkflowInstanceVO prepared = runtimeService.run(
        version.runSpec(),
        version.taskVersionsByNode(),
        version.id(),
        version.versionNo(),
        false);
    return runtimeService.activate(prepared.id());
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
