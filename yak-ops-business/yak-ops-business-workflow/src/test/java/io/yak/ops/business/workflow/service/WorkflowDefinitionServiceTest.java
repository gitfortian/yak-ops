package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.model.WorkflowDefinitionCreateRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.EdgeRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.NodeRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionVO;
import io.yak.ops.business.workflow.model.WorkflowInstanceVO;
import io.yak.ops.business.workflow.model.WorkflowVersionVO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionServiceTest {

  @Mock private WorkflowRuntimeService runtimeService;
  @Mock private TaskRegistry taskRegistry;
  private WorkflowDefinitionService service;

  @BeforeEach
  void setUp() {
    service = new WorkflowDefinitionService(runtimeService, taskRegistry);
  }

  @Test
  void shouldPublishVersionAndKeepDraftEditableWhileOnline() {
    WorkflowDefinitionVO created = createConfigured("订单同步工作流");
    when(taskRegistry.snapshot("sync-1")).thenReturn(snapshot("sync-1", "同步订单", 3));
    when(taskRegistry.snapshot("sync-2")).thenReturn(snapshot("sync-2", "同步明细", 5));

    WorkflowDefinitionVO published = service.online(created.id());
    assertThat(published.status()).isEqualTo("ONLINE");
    assertThat(published.activeVersionNo()).isEqualTo(1);
    assertThat(published.latestVersionNo()).isEqualTo(1);
    assertThat(published.draftChanged()).isFalse();

    WorkflowDefinitionVO edited = service.update(
        created.id(),
        new WorkflowDefinitionUpdateRequest(
            "订单同步工作流-草稿修改",
            "新的草稿",
            created.nodes(),
            created.edges(),
            created.input(),
            created.workflowTimeoutSeconds(),
            created.failureStrategy()));

    assertThat(edited.status()).isEqualTo("ONLINE");
    assertThat(edited.activeVersionNo()).isEqualTo(1);
    assertThat(edited.draftChanged()).isTrue();
  }

  @Test
  void shouldTestCurrentDraftBeforePublish() {
    WorkflowDefinitionVO created = createConfigured("草稿测试");
    when(taskRegistry.snapshot("sync-1")).thenReturn(snapshot("sync-1", "同步订单", 7));
    when(taskRegistry.snapshot("sync-2")).thenReturn(snapshot("sync-2", "同步明细", 8));
    WorkflowInstanceVO prepared = instance("test-exec", "CREATED");
    WorkflowInstanceVO running = instance("test-exec", "RUNNING");
    when(runtimeService.run(any(), any(), any(), any(), anyBoolean())).thenReturn(prepared);
    when(runtimeService.activate("test-exec")).thenReturn(running);
    when(runtimeService.getInstance("test-exec")).thenReturn(running);

    WorkflowDefinitionVO tested = service.testRun(created.id());

    assertThat(tested.status()).isEqualTo("DRAFT");
    assertThat(tested.activeVersionNo()).isNull();
    assertThat(tested.latestExecutionId()).isEqualTo("test-exec");
    verify(runtimeService).run(
        argThat(request -> request.name().equals("草稿测试")),
        argThat(tasks -> tasks.get("node-a").version() == 7L),
        eq(null),
        eq(null),
        eq(true));
  }

  @Test
  void shouldRunPublishedTaskSnapshotEvenWhenRegistryMovesForward() {
    WorkflowDefinitionVO created = createConfigured("可复现工作流");
    when(taskRegistry.snapshot("sync-1")).thenReturn(snapshot("sync-1", "同步订单", 11));
    when(taskRegistry.snapshot("sync-2")).thenReturn(snapshot("sync-2", "同步明细", 12));
    WorkflowDefinitionVO published = service.online(created.id());

    // 发布以后当前任务已经升级；正式运行不应再次向 TaskRegistry 取新版本。
    when(taskRegistry.snapshot("sync-1")).thenReturn(snapshot("sync-1", "同步订单", 99));
    when(taskRegistry.snapshot("sync-2")).thenReturn(snapshot("sync-2", "同步明细", 99));
    WorkflowInstanceVO prepared = instance("exec-v1", "CREATED");
    WorkflowInstanceVO completed = instance("exec-v1", "SUCCESS");
    when(runtimeService.run(any(), any(), any(), any(), anyBoolean())).thenReturn(prepared);
    when(runtimeService.activate("exec-v1")).thenReturn(completed);
    when(runtimeService.getInstance("exec-v1")).thenReturn(completed);

    service.run(created.id());

    verify(runtimeService).run(
        argThat(request -> request.name().equals("可复现工作流")),
        argThat(tasks -> tasks.get("node-a").version() == 11L
            && tasks.get("node-b").version() == 12L),
        eq(published.activeVersionId()),
        eq(1),
        eq(false));
  }

  @Test
  void shouldCreateNewVersionOnlyWhenDraftChanged() {
    WorkflowDefinitionVO created = createConfigured("版本工作流");
    when(taskRegistry.snapshot("sync-1")).thenReturn(snapshot("sync-1", "同步订单", 1));
    when(taskRegistry.snapshot("sync-2")).thenReturn(snapshot("sync-2", "同步明细", 1));
    WorkflowDefinitionVO v1 = service.online(created.id());

    // 编辑器发布/测试前会先保存；完全相同的 payload 不应制造新的 draft revision。
    WorkflowDefinitionVO equivalentSave = service.update(
        created.id(),
        new WorkflowDefinitionUpdateRequest(
            created.name(),
            created.description(),
            created.nodes(),
            created.edges(),
            created.input(),
            created.workflowTimeoutSeconds(),
            created.failureStrategy()));
    assertThat(equivalentSave.draftChanged()).isFalse();
    WorkflowDefinitionVO sameVersion = service.online(created.id());
    assertThat(sameVersion.activeVersionNo()).isEqualTo(1);
    assertThat(sameVersion.latestVersionNo()).isEqualTo(1);

    service.offline(created.id());
    WorkflowDefinitionVO enabledAgain = service.online(created.id());
    assertThat(enabledAgain.activeVersionNo()).isEqualTo(1);
    assertThat(enabledAgain.latestVersionNo()).isEqualTo(1);

    service.update(
        created.id(),
        new WorkflowDefinitionUpdateRequest(
            "版本工作流 v2 草稿",
            null,
            created.nodes(),
            created.edges(),
            created.input(),
            30L,
            created.failureStrategy()));
    when(taskRegistry.snapshot("sync-1")).thenReturn(snapshot("sync-1", "同步订单", 2));
    when(taskRegistry.snapshot("sync-2")).thenReturn(snapshot("sync-2", "同步明细", 4));
    WorkflowDefinitionVO v2 = service.online(created.id());

    assertThat(v1.activeVersionNo()).isEqualTo(1);
    assertThat(v2.activeVersionNo()).isEqualTo(2);
    assertThat(v2.latestVersionNo()).isEqualTo(2);
    assertThat(v2.draftChanged()).isFalse();
    List<WorkflowVersionVO> versions = service.versions(created.id());
    assertThat(versions).extracting(WorkflowVersionVO::versionNo).containsExactly(2, 1);
    assertThat(versions.get(0).active()).isTrue();
    assertThat(versions.get(0).taskBindings())
        .extracting(binding -> binding.taskVersion())
        .containsExactly(2L, 4L);
  }

  @Test
  void shouldRejectCycleWhenPublishing() {
    WorkflowDefinitionVO created = service.create(new WorkflowDefinitionCreateRequest("循环工作流", null));
    service.update(
        created.id(),
        new WorkflowDefinitionUpdateRequest(
            "循环工作流",
            null,
            List.of(node("a", "sync-1", 0D, 0D), node("b", "sync-2", 0D, 0D)),
            List.of(new EdgeRequest("a", "b"), new EdgeRequest("b", "a")),
            Map.of(),
            0L,
            "CONTINUE_INDEPENDENT_BRANCHES"));

    assertThatThrownBy(() -> service.online(created.id()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("循环依赖");
  }

  private WorkflowDefinitionVO createConfigured(String name) {
    WorkflowDefinitionVO created = service.create(new WorkflowDefinitionCreateRequest(name, "测试定义"));
    return service.update(
        created.id(),
        new WorkflowDefinitionUpdateRequest(
            name,
            "测试定义",
            List.of(node("node-a", "sync-1", 120D, 80D), node("node-b", "sync-2", 380D, 80D)),
            List.of(new EdgeRequest("node-a", "node-b")),
            Map.of("bizDate", "2026-08-08"),
            600L,
            "CONTINUE_INDEPENDENT_BRANCHES"));
  }

  private TaskVersionSnapshot snapshot(String id, String name, long version) {
    return new TaskVersionSnapshot(
        id,
        name,
        "SYNC",
        version,
        "digest-" + version,
        "{\"definitionVersion\":" + version + "}",
        "{\"jobSpecVersion\":" + version + "}");
  }

  private NodeRequest node(String id, String taskId, double x, double y) {
    return new NodeRequest(id, taskId, x, y, 1, 0L, 0L, 0L, Map.of(), "ALL_SUCCESS", "FAIL_WORKFLOW");
  }

  private WorkflowInstanceVO instance(String id, String status) {
    return new WorkflowInstanceVO(
        id,
        "runtime-definition",
        null,
        "订单同步工作流",
        status,
        "CONTINUE_INDEPENDENT_BRANCHES",
        Instant.now(),
        null,
        null,
        0L,
        Map.of(),
        2,
        1,
        List.of());
  }
}
