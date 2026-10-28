package io.yak.ops.business.datamap.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.crawl.impl.DataMapCrawlServiceImpl;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlSubmitDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlVO;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.datamap.DataMapCrawlEntity;
import io.yak.ops.dao.entity.datamap.DataMapCrawlFailureEntity;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlMapper;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;

/** Verifies the submission paths that must never leave the datasource scope lock behind. */
class DataMapCrawlServiceTest {

    @Mock
    private DataMapCrawlMapper crawlMapper;

    @Mock
    private DataMapCrawlWorker worker;

    @Mock
    private DataSourceLookupService dataSourceLookup;

    @Mock
    private TaskExecutor crawlExecutor;

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
    void submissionIsPendingAndCoversTheRequestedNamespace() {
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("MYSQL", "demo", null, 7));
        DataMapCrawlVO run = service.submit("ws", "user", submit("ds", "analytics", null));
        assertEquals("PENDING", run.getStatus());
        assertEquals("analytics", run.getDatabase());
        assertEquals("", run.getSchema());
        verify(crawlExecutor).execute(any());
    }

    @Test
    void aSecondActiveRunIsRefusedByTheIndex() {
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("MYSQL", "demo", null, 7));
        when(crawlMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_data_map_crawl_active"));
        BusinessException failure =
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> service.submit("ws", "user", submit("ds", "analytics", null)));
        assertEquals(DataMapErrorCode.CRAWL_BUSY, failure.getErrorCode());
    }

    @Test
    void aFullQueueReleasesTheScopeLockAndReportsBusy() {
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("MYSQL", "demo", null, 7));
        doThrow(new TaskRejectedException("queue full")).when(crawlExecutor).execute(any());
        BusinessException failure =
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> service.submit("ws", "user", submit("ds", "analytics", null)));
        assertEquals(DataMapErrorCode.CRAWL_BUSY, failure.getErrorCode());
        verify(crawlMapper).reject(anyString(), eq(DataMapErrorCode.CRAWL_BUSY.getMessage()), any());
    }

    @Test
    void anUnresolvableDatasourceIsAScopeFailureNotABusyDatasource() {
        when(dataSourceLookup.dataSource("ws", "ds")).thenThrow(new IllegalArgumentException("absent"));
        BusinessException failure =
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> service.submit("ws", "user", submit("ds", "analytics", null)));
        assertEquals(DataMapErrorCode.CRAWL_SCOPE_INVALID, failure.getErrorCode());
    }

    @Test
    void aSubmissionWithoutADatabaseIsRefusedBeforeAnyRunRowExists() {
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("MYSQL", "demo", null, 7));
        BusinessException failure =
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> service.submit("ws", "user", submit("ds", "  ", null)));
        assertEquals(DataMapErrorCode.CRAWL_DATABASE_REQUIRED, failure.getErrorCode());
        verify(crawlMapper, never()).insert(any());
        verify(crawlExecutor, never()).execute(any());
    }

    @Test
    void anUncompilableExcludePatternIsRefusedBeforeAnyRunRowExists() {
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("MYSQL", "demo", null, 7));
        DataMapCrawlSubmitDTO input = submit("ds", "analytics", null);
        input.setExcludePattern("^tmp_[a-");
        BusinessException failure =
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> service.submit("ws", "user", input));
        assertEquals(DataMapErrorCode.CRAWL_PATTERN_INVALID, failure.getErrorCode());
        verify(crawlMapper, never()).insert(any());
        verify(crawlExecutor, never()).execute(any());
    }

    @Test
    void aRetryMustPointAtAFailedRunWithTheSameNamespace() {
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("MYSQL", "demo", null, 7));
        DataMapCrawlSubmitDTO missing = submit("ds", "analytics", null);
        missing.setRetryOf("c0");
        BusinessException failure =
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> service.submit("ws", "user", missing));
        assertEquals(DataMapErrorCode.CRAWL_RETRY_INVALID, failure.getErrorCode());
        verify(crawlMapper, never()).insert(any());

        DataMapCrawlEntity source = new DataMapCrawlEntity();
        source.setDatasourceId("ds");
        source.setDatabaseName("analytics");
        when(crawlMapper.findByIdForWorkspace("ws", "c0")).thenReturn(source);
        when(crawlMapper.listFailures("c0", 200)).thenReturn(List.of());

        BusinessException noFailures =
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> service.submit("ws", "user", missing));
        assertEquals(DataMapErrorCode.CRAWL_RETRY_INVALID, noFailures.getErrorCode());
        verify(crawlMapper, never()).insert(any());
    }

    @Test
    void aValidRetryAndPatternAreStoredForTheWorker() {
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("MYSQL", "demo", null, 7));
        DataMapCrawlEntity source = new DataMapCrawlEntity();
        source.setDatasourceId("ds");
        source.setDatabaseName("analytics");
        DataMapCrawlFailureEntity failure = new DataMapCrawlFailureEntity();
        failure.setTableName("orders");
        when(crawlMapper.findByIdForWorkspace("ws", "c0")).thenReturn(source);
        when(crawlMapper.listFailures("c0", 200)).thenReturn(List.of(failure));

        DataMapCrawlSubmitDTO input = submit("ds", "analytics", null);
        input.setRetryOf("c0");
        input.setExcludePattern("^tmp_");
        DataMapCrawlVO run = service.submit("ws", "user", input);

        assertEquals("c0", run.getRetryOf());
        assertEquals("^tmp_", run.getExcludePattern());
        assertEquals("MANUAL", run.getTriggerType());
        verify(crawlExecutor).execute(any());
    }

    @Test
    void simultaneousSubmissionsForOneDatasourceHaveExactlyOneWinner() throws Exception {
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("MYSQL", "demo", null, 7));
        Set<String> active = ConcurrentHashMap.newKeySet();
        when(crawlMapper.insert(any())).thenAnswer(call -> {
            DataMapCrawlEntity row = call.getArgument(0);
            if (!active.add(row.getWorkspaceId() + "|" + row.getDatasourceId())) {
                throw new DuplicateKeyException("uk_data_map_crawl_active");
            }
            return 1;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Object> submission = () -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    return service.submit("ws", "user", submit("ds", "analytics", null));
                } catch (BusinessException busy) {
                    return busy.getErrorCode();
                }
            };
            Future<Object> first = pool.submit(submission);
            Future<Object> second = pool.submit(submission);
            start.countDown();
            List<Object> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter(DataMapCrawlVO.class::isInstance).count());
            assertEquals(1, outcomes.stream().filter(DataMapErrorCode.CRAWL_BUSY::equals).count());
        } finally {
            pool.shutdownNow();
        }
        // The loser is refused by the index, not by a read: the service never selects a run first.
        verify(crawlExecutor, times(1)).execute(any());
        verify(crawlMapper, times(2)).insert(any());
        verify(crawlMapper, never()).findById(anyString());
        verify(crawlMapper, never()).findByIdForWorkspace(anyString(), anyString());
    }

    private static DataMapCrawlSubmitDTO submit(String datasourceId, String database, String schema) {
        DataMapCrawlSubmitDTO input = new DataMapCrawlSubmitDTO();
        input.setDatasourceId(datasourceId);
        input.setDatabase(database);
        input.setSchema(schema);
        return input;
    }
}
