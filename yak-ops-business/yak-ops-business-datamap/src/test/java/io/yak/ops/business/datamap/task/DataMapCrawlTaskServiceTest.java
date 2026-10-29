package io.yak.ops.business.datamap.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.crawl.DataMapCrawlService;
import io.yak.ops.business.datamap.task.impl.DataMapCrawlTaskServiceImpl;
import io.yak.ops.business.modeling.planning.PlanningService;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlTaskSaveDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlTaskVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlVO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.enums.datamap.DataMapCrawlScheduleType;
import io.yak.ops.common.enums.datamap.DataMapCrawlTaskStatus;
import io.yak.ops.common.enums.datamap.DataMapCrawlTriggerType;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.datamap.DataMapCrawlTaskEntity;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlMapper;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlTaskMapper;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Verifies task lifecycle validation and occurrence ownership of the scheduler path. */
class DataMapCrawlTaskServiceTest {

    @Mock
    private DataMapCrawlTaskMapper taskMapper;

    @Mock
    private DataMapCrawlMapper crawlMapper;

    @Mock
    private DataMapCrawlService crawlService;

    @Mock
    private DataSourceLookupService dataSourceLookup;

    @Mock
    private PlanningService planningService;

    @InjectMocks
    private DataMapCrawlTaskServiceImpl service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("MYSQL", "demo", null, 7));
        when(planningService.owners("ws", "actor")).thenReturn(List.of(new OwnerOptionVO("owner", "Owner")));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void aCronTaskStoresItsExpressionAndNextOccurrence() {
        DataMapCrawlTaskVO task = service.create("ws", "actor", input(DataMapCrawlScheduleType.CRON, "0 0 3 * * *"));

        assertEquals("RUNNING", task.getStatus());
        assertEquals("0 0 3 * * *", task.getCronExpression());
        assertNotNull(task.getNextFireTime());
        assertTrue(task.getNextFireTime().isAfter(LocalDateTime.now()));
        verify(taskMapper).insert(any());
    }

    @Test
    void exclusionsAreStoredAsADeduplicatedSnapshot() {
        DataMapCrawlTaskSaveDTO input = input(DataMapCrawlScheduleType.MANUAL, null);
        input.setExcludeTables(List.of(" tmp_debug ", "tmp_debug", "", "audit_log"));
        input.setExcludePattern(" ^tmp_ ");

        DataMapCrawlTaskVO task = service.create("ws", "actor", input);

        assertEquals(List.of("tmp_debug", "audit_log"), task.getExcludeTables());
        assertEquals("^tmp_", task.getExcludePattern());
        assertNull(task.getCronExpression());
        assertNull(task.getNextFireTime());
    }

    @Test
    void aDraftNeverHoldsASchedule() {
        DataMapCrawlTaskSaveDTO input = input(DataMapCrawlScheduleType.CRON, "0 0 3 * * *");
        input.setStatus(DataMapCrawlTaskStatus.DRAFT);

        DataMapCrawlTaskVO task = service.create("ws", "actor", input);

        assertEquals("DRAFT", task.getStatus());
        assertNull(task.getNextFireTime());
    }

    @Test
    void anInvalidCronExpressionIsRefusedBeforeAnyRowExists() {
        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.create("ws", "actor", input(DataMapCrawlScheduleType.CRON, "not-a-cron")));

        assertEquals(DataMapErrorCode.CRAWL_TASK_CRON_INVALID, failure.getErrorCode());
        verify(taskMapper, never()).insert(any());
    }

    @Test
    void aCronTaskWithoutAnExpressionIsRefused() {
        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.create("ws", "actor", input(DataMapCrawlScheduleType.CRON, null)));

        assertEquals(DataMapErrorCode.CRAWL_TASK_CRON_INVALID, failure.getErrorCode());
    }

    @Test
    void anUncompilableExcludePatternIsRefusedBeforeAnyRowExists() {
        DataMapCrawlTaskSaveDTO input = input(DataMapCrawlScheduleType.MANUAL, null);
        input.setExcludePattern("^tmp_[a-");

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DataMapErrorCode.CRAWL_PATTERN_INVALID, failure.getErrorCode());
        verify(taskMapper, never()).insert(any());
    }

    @Test
    void anOwnerOutsideTheWorkspaceIsRefused() {
        when(planningService.owners("ws", "actor")).thenReturn(List.of(new OwnerOptionVO("someone", "Someone")));

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.create("ws", "actor", input(DataMapCrawlScheduleType.MANUAL, null)));

        assertEquals(DataMapErrorCode.INVALID_OWNER, failure.getErrorCode());
    }

    @Test
    void moreThanFiveHundredExcludedTablesAreRefused() {
        List<String> tables = new ArrayList<>();
        for (int index = 0; index < 501; index++) tables.add("table_" + index);
        DataMapCrawlTaskSaveDTO input = input(DataMapCrawlScheduleType.MANUAL, null);
        input.setExcludeTables(tables);

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DataMapErrorCode.CRAWL_EXCLUDE_TOO_MANY, failure.getErrorCode());
    }

    @Test
    void aRunningTaskCannotBeDeletedEvenBeforeItsRunIsGone() {
        when(taskMapper.findByIdForWorkspace("ws", "t1")).thenReturn(task("RUNNING"));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.delete("ws", "t1"));

        assertEquals(DataMapErrorCode.CRAWL_TASK_RUNNING, failure.getErrorCode());
        verify(taskMapper, never()).delete(any(), any());
    }

    @Test
    void anOfflineTaskWithAnActiveRunCannotBeDeleted() {
        when(taskMapper.findByIdForWorkspace("ws", "t1")).thenReturn(task("OFFLINE"));
        when(crawlMapper.countActiveByTask("ws", "t1")).thenReturn(1L);

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.delete("ws", "t1"));

        assertEquals(DataMapErrorCode.CRAWL_TASK_RUNNING, failure.getErrorCode());
    }

    @Test
    void anOfflineTaskIsRemovedWithoutTouchingItsRunsDirectly() {
        when(taskMapper.findByIdForWorkspace("ws", "t1")).thenReturn(task("OFFLINE"));
        when(taskMapper.delete("ws", "t1")).thenReturn(1);

        service.delete("ws", "t1");

        verify(taskMapper).delete("ws", "t1");
    }

    @Test
    void anOfflineTaskCannotBeStartedManually() {
        when(taskMapper.findByIdForWorkspace("ws", "t1")).thenReturn(task("OFFLINE"));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.run("ws", "actor", "t1"));

        assertEquals(DataMapErrorCode.CRAWL_TASK_NOT_RUNNABLE, failure.getErrorCode());
    }

    @Test
    void aTaskWithAnActiveRunIsNotStartedTwice() {
        when(taskMapper.findByIdForWorkspace("ws", "t1")).thenReturn(task("RUNNING"));
        when(crawlMapper.countActiveByTask("ws", "t1")).thenReturn(1L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.run("ws", "actor", "t1"));

        assertEquals(DataMapErrorCode.CRAWL_BUSY, failure.getErrorCode());
        verify(crawlService, never()).submitTaskRun(any(), any(), any(), any(), any());
    }

    @Test
    void aManualRunCarriesTheStoredExclusions() {
        DataMapCrawlTaskEntity stored = task("RUNNING");
        stored.setExcludePattern("^tmp_");
        stored.setExcludeTables("[\"audit_log\"]");
        when(taskMapper.findByIdForWorkspace("ws", "t1")).thenReturn(stored);

        service.run("ws", "actor", "t1");

        verify(crawlService)
                .submitTaskRun(
                        eq("ws"),
                        eq("actor"),
                        argThat(spec -> "t1".equals(spec.taskId())
                                && "^tmp_".equals(spec.excludePattern())
                                && List.of("audit_log").equals(spec.excludeTables())),
                        eq(DataMapCrawlTriggerType.MANUAL),
                        isNull());
    }

    @Test
    void aRetryStaysOwnedByTheTaskThatFailed() {
        DataMapCrawlTaskEntity stored = task("OFFLINE");
        stored.setExcludePattern("^tmp_");
        when(taskMapper.findByIdForWorkspace("ws", "t1")).thenReturn(stored);

        service.retry("ws", "actor", "t1", "c9");

        verify(crawlService)
                .submitTaskRun(
                        eq("ws"),
                        eq("actor"),
                        argThat(spec -> "t1".equals(spec.taskId()) && "^tmp_".equals(spec.excludePattern())),
                        eq(DataMapCrawlTriggerType.MANUAL),
                        eq("c9"));
    }

    @Test
    void aDueOccurrenceIsAdvancedBeforeItIsSubmitted() {
        DataMapCrawlTaskEntity due = task("RUNNING");
        due.setScheduleType("CRON");
        due.setCronExpression("0 0 3 * * *");
        due.setNextFireTime(LocalDateTime.now().minusMinutes(1));
        when(taskMapper.listDue(any(), eq(20))).thenReturn(List.of(due));
        when(taskMapper.advanceNextFire(eq("t1"), any(), any())).thenReturn(1);
        when(crawlService.submitTaskRun(any(), any(), any(), any(), any())).thenReturn(new DataMapCrawlVO());

        assertEquals(1, service.dispatchDue());

        verify(taskMapper).advanceNextFire(eq("t1"), any(), any());
        verify(crawlService)
                .submitTaskRun(
                        eq("ws"),
                        eq(CommonConstants.SYSTEM_USER),
                        any(),
                        eq(DataMapCrawlTriggerType.SCHEDULED),
                        isNull());
    }

    @Test
    void anOccurrenceAlreadyAdvancedByAnotherPollerIsNotSubmitted() {
        DataMapCrawlTaskEntity due = task("RUNNING");
        due.setScheduleType("CRON");
        due.setCronExpression("0 0 3 * * *");
        due.setNextFireTime(LocalDateTime.now().minusMinutes(1));
        when(taskMapper.listDue(any(), eq(20))).thenReturn(List.of(due));
        when(taskMapper.advanceNextFire(eq("t1"), any(), any())).thenReturn(0);

        assertEquals(0, service.dispatchDue());

        verify(crawlService, never()).submitTaskRun(any(), any(), any(), any(), any());
    }

    @Test
    void anInvalidStoredCronSkipsOnlyItsOwnOccurrence() {
        DataMapCrawlTaskEntity due = task("RUNNING");
        due.setScheduleType("CRON");
        due.setCronExpression("broken");
        due.setNextFireTime(LocalDateTime.now().minusMinutes(1));
        when(taskMapper.listDue(any(), eq(20))).thenReturn(List.of(due));

        assertEquals(0, service.dispatchDue());

        verify(taskMapper, never()).advanceNextFire(any(), any(), any());
        verify(crawlService, never()).submitTaskRun(any(), any(), any(), any(), any());
    }

    @Test
    void schedulePreviewReturnsTheNextThreeOccurrencesInOrder() {
        List<LocalDateTime> occurrences = service.previewSchedule("0 0 2 * * *");

        assertEquals(3, occurrences.size());
        assertTrue(occurrences.get(0).isAfter(LocalDateTime.now()));
        assertTrue(occurrences.get(1).isAfter(occurrences.get(0)));
        assertTrue(occurrences.get(2).isAfter(occurrences.get(1)));
    }

    @Test
    void schedulePreviewRefusesAnInvalidExpression() {
        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.previewSchedule("broken"));

        assertEquals(DataMapErrorCode.CRAWL_TASK_CRON_INVALID, failure.getErrorCode());
    }

    @Test
    void takingATaskOfflineClearsItsNextOccurrence() {
        when(taskMapper.findByIdForWorkspace("ws", "t1")).thenReturn(task("RUNNING"));
        when(taskMapper.transitionStatus(eq("ws"), eq("t1"), eq("RUNNING"), eq("OFFLINE"), isNull(), any(), any()))
                .thenReturn(1);

        DataMapCrawlTaskVO task = service.changeStatus("ws", "actor", "t1", DataMapCrawlTaskStatus.OFFLINE);

        assertEquals("OFFLINE", task.getStatus());
        assertNull(task.getNextFireTime());
    }

    private static DataMapCrawlTaskSaveDTO input(DataMapCrawlScheduleType scheduleType, String cron) {
        DataMapCrawlTaskSaveDTO input = new DataMapCrawlTaskSaveDTO();
        input.setName("  orders 采集 ");
        input.setDatasourceId("ds");
        input.setDatabase("demo");
        input.setOwnerId("owner");
        input.setScheduleType(scheduleType);
        input.setCronExpression(cron);
        input.setStatus(DataMapCrawlTaskStatus.RUNNING);
        return input;
    }

    private static DataMapCrawlTaskEntity task(String status) {
        DataMapCrawlTaskEntity row = new DataMapCrawlTaskEntity();
        row.setId("t1");
        row.setWorkspaceId("ws");
        row.setName("orders 采集");
        row.setDatasourceId("ds");
        row.setDatabaseName("demo");
        row.setSchemaName("");
        row.setOwnerId("owner");
        row.setStatus(status);
        row.setScheduleType("MANUAL");
        row.setExcludeTables("[]");
        return row;
    }
}
