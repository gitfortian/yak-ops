package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.model.WorkflowDefinitionCreateRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.EdgeRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.NodeRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionVO;
import io.yak.ops.business.workflow.model.WorkflowInstanceVO;
import io.yak.ops.business.workflow.model.WorkflowRunRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowStartDefinitionExecutionTest {

  @Mock private WorkflowRuntimeService runtimeService;
  @Mock private TaskRegistry taskRegistry;
  private WorkflowDefinitionService service;

  @BeforeEach
  void setUp() {
    service = new WorkflowDefinitionService(runtimeService, taskRegistry);
  }

  @Test
  void shouldCompileStartFromEditorMetaAndKeepRuntimeInputClean() {
    WorkflowDefinitionVO created = service.create(
        new WorkflowDefinitionCreateRequest("start-semantics", null));
    service.update(
        created.id(),
        new WorkflowDefinitionUpdateRequest(
            "start-semantics",
            null,
            List.of(node("a", "task-a"), node("b", "task-b"), node("c", "task-c")),
            List.of(new EdgeRequest("a", "c")),
            Map.of("inputs", Map.of("bizDate", "2026-08-09")),
            startEditorMeta(List.of("a")),
            0L,
            "CONTINUE_INDEPENDENT_BRANCHES"));

    when(taskRegistry.snapshot("task-a")).thenReturn(snapshot("task-a", "A"));
    when(taskRegistry.snapshot("task-c")).thenReturn(snapshot("task-c", "C"));
    service.online(created.id());

    WorkflowInstanceVO prepared = instance("exec-1", "CREATED", 2, 1);
    WorkflowInstanceVO running = instance("exec-1", "RUNNING", 2, 1);
    ArgumentCaptor<WorkflowRunRequest> requestCaptor = ArgumentCaptor.forClass(WorkflowRunRequest.class);
    when(runtimeService.run(requestCaptor.capture(), any(), any(), any(), anyBoolean())).thenReturn(prepared);
    when(runtimeService.activate("exec-1")).thenReturn(running);
    when(runtimeService.getInstance("exec-1")).thenReturn(running);

    service.run(created.id());

    WorkflowRunRequest runtimeRequest = requestCaptor.getValue();
    assertThat(runtimeRequest.nodes())
        .extracting(WorkflowRunRequest.NodeRequest::id)
        .containsExactly("a", "c");
    assertThat(runtimeRequest.edges())
        .extracting(edge -> edge.source() + "->" + edge.target())
        .containsExactly("a->c");
    assertThat(runtimeRequest.input())
        .containsEntry("inputs", Map.of("bizDate", "2026-08-09"))
        .doesNotContainKeys("__yak_start__", "__yak_editor__");
  }

  @Test
  void shouldKeepLegacyStartMetaInInputReadable() {
    WorkflowDefinitionVO created = service.create(
        new WorkflowDefinitionCreateRequest("legacy-start", null));
    service.update(
        created.id(),
        new WorkflowDefinitionUpdateRequest(
            "legacy-start",
            null,
            List.of(node("a", "task-a"), node("b", "task-b")),
            List.of(),
            legacyStartInput(List.of("b")),
            0L,
            "CONTINUE_INDEPENDENT_BRANCHES"));

    when(taskRegistry.snapshot("task-b")).thenReturn(snapshot("task-b", "B"));
    WorkflowDefinitionVO published = service.online(created.id());

    assertThat(published.activeVersionNo()).isEqualTo(1);
    assertThat(service.versions(created.id()).get(0).nodeCount()).isEqualTo(1);
  }

  private TaskVersionSnapshot snapshot(String id, String name) {
    return new TaskVersionSnapshot(id, name, "SYNC", 1L, "digest", "{}", "{}");
  }

  private Map<String, Object> startEditorMeta(List<String> nextNodeIds) {
    return Map.of("__yak_start__", Map.of("version", 2, "nextNodeIds", nextNodeIds));
  }

  private Map<String, Object> legacyStartInput(List<String> nextNodeIds) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("__yak_start__", Map.of("version", 2, "nextNodeIds", nextNodeIds));
    return input;
  }

  private NodeRequest node(String id, String taskId) {
    return new NodeRequest(
        id, taskId, 0D, 0D, 1, 0L, 0L, 0L,
        Map.of(), "ALL_SUCCESS", "FAIL_WORKFLOW");
  }

  private WorkflowInstanceVO instance(String id, String status, int nodeCount, int edgeCount) {
    return new WorkflowInstanceVO(
        id, "runtime-definition", null, "start-semantics", status,
        "CONTINUE_INDEPENDENT_BRANCHES", Instant.now(), null, null, 0L,
        Map.of(), nodeCount, edgeCount, List.of());
  }
}
