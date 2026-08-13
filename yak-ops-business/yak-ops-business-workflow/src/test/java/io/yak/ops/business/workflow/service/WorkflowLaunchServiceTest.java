package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.domain.WorkflowTriggerType;
import io.yak.ops.business.workflow.persistence.WorkflowExecutionTriggerRecorder;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO.NodeDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowLaunchServiceTest {

  @Mock private WorkflowDefinitionService definitionService;
  @Mock private WorkflowRuntimeService runtimeService;
  @Mock private WorkflowExecutionTriggerRecorder triggerRecorder;

  private WorkflowLaunchService service;

  @BeforeEach
  void setUp() {
    service = new WorkflowLaunchService(definitionService, runtimeService, triggerRecorder);
  }

  @Test
  void shouldRunPublishedDefinitionAndRecordTriggerContext() {
    WorkflowTriggerContext trigger = new WorkflowTriggerContext(
        WorkflowTriggerType.SCHEDULE,
        "schedule-trigger-20260814T020000",
        "schedule-1",
        Instant.parse("2026-08-14T02:00:00Z"));
    WorkflowDefinitionVO expected = definition("execution-1");
    when(definitionService.run("workflow-1")).thenReturn(expected);

    WorkflowDefinitionVO actual = service.runPublished("workflow-1", trigger);

    assertThat(actual).isSameAs(expected);
    verify(definitionService).run("workflow-1");
    verify(triggerRecorder).record("execution-1", trigger);
  }

  @Test
  void shouldRouteAdHocApiRunAndRecordTriggerContext() {
    WorkflowRunDTO request = new WorkflowRunDTO(
        "临时工作流",
        List.of(new NodeDTO("node-1", "task-1")),
        List.of(),
        Map.of());
    WorkflowTriggerContext trigger = WorkflowTriggerContext.api();
    WorkflowInstanceVO expected = instance("execution-api");
    when(runtimeService.run(request)).thenReturn(expected);

    WorkflowInstanceVO actual = service.runAdHoc(request, trigger);

    assertThat(actual).isSameAs(expected);
    verify(runtimeService).run(request);
    verify(triggerRecorder).record("execution-api", trigger);
  }

  private WorkflowDefinitionVO definition(String executionId) {
    Instant now = Instant.parse("2026-08-13T12:00:00Z");
    return new WorkflowDefinitionVO(
        "workflow-1",
        "订单同步",
        "",
        "ONLINE",
        0,
        0,
        List.of(),
        List.of(),
        Map.of(),
        Map.of(),
        0L,
        "CONTINUE_INDEPENDENT_BRANCHES",
        "workflow-version-1",
        1,
        1,
        false,
        executionId,
        "RUNNING",
        now,
        now);
  }

  private WorkflowInstanceVO instance(String executionId) {
    Instant now = Instant.parse("2026-08-13T12:00:00Z");
    return new WorkflowInstanceVO(
        executionId,
        "runtime-definition",
        null,
        "临时工作流",
        "CREATED",
        "CONTINUE_INDEPENDENT_BRANCHES",
        now,
        null,
        null,
        0L,
        Map.of(),
        0,
        0,
        List.of());
  }
}
