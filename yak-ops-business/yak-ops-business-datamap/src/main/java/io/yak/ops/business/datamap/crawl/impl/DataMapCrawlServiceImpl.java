package io.yak.ops.business.datamap.crawl.impl;

import io.yak.ops.business.datamap.crawl.DataMapCrawlRecovery;
import io.yak.ops.business.datamap.crawl.DataMapCrawlService;
import io.yak.ops.business.datamap.crawl.DataMapCrawlTaskSpec;
import io.yak.ops.business.datamap.crawl.DataMapCrawlWorker;
import io.yak.ops.business.datamap.support.DataMapScope;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlSubmitDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlFailureVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlSummaryVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.datamap.DataMapCrawlStatus;
import io.yak.ops.common.enums.datamap.DataMapCrawlTaskStatus;
import io.yak.ops.common.enums.datamap.DataMapCrawlTriggerType;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datamap.DataMapCrawlEntity;
import io.yak.ops.dao.entity.datamap.DataMapCrawlFailureEntity;
import io.yak.ops.dao.mapper.datamap.DataMapColumnCoverageRow;
import io.yak.ops.dao.mapper.datamap.DataMapColumnMapper;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlMapper;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlTaskMapper;
import io.yak.ops.dao.mapper.datamap.DataMapRunSummaryRow;
import io.yak.ops.dao.mapper.datamap.DataMapTaskFailureRow;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Default crawl submission and read model.
 *
 * <p>Concurrency is two constraints, not one: the executor limits throughput, while
 * {@code uk_data_map_crawl_active} limits one active run per datasource. The second is the one that
 * makes the first safe, because two workers can only ever point at two different datasources.
 */
@Service
public class DataMapCrawlServiceImpl implements DataMapCrawlService, DataMapCrawlRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(DataMapCrawlServiceImpl.class);

    private static final int FAILURE_LIMIT = 200;
    private static final int DEFAULT_SUMMARY_HOURS = 24;
    private static final String RESTART_ERROR = "应用重启导致采集中断";
    private static final long RECOVERY_WAIT_MILLIS = 30_000L;

    @Resource
    private DataMapCrawlMapper crawlMapper;

    @Resource
    private DataMapCrawlTaskMapper taskMapper;

    @Resource
    private DataMapColumnMapper columnMapper;

    @Resource
    private DataMapCrawlWorker worker;

    @Resource
    private DataSourceLookupService dataSourceLookup;

    @Resource(name = "dataMapCrawlExecutor")
    private TaskExecutor crawlExecutor;

    private final CountDownLatch recoveryComplete = new CountDownLatch(1);

    @Override
    public PagingData<DataMapCrawlVO> list(
            String workspaceId,
            String datasourceId,
            String taskId,
            DataMapCrawlStatus status,
            DataMapCrawlTriggerType triggerType,
            Boolean failed,
            Integer days,
            int pageNo,
            int pageSize) {
        String datasourceFilter = StringUtils.trimToNull(datasourceId);
        String taskFilter = StringUtils.trimToNull(taskId);
        String statusFilter = status == null ? null : status.name();
        String triggerFilter = triggerType == null ? null : triggerType.name();
        LocalDateTime since = days == null ? null : DateUtils.now().minusDays(days);
        long total = crawlMapper.count(
                workspaceId, datasourceFilter, taskFilter, statusFilter, triggerFilter, failed, since);
        List<DataMapCrawlVO> rows = total == 0
                ? List.of()
                : crawlMapper
                        .listPage(
                                workspaceId,
                                datasourceFilter,
                                taskFilter,
                                statusFilter,
                                triggerFilter,
                                failed,
                                since,
                                pageSize,
                                (pageNo - 1) * pageSize)
                        .stream()
                        .map(DataMapCrawlServiceImpl::toVO)
                        .toList();
        return PagingData.from(PageData.of(rows, total, pageNo, pageSize));
    }

    @Override
    public DataMapCrawlSummaryVO summary(String workspaceId, Integer hours) {
        int windowHours = hours == null ? DEFAULT_SUMMARY_HOURS : hours;
        DataMapRunSummaryRow runs =
                crawlMapper.summarizeRuns(workspaceId, DateUtils.now().minusHours(windowHours));
        DataMapTaskFailureRow backlog = crawlMapper.summarizeLatestTaskFailures(workspaceId);
        DataMapColumnCoverageRow coverage = columnMapper.summarizeCoverage(workspaceId);
        DataMapCrawlSummaryVO summary = new DataMapCrawlSummaryVO();
        summary.setWindowHours(windowHours);
        summary.setTaskTotal(taskMapper.count(workspaceId, null, null, null, null));
        summary.setOnlineTaskCount(
                taskMapper.count(workspaceId, null, DataMapCrawlTaskStatus.RUNNING.name(), null, null));
        summary.setRunTotal(count(runs == null ? null : runs.getTotal()));
        summary.setRunSucceeded(count(runs == null ? null : runs.getSucceeded()));
        summary.setRunPartial(count(runs == null ? null : runs.getPartial()));
        summary.setRunFailed(count(runs == null ? null : runs.getFailed()));
        summary.setRunInterrupted(count(runs == null ? null : runs.getInterrupted()));
        summary.setRunRunning(count(runs == null ? null : runs.getRunning()));
        summary.setRunPending(count(runs == null ? null : runs.getPending()));
        summary.setFailedTableCount(count(backlog == null ? null : backlog.getFailedTables()));
        summary.setAbnormalTaskCount(count(backlog == null ? null : backlog.getAbnormalTasks()));
        summary.setColumnTotal(count(coverage == null ? null : coverage.getColumnTotal()));
        summary.setDescribedColumnCount(count(coverage == null ? null : coverage.getDescribedColumns()));
        return summary;
    }

    /** An empty workspace aggregates to zero rows, which some dialects report as null. */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }

    @Override
    public DataMapCrawlVO submit(String workspaceId, String actor, DataMapCrawlSubmitDTO input) {
        awaitRecovery();
        String datasourceId = StringUtils.trimToNull(input.getDatasourceId());
        DataSourceInfo datasource = resolveDatasource(workspaceId, datasourceId);
        // A database is mandatory: inheriting the datasource binding would crawl every visible
        // catalog and store them under one empty identity, so the catalog could never filter by
        // database and same-named tables would overwrite each other.
        String database = StringUtils.trimToNull(input.getDatabase());
        if (database == null) throw new BusinessException(DataMapErrorCode.CRAWL_DATABASE_REQUIRED);
        DataMapScope scope = DataMapScope.resolve(database, input.getSchema(), datasource);
        String excludePattern = validatePattern(input.getExcludePattern());

        // A retry re-enumerates exactly the names the source run failed on, in the same namespace;
        // anything else makes the retry either a full run or a no-op wearing a retry badge.
        String retryOf = StringUtils.trimToNull(input.getRetryOf());
        if (retryOf != null) {
            DataMapCrawlEntity source = crawlMapper.findByIdForWorkspace(workspaceId, retryOf);
            List<DataMapCrawlFailureEntity> failures =
                    source == null ? List.of() : crawlMapper.listFailures(retryOf, FAILURE_LIMIT);
            if (source == null
                    || !source.getDatasourceId().equals(datasourceId)
                    || !source.getDatabaseName().equals(scope.databaseName())
                    || failures.isEmpty()) {
                throw new BusinessException(DataMapErrorCode.CRAWL_RETRY_INVALID);
            }
        }
        return createRun(
                workspaceId,
                actor,
                datasourceId,
                datasource,
                scope,
                excludePattern,
                null,
                retryOf,
                null,
                DataMapCrawlTriggerType.MANUAL);
    }

    @Override
    public DataMapCrawlVO submitTaskRun(
            String workspaceId,
            String actor,
            DataMapCrawlTaskSpec task,
            DataMapCrawlTriggerType triggerType,
            String retryOf) {
        awaitRecovery();
        String datasourceId = StringUtils.trimToNull(task.datasourceId());
        DataSourceInfo datasource = resolveDatasource(workspaceId, datasourceId);
        String database = StringUtils.trimToNull(task.database());
        if (database == null) throw new BusinessException(DataMapErrorCode.CRAWL_DATABASE_REQUIRED);
        DataMapScope scope = DataMapScope.resolve(database, task.schema(), datasource);
        String sourceRunId = validateRetry(workspaceId, task.taskId(), datasourceId, scope, retryOf);
        String excludePattern = validatePattern(task.excludePattern());
        String excludeTables =
                task.excludeTables() == null || task.excludeTables().isEmpty()
                        ? null
                        : JSONUtils.toJson(task.excludeTables());
        return createRun(
                workspaceId,
                actor,
                datasourceId,
                datasource,
                scope,
                excludePattern,
                excludeTables,
                sourceRunId,
                task.taskId(),
                triggerType);
    }

    /**
     * Resolves a retry source that belongs to the same task and namespace and actually failed.
     * Anything else would make the retry a full run or an empty run wearing a retry badge.
     */
    private String validateRetry(
            String workspaceId, String taskId, String datasourceId, DataMapScope scope, String retryOf) {
        String sourceRunId = StringUtils.trimToNull(retryOf);
        if (sourceRunId == null) return null;
        DataMapCrawlEntity source = crawlMapper.findByIdForWorkspace(workspaceId, sourceRunId);
        if (source == null
                || !taskId.equals(source.getTaskId())
                || !source.getDatasourceId().equals(datasourceId)
                || !source.getDatabaseName().equals(scope.databaseName())
                || crawlMapper.listFailures(sourceRunId, 1).isEmpty()) {
            throw new BusinessException(DataMapErrorCode.CRAWL_RETRY_INVALID);
        }
        return sourceRunId;
    }

    private DataSourceInfo resolveDatasource(String workspaceId, String datasourceId) {
        try {
            return dataSourceLookup.dataSource(workspaceId, datasourceId);
        } catch (RuntimeException failure) {
            throw new BusinessException(DataMapErrorCode.CRAWL_SCOPE_INVALID);
        }
    }

    private static String validatePattern(String excludePattern) {
        String pattern = StringUtils.trimToNull(excludePattern);
        if (pattern == null) return null;
        try {
            Pattern.compile(pattern);
        } catch (RuntimeException failure) {
            throw new BusinessException(DataMapErrorCode.CRAWL_PATTERN_INVALID);
        }
        return pattern;
    }

    private DataMapCrawlVO createRun(
            String workspaceId,
            String actor,
            String datasourceId,
            DataSourceInfo datasource,
            DataMapScope scope,
            String excludePattern,
            String excludeTables,
            String retryOf,
            String taskId,
            DataMapCrawlTriggerType triggerType) {
        LocalDateTime now = DateUtils.now();
        DataMapCrawlEntity row = new DataMapCrawlEntity();
        row.setId(IdUtils.nextId());
        row.setWorkspaceId(workspaceId);
        row.setTaskId(taskId);
        row.setDatasourceId(datasourceId);
        row.setDatasourceRevision(datasource.revision());
        row.setDatabaseName(scope.databaseName());
        row.setSchemaName(scope.schemaName());
        row.setStatus(DataMapCrawlStatus.PENDING.name());
        row.setActiveScope(1);
        row.setTableCount(0);
        row.setColumnCount(0);
        row.setAddedCount(0);
        row.setUpdatedCount(0);
        row.setRemovedCount(0);
        row.setFailedCount(0);
        row.setFilteredCount(0);
        row.setTriggerType(triggerType.name());
        row.setExcludePattern(excludePattern);
        row.setExcludeTables(excludeTables);
        row.setRetryOf(retryOf);
        row.setCreatedTime(now);
        row.setCreateBy(StringUtils.trimToNull(actor) == null ? CommonConstants.SYSTEM_USER : actor);
        try {
            crawlMapper.insert(row);
        } catch (DuplicateKeyException busy) {
            throw new BusinessException(DataMapErrorCode.CRAWL_BUSY);
        }
        try {
            crawlExecutor.execute(() -> worker.run(row.getId()));
        } catch (TaskRejectedException rejected) {
            crawlMapper.reject(row.getId(), DataMapErrorCode.CRAWL_BUSY.getMessage(), DateUtils.now());
            throw new BusinessException(DataMapErrorCode.CRAWL_BUSY);
        }
        return toVO(row);
    }

    @Override
    public DataMapCrawlDetailVO detail(String workspaceId, String crawlId) {
        DataMapCrawlEntity row = crawlMapper.findByIdForWorkspace(workspaceId, crawlId);
        if (row == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        DataMapCrawlDetailVO detail = new DataMapCrawlDetailVO();
        detail.setCrawl(toVO(row));
        detail.setFailures(crawlMapper.listFailures(crawlId, FAILURE_LIMIT).stream()
                .map(failure ->
                        new DataMapCrawlFailureVO(failure.getTableName(), failure.getError(), failure.getCreatedTime()))
                .toList());
        return detail;
    }

    @Override
    public void recoverInterruptedRuns() {
        try {
            int swept = crawlMapper.sweepInterrupted(RESTART_ERROR, DateUtils.now());
            if (swept > 0) LOG.warn("data-map crawl recovery failed {} interrupted runs", swept);
        } catch (RuntimeException failure) {
            LOG.error("data-map crawl recovery could not run", failure);
        } finally {
            recoveryComplete.countDown();
        }
    }

    /** Waits for startup recovery so a stale lock is never reported as a busy datasource. */
    private void awaitRecovery() {
        try {
            if (!recoveryComplete.await(RECOVERY_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new BusinessException(DataMapErrorCode.CRAWL_BUSY);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BusinessException(DataMapErrorCode.CRAWL_BUSY);
        }
    }

    private static DataMapCrawlVO toVO(DataMapCrawlEntity row) {
        LocalDateTime finished = row.getFinishedTime();
        Long duration = finished == null || row.getStartedTime() == null
                ? null
                : Duration.between(row.getStartedTime(), finished).getSeconds();
        DataMapCrawlVO vo = new DataMapCrawlVO();
        vo.setId(row.getId());
        vo.setTaskId(row.getTaskId());
        vo.setTaskName(row.getTaskName());
        vo.setDatasourceId(row.getDatasourceId());
        vo.setDatabase(row.getDatabaseName());
        vo.setSchema(row.getSchemaName());
        vo.setStatus(row.getStatus());
        vo.setTableCount(row.getTableCount() == null ? 0 : row.getTableCount());
        vo.setColumnCount(row.getColumnCount() == null ? 0 : row.getColumnCount());
        vo.setAddedCount(row.getAddedCount() == null ? 0 : row.getAddedCount());
        vo.setUpdatedCount(row.getUpdatedCount() == null ? 0 : row.getUpdatedCount());
        vo.setRemovedCount(row.getRemovedCount() == null ? 0 : row.getRemovedCount());
        vo.setFailedCount(row.getFailedCount() == null ? 0 : row.getFailedCount());
        vo.setFilteredCount(row.getFilteredCount() == null ? 0 : row.getFilteredCount());
        vo.setTriggerType(row.getTriggerType() == null ? DataMapCrawlTriggerType.MANUAL.name() : row.getTriggerType());
        vo.setExcludePattern(row.getExcludePattern());
        vo.setDurationSeconds(duration);
        vo.setRetryOf(row.getRetryOf());
        vo.setError(row.getError());
        vo.setCreatedTime(row.getCreatedTime());
        vo.setStartedTime(row.getStartedTime());
        vo.setFinishedTime(row.getFinishedTime());
        vo.setCreateBy(row.getCreateBy());
        return vo;
    }
}
