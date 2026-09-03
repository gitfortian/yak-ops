package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.audit.AuditQueryService;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Read-side enrichment for Workflow instances that must not mutate runtime execution truth. */
@Service
public class WorkflowInstanceQueryService {

  private static final String WORKFLOW_EXECUTE_OPERATION = "WORKFLOW_EXECUTE";
  private static final String WORKFLOW_EXECUTION_RESOURCE = "WORKFLOW_EXECUTION";

  private final WorkflowRuntime workflowRuntime;
  private final AuditQueryService auditQueryService;
  private final CurrentProject currentProject;

  @Autowired
  public WorkflowInstanceQueryService(
      WorkflowRuntime workflowRuntime,
      ObjectProvider<AuditQueryService> auditQueryServiceProvider,
      CurrentProject currentProject) {
    this(
        workflowRuntime,
        auditQueryServiceProvider == null ? null : auditQueryServiceProvider.getIfAvailable(),
        currentProject);
  }

  WorkflowInstanceQueryService(
      WorkflowRuntime workflowRuntime,
      AuditQueryService auditQueryService,
      CurrentProject currentProject) {
    this.workflowRuntime = workflowRuntime;
    this.auditQueryService = auditQueryService;
    this.currentProject = currentProject;
  }

  public List<WorkflowInstanceVO> listInstances() {
    List<WorkflowInstanceVO> instances = workflowRuntime.listInstances();
    if (instances.isEmpty() || auditQueryService == null || currentProject == null) {
      return instances;
    }

    Map<String, String> creators =
        auditQueryService.firstActorNames(
            WORKFLOW_EXECUTE_OPERATION,
            WORKFLOW_EXECUTION_RESOURCE,
            instances.stream().map(WorkflowInstanceVO::id).toList(),
            currentProject.requireProjectId());
    if (creators.isEmpty()) return instances;

    return instances.stream()
        .map(instance -> withCreator(instance, creators.get(instance.id())))
        .toList();
  }

  public WorkflowInstanceVO getInstance(String executionId) {
    WorkflowInstanceVO instance = workflowRuntime.getInstance(executionId);
    if (auditQueryService == null || currentProject == null) return instance;

    String creatorName =
        auditQueryService.firstActorNames(
                WORKFLOW_EXECUTE_OPERATION,
                WORKFLOW_EXECUTION_RESOURCE,
                List.of(instance.id()),
                currentProject.requireProjectId())
            .get(instance.id());
    return withCreator(instance, creatorName);
  }

  private WorkflowInstanceVO withCreator(WorkflowInstanceVO instance, String creatorName) {
    return creatorName == null || creatorName.isBlank()
        ? instance
        : instance.withCreatorName(creatorName.trim());
  }
}
