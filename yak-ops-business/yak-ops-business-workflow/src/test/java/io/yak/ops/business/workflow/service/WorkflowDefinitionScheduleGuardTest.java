package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionScheduleGuardTest {

  @Mock private ObjectProvider<WorkflowScheduleDao> scheduleDaoProvider;
  @Mock private ObjectProvider<WorkflowScheduleLifecycle> scheduleLifecycleProvider;
  @Mock private WorkflowScheduleDao scheduleDao;
  @Mock private WorkflowScheduleLifecycle scheduleLifecycle;

  private WorkflowDefinitionScheduleGuard guard;

  @BeforeEach
  void setUp() {
    guard = new WorkflowDefinitionScheduleGuard(scheduleDaoProvider, scheduleLifecycleProvider);
  }

  @Test
  void shouldKeepLightweightBehaviorWhenScheduleComponentsAreUnavailable() {
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(null);

    assertThatCode(() -> guard.activateConfiguredSchedules("workflow-1"))
        .doesNotThrowAnyException();
    assertThatCode(() -> guard.deactivateConfiguredSchedules("workflow-1"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldActivateSavedSchedulesWhenWorkflowGoesOnline() {
    WorkflowSchedulePO schedule = schedule("schedule-1", "OFFLINE");
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(scheduleDao);
    when(scheduleLifecycleProvider.getIfAvailable()).thenReturn(scheduleLifecycle);
    when(scheduleDao.selectSchedules("workflow-1", "OFFLINE")).thenReturn(List.of(schedule));

    guard.activateConfiguredSchedules("workflow-1");

    verify(scheduleLifecycle).online("schedule-1");
  }

  @Test
  void shouldSkipExpiredScheduleWhenWorkflowGoesOnline() {
    WorkflowSchedulePO schedule = schedule("schedule-expired", "OFFLINE");
    schedule.setEndTime(Instant.now().minus(1, ChronoUnit.DAYS));
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(scheduleDao);
    when(scheduleLifecycleProvider.getIfAvailable()).thenReturn(scheduleLifecycle);
    when(scheduleDao.selectSchedules("workflow-1", "OFFLINE")).thenReturn(List.of(schedule));

    guard.activateConfiguredSchedules("workflow-1");

    verifyNoInteractions(scheduleLifecycle);
  }

  @Test
  void shouldDeactivateOnlineSchedulesBeforeWorkflowGoesOffline() {
    WorkflowSchedulePO schedule = schedule("schedule-1", "ONLINE");
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(scheduleDao);
    when(scheduleLifecycleProvider.getIfAvailable()).thenReturn(scheduleLifecycle);
    when(scheduleDao.selectSchedules("workflow-1", "ONLINE")).thenReturn(List.of(schedule));

    guard.deactivateConfiguredSchedules("workflow-1");

    verify(scheduleLifecycle).offline("schedule-1");
  }

  private WorkflowSchedulePO schedule(String id, String status) {
    WorkflowSchedulePO schedule = new WorkflowSchedulePO();
    schedule.setId(id);
    schedule.setWorkflowId("workflow-1");
    schedule.setStatus(status);
    return schedule;
  }
}
