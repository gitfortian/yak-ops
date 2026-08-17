package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
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
  @Mock private WorkflowScheduleDao scheduleDao;

  private WorkflowDefinitionScheduleGuard guard;

  @BeforeEach
  void setUp() {
    guard = new WorkflowDefinitionScheduleGuard(scheduleDaoProvider);
  }

  @Test
  void shouldAllowOfflineWhenSchedulePersistenceIsUnavailable() {
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(null);

    assertThatCode(() -> guard.ensureCanOffline("workflow-1")).doesNotThrowAnyException();
  }

  @Test
  void shouldAllowOfflineWhenNoOnlineScheduleExists() {
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(scheduleDao);
    when(scheduleDao.selectSchedules("workflow-1", "ONLINE")).thenReturn(List.of());

    assertThatCode(() -> guard.ensureCanOffline("workflow-1")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectOfflineWhenOnlineScheduleExists() {
    WorkflowSchedulePO schedule = new WorkflowSchedulePO();
    schedule.setId("schedule-1");
    schedule.setWorkflowId("workflow-1");
    schedule.setStatus("ONLINE");

    when(scheduleDaoProvider.getIfAvailable()).thenReturn(scheduleDao);
    when(scheduleDao.selectSchedules("workflow-1", "ONLINE")).thenReturn(List.of(schedule));

    assertThatThrownBy(() -> guard.ensureCanOffline("workflow-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请先停用调度");
  }
}
