package io.yak.ops.business.datamap.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.crawl.impl.DataMapCrawlServiceImpl;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlSummaryVO;
import io.yak.ops.dao.mapper.datamap.DataMapColumnCoverageRow;
import io.yak.ops.dao.mapper.datamap.DataMapColumnMapper;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlMapper;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlTaskMapper;
import io.yak.ops.dao.mapper.datamap.DataMapRunQualityRow;
import io.yak.ops.dao.mapper.datamap.DataMapRunSummaryRow;
import io.yak.ops.dao.mapper.datamap.DataMapTaskFailureRow;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Verifies the health band figures.
 *
 * <p>Two properties matter more than the arithmetic: an empty Workspace must read as zeros rather
 * than nulls, and the run window must follow the caller instead of a hard-coded day, because the
 * band is the only place an operator sees "what happened just now".
 */
class DataMapCrawlSummaryTest {

    @Mock
    private DataMapCrawlMapper crawlMapper;

    @Mock
    private DataMapCrawlTaskMapper taskMapper;

    @Mock
    private DataMapColumnMapper columnMapper;

    @InjectMocks
    private DataMapCrawlServiceImpl service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        service.recoverInterruptedRuns();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void anEmptyWorkspaceReadsAsZerosInsteadOfNulls() {
        when(taskMapper.count(eq("ws"), isNull(), isNull(), isNull(), isNull())).thenReturn(0L);
        when(taskMapper.count(eq("ws"), isNull(), eq("RUNNING"), isNull(), isNull())).thenReturn(0L);
        // MySQL reports SUM over no rows as null, so the mapper row itself carries nulls.
        when(crawlMapper.summarizeRuns(eq("ws"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DataMapRunSummaryRow());
        when(crawlMapper.summarizeRunQuality(eq("ws"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DataMapRunQualityRow());
        when(crawlMapper.percentile95Duration(eq("ws"), org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(crawlMapper.summarizeLatestTaskFailures("ws")).thenReturn(new DataMapTaskFailureRow());
        when(columnMapper.summarizeCoverage("ws")).thenReturn(new DataMapColumnCoverageRow());

        DataMapCrawlSummaryVO summary = service.summary("ws", 24);

        assertEquals(0, summary.getRunTotal());
        assertEquals(0, summary.getFailedTableCount());
        assertEquals(0, summary.getColumnTotal());
        assertEquals(0, summary.getWeekRunTotal());
        assertEquals(0, summary.getAvgDurationSeconds());
        assertEquals(0, summary.getP95DurationSeconds());
        assertEquals(24, summary.getWindowHours());
    }

    @Test
    void everyOutcomeIsReportedSeparatelySoPartialSuccessStaysVisible() {
        DataMapRunSummaryRow runs = new DataMapRunSummaryRow();
        runs.setTotal(12L);
        runs.setSucceeded(8L);
        runs.setPartial(2L);
        runs.setFailed(1L);
        runs.setInterrupted(0L);
        runs.setRunning(1L);
        runs.setPending(0L);
        when(taskMapper.count(eq("ws"), isNull(), isNull(), isNull(), isNull())).thenReturn(5L);
        when(taskMapper.count(eq("ws"), isNull(), eq("RUNNING"), isNull(), isNull())).thenReturn(3L);
        when(crawlMapper.summarizeRuns(eq("ws"), org.mockito.ArgumentMatchers.any())).thenReturn(runs);
        DataMapRunQualityRow quality = new DataMapRunQualityRow();
        quality.setTodayTotal(4L);
        quality.setTodaySucceeded(4L);
        quality.setWeekTotal(28L);
        quality.setWeekSucceeded(27L);
        quality.setAvgSeconds(42L);
        when(crawlMapper.summarizeRunQuality(eq("ws"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(quality);
        when(crawlMapper.percentile95Duration(eq("ws"), org.mockito.ArgumentMatchers.any())).thenReturn(61L);
        DataMapTaskFailureRow backlog = new DataMapTaskFailureRow();
        backlog.setFailedTables(7L);
        backlog.setAbnormalTasks(2L);
        when(crawlMapper.summarizeLatestTaskFailures("ws")).thenReturn(backlog);
        DataMapColumnCoverageRow coverage = new DataMapColumnCoverageRow();
        coverage.setColumnTotal(200L);
        coverage.setDescribedColumns(150L);
        when(columnMapper.summarizeCoverage("ws")).thenReturn(coverage);

        DataMapCrawlSummaryVO summary = service.summary("ws", 6);

        assertEquals(5, summary.getTaskTotal());
        assertEquals(3, summary.getOnlineTaskCount());
        assertEquals(8, summary.getRunSucceeded());
        assertEquals(2, summary.getRunPartial());
        assertEquals(1, summary.getRunFailed());
        assertEquals(1, summary.getRunRunning());
        assertEquals(7, summary.getFailedTableCount());
        assertEquals(2, summary.getAbnormalTaskCount());
        assertEquals(200, summary.getColumnTotal());
        assertEquals(150, summary.getDescribedColumnCount());
        assertEquals(4, summary.getTodayRunTotal());
        assertEquals(4, summary.getTodayRunSucceeded());
        assertEquals(28, summary.getWeekRunTotal());
        assertEquals(27, summary.getWeekRunSucceeded());
        assertEquals(42, summary.getAvgDurationSeconds());
        assertEquals(61, summary.getP95DurationSeconds());
    }

    @Test
    void theRunWindowFollowsTheCaller() {
        when(crawlMapper.summarizeRuns(eq("ws"), org.mockito.ArgumentMatchers.any())).thenReturn(new DataMapRunSummaryRow());
        when(crawlMapper.summarizeLatestTaskFailures("ws")).thenReturn(new DataMapTaskFailureRow());
        when(columnMapper.summarizeCoverage("ws")).thenReturn(new DataMapColumnCoverageRow());

        service.summary("ws", 48);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(crawlMapper).summarizeRuns(eq("ws"), since.capture());
        LocalDateTime expected = LocalDateTime.now().minusHours(48);
        assertTrue(Math.abs(java.time.Duration.between(expected, since.getValue()).toMinutes()) <= 1,
                "window start should be 48 hours back, was " + since.getValue());
    }

    @Test
    void aMissingWindowFallsBackToADay() {
        when(crawlMapper.summarizeRuns(eq("ws"), org.mockito.ArgumentMatchers.any())).thenReturn(new DataMapRunSummaryRow());
        when(crawlMapper.summarizeLatestTaskFailures("ws")).thenReturn(new DataMapTaskFailureRow());
        when(columnMapper.summarizeCoverage("ws")).thenReturn(new DataMapColumnCoverageRow());

        assertEquals(24, service.summary("ws", null).getWindowHours());
    }
}
