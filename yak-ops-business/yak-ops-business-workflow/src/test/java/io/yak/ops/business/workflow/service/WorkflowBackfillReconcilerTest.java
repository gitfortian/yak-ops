package io.yak.ops.business.workflow.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.service.WorkflowBackfillPlanner.Occurrence;
import io.yak.ops.business.workflow.service.WorkflowBackfillPlanner.Plan;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowBackfillReconcilerTest {
  @Mock private WorkflowBackfillDao dao;
  @Mock private WorkflowBackfillPlanner planner;
  @Mock private WorkflowScheduleTriggerCoordinator coordinator;

  private WorkflowBackfillReconciler reconciler;

  @BeforeEach
  void setUp() {
    reconciler = new WorkflowBackfillReconciler(dao, planner, coordinator);
  }

  @Test
  void shouldReplayAllOccurrencesForRunningBatchAndRelyOnLedgerDedupe() {
    WorkflowBackfillPO backfill = new WorkflowBackfillPO();
    backfill.setId("backfill-1");
    backfill.setStatus("RUNNING");
    backfill.setCronExpression("0 0 2 * * ?");
    backfill.setTimezone("Asia/Shanghai");
    backfill.setStartBusinessDate(LocalDate.of(2026, 8, 1));
    backfill.setEndBusinessDate(LocalDate.of(2026, 8, 2));
    Occurrence first = new Occurrence(
        LocalDate.of(2026, 8, 1),
        Instant.parse("2026-07-31T18:00:00Z"),
        "2026-08-01T02:00:00+08:00");
    Occurrence second = new Occurrence(
        LocalDate.of(2026, 8, 2),
        Instant.parse("2026-08-01T18:00:00Z"),
        "2026-08-02T02:00:00+08:00");
    when(dao.selectList(null, null)).thenReturn(List.of(backfill));
    when(planner.plan(
        backfill.getCronExpression(),
        backfill.getTimezone(),
        backfill.getStartBusinessDate(),
        backfill.getEndBusinessDate()))
        .thenReturn(new Plan("Asia/Shanghai", "0 0 2 * * ?", List.of(first, second)));

    reconciler.reconcile();

    verify(coordinator).submitBackfill(backfill, first);
    verify(coordinator).submitBackfill(backfill, second);
  }
}
