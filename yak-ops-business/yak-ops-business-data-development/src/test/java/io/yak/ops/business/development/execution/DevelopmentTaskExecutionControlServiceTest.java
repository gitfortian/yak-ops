package io.yak.ops.business.development.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSubmission;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DevelopmentTaskExecutionControlServiceTest {

  @Test
  void refreshesTerminalRuntimeStateIntoDurableHistory() {
    DevelopmentTaskExecutionService histories = mock(DevelopmentTaskExecutionService.class);
    DevelopmentTaskRunService runs = mock(DevelopmentTaskRunService.class);
    TaskExecutionGateway gateway = mock(TaskExecutionGateway.class);
    DevelopmentTaskExecutionDetail running = execution("RUNNING", "runtime-1", null);
    DevelopmentTaskExecutionDetail completed = execution("SUCCESS", "runtime-1", null);
    when(histories.get(10L)).thenReturn(running, completed);
    when(gateway.status("SQL", "runtime-1"))
        .thenReturn(new TaskExecution("runtime-1", "SUCCEEDED", null, Map.of("rows", 1)));

    DevelopmentTaskExecutionControlService service =
        new DevelopmentTaskExecutionControlService(histories, runs, gateway);

    assertEquals("SUCCESS", service.refresh(10L).status());
    verify(histories).complete(eq(10L), eq("SUCCESS"), anyLong(), eq(null), eq(Map.of("rows", 1)));
  }

  @Test
  void retriesTheExactPersistedDefinitionAndLinksThePreviousExecution() {
    DevelopmentTaskExecutionService histories = mock(DevelopmentTaskExecutionService.class);
    DevelopmentTaskRunService runs = mock(DevelopmentTaskRunService.class);
    TaskExecutionGateway gateway = mock(TaskExecutionGateway.class);
    DevelopmentTaskExecutionDetail failed = execution("FAILED", "runtime-1", null);
    DevelopmentTaskExecutionSubmission retried = new DevelopmentTaskExecutionSubmission(
        11L, 7L, "SQL", "runtime-2", TaskExecutionStatus.RUNNING);
    when(histories.get(10L)).thenReturn(failed);
    when(runs.submit(7L, "SQL", 3, "select 1", "{\"dataSourceId\":\"9\"}", "bruce", 10L))
        .thenReturn(retried);

    DevelopmentTaskExecutionControlService service =
        new DevelopmentTaskExecutionControlService(histories, runs, gateway);

    DevelopmentTaskExecutionSubmission result = service.retry(10L, "bruce");

    assertEquals(11L, result.id());
    verify(runs).submit(7L, "SQL", 3, "select 1", "{\"dataSourceId\":\"9\"}", "bruce", 10L);
  }

  private DevelopmentTaskExecutionDetail execution(
      String status,
      String runtimeExecutionId,
      Long retryOfExecutionId) {
    return new DevelopmentTaskExecutionDetail(
        10L,
        7L,
        "测试任务",
        "SQL",
        3,
        "MANUAL",
        runtimeExecutionId,
        retryOfExecutionId,
        status,
        "bruce",
        "RUNNING".equals(status) ? null : 100L,
        null,
        "select 1",
        "{\"dataSourceId\":\"9\"}",
        Map.of(),
        LocalDateTime.now().minusSeconds(1),
        "RUNNING".equals(status) ? null : LocalDateTime.now());
  }
}
