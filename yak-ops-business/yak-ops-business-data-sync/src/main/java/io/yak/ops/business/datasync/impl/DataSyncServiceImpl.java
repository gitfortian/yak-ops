package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.DataSyncService;
import io.yak.ops.business.datasync.catalog.DataSyncCatalogColumns;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.execution.executor.OfflineSyncExecutor;
import io.yak.ops.business.datasync.execution.executor.RealtimeSyncExecutor;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionRegistry;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleDefinition;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFire;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFireListener;
import io.yak.ops.business.datasync.scheduler.ScheduleEngine;
import io.yak.ops.business.datasync.scheduler.ScheduleEngineException;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingPreviewDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRealtimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRetryPolicyDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRuntimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncFieldMappingVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingPreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsFailureRankVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsStatusMetricVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsSummaryVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsTrendPointVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRealtimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSchedulePreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncOperationsRange;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncExecutionEventEntity;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncExecutionEventRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstancePageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsFailureStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsMetricsRepository;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsStatusStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsSummaryStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsTrendStats;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskPageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaCompatibility;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaMapper;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import jakarta.annotation.Resource;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 实现 Workspace-scoped Data Sync 任务定义持久化和任务/实例查询。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Service
public class DataSyncServiceImpl implements DataSyncService, DataSyncScheduleFireListener {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncServiceImpl.class);
    private static final Set<String> REALTIME_TARGET_TYPES = Set.of("MYSQL", "POSTGRE_SQL", "ORACLE");

    @Resource
    private DataSyncTaskRepository taskRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncAttemptRepository attemptRepository;

    @Resource
    private DataSyncExecutionEventRepository executionEventRepository;

    @Resource
    private DataSyncOperationsMetricsRepository operationsMetricsRepository;

    @Resource
    private DataSyncScheduleRepository scheduleRepository;

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private OfflineSyncExecutor offlineSyncExecutor;

    @Resource
    private RealtimeSyncExecutor realtimeSyncExecutor;

    @Resource
    private DataSyncExecutionRegistry executionRegistry;

    @Resource
    private DataSyncAttemptLifecycle attemptLifecycle;

    @Resource
    private ScheduleEngine scheduleEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO createTask(DataSyncTaskDTO dto) {
        return createTask(dto, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO createTask(DataSyncTaskDTO dto, String operatorUserId) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        String name = StringUtils.trimToNull(dto.getName());
        if (name == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "任务名称不能为空");
        ensureTaskNameAvailable(workspaceId, name, null);
        DataSyncType syncType = requireSyncType(dto.getSyncType());
        DataSyncMappingPreviewDTO resolvedScope =
                resolveMappingScope(BeanCopyUtils.copy(dto, DataSyncMappingPreviewDTO.class));
        validateTaskDefinition(syncType, dto, resolvedScope);
        requireCompatibleMapping(resolvedScope);

        DataSyncTaskEntity entity = new DataSyncTaskEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setName(name);
        entity.setSyncType(syncType);
        entity.setStatus(DataSyncTaskStatus.UNPUBLISHED);
        entity.setDesiredState(DataSyncDesiredState.STOPPED);
        applyDefinition(entity, dto, resolvedScope);
        entity.setDefinitionVersion(1);
        entity.initCreate(operatorUserId);

        if (taskRepository.add(entity) == null) {
            throw new DataSyncException(DataSyncErrorCode.CREATE_TASK_FAILED);
        }
        return toTaskVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto) {
        return updateTask(id, dto, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto, String operatorUserId) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity entity = requireTask(workspaceId, id);
        requireTaskStatus(entity, DataSyncTaskStatus.UNPUBLISHED, "已上线任务请先下线后再编辑");
        String name = StringUtils.trimToNull(dto.getName());
        if (name == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "任务名称不能为空");
        ensureTaskNameAvailable(workspaceId, name, id);
        DataSyncType syncType = requireSyncType(dto.getSyncType());
        if (entity.getSyncType() != syncType) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "同步类型创建后不允许修改");
        }
        DataSyncMappingPreviewDTO resolvedScope =
                resolveMappingScope(BeanCopyUtils.copy(dto, DataSyncMappingPreviewDTO.class));
        validateTaskDefinition(syncType, dto, resolvedScope);
        requireCompatibleMapping(resolvedScope);

        boolean executableDefinitionChanged = executableDefinitionChanged(entity, dto, resolvedScope);
        entity.setName(name);
        applyDefinition(entity, dto, resolvedScope);
        if (executableDefinitionChanged) {
            entity.setDefinitionVersion(Math.max(1, entity.getDefinitionVersion()) + 1);
        }
        entity.initUpdate(operatorUserId);

        if (taskRepository.update(workspaceId, entity) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED);
        }
        return toTaskVO(entity);
    }

    @Override
    public DataSyncTaskVO queryTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return toTaskVO(requireTask(workspaceId, id));
    }

    @Override
    public PagingData<DataSyncTaskVO> queryTaskPage(DataSyncTaskQueryDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY);
        if (CollectionUtils.isNotEmpty(dto.getSorts())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "任务分页暂不支持自定义排序");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskPageQuery query = new DataSyncTaskPageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getKeyword()),
                dto.getSyncType(),
                dto.getStatus(),
                StringUtils.trimToNull(dto.getSourceDataSourceId()),
                StringUtils.trimToNull(dto.getTargetDataSourceId()));
        PageData<DataSyncTaskEntity> page = taskRepository.queryPage(workspaceId, query);
        List<String> offlineTaskIds = page.records().stream()
                .filter(task -> task.getSyncType() == DataSyncType.OFFLINE)
                .map(DataSyncTaskEntity::getId)
                .toList();
        Map<String, DataSyncScheduleEntity> scheduleByTask = new HashMap<>();
        if (!offlineTaskIds.isEmpty()) {
            scheduleRepository
                    .queryByTasks(workspaceId, offlineTaskIds)
                    .forEach(schedule -> scheduleByTask.put(schedule.getTaskId(), schedule));
        }
        return PagingData.from(page.map(task -> toTaskListVO(task, scheduleByTask.get(task.getId()))));
    }

    @Override
    public PagingData<DataSyncTaskOperationVO> queryTaskOperationPage(DataSyncTaskQueryDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY);
        if (CollectionUtils.isNotEmpty(dto.getSorts())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "运维任务分页暂不支持自定义排序");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskPageQuery query = new DataSyncTaskPageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getKeyword()),
                dto.getSyncType(),
                DataSyncTaskStatus.PUBLISHED,
                StringUtils.trimToNull(dto.getSourceDataSourceId()),
                StringUtils.trimToNull(dto.getTargetDataSourceId()));
        return PagingData.from(
                taskRepository.queryPage(workspaceId, query).map(task -> toTaskOperationVO(workspaceId, task)));
    }

    @Override
    public DataSyncOperationsDashboardVO queryOperationsDashboard(DataSyncOperationsDashboardDTO dto) {
        if (dto == null || dto.getSyncType() == null || dto.getRange() == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "运维指标查询参数不完整");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        LocalDateTime rangeEnd = DateUtils.now();
        LocalDateTime rangeStart = operationsRangeStart(dto.getRange(), rangeEnd);
        DataSyncOperationsSummaryStats summaryStats =
                operationsMetricsRepository.querySummary(workspaceId, dto.getSyncType(), rangeStart, rangeEnd);
        List<DataSyncOperationsTrendStats> trendStats = operationsMetricsRepository.queryTrend(
                workspaceId,
                dto.getSyncType(),
                rangeStart,
                rangeEnd,
                dto.getRange().isHourly());
        List<DataSyncOperationsStatusStats> statusStats = operationsMetricsRepository.queryStatusDistribution(
                workspaceId, dto.getSyncType(), rangeStart, rangeEnd);
        List<DataSyncOperationsFailureStats> failureStats = operationsMetricsRepository.queryFailureRanking(
                workspaceId, dto.getSyncType(), rangeStart, rangeEnd, 5);

        DataSyncOperationsDashboardVO result = new DataSyncOperationsDashboardVO();
        result.setSyncType(dto.getSyncType().name());
        result.setRange(dto.getRange().name());
        result.setRangeStart(rangeStart);
        result.setRangeEnd(rangeEnd);
        result.setSummary(toOperationsSummaryVO(summaryStats));
        result.setTrend(toOperationsTrendVO(trendStats, dto.getRange(), rangeStart, rangeEnd));
        result.setStatusDistribution(toOperationsStatusDistribution(statusStats));
        result.setFailureRanking(
                failureStats.stream().map(this::toOperationsFailureRankVO).toList());
        return result;
    }

    @Override
    public DataSyncMappingPreviewVO previewMapping(DataSyncMappingPreviewDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "字段映射参数不完整");
        return previewResolvedMapping(resolveMappingScope(dto));
    }

    private DataSyncMappingPreviewVO previewResolvedMapping(DataSyncMappingPreviewDTO dto) {
        List<DataSourceCatalogColumnVO> sourceColumns = dataSourceService.queryCatalogColumns(
                dto.getSourceDataSourceId(),
                tablePath(dto.getSourceDatabase(), dto.getSourceSchema(), dto.getSourceTable()));
        List<DataSourceCatalogColumnVO> targetColumns = dataSourceService.queryCatalogColumns(
                dto.getTargetDataSourceId(),
                tablePath(dto.getTargetDatabase(), dto.getTargetSchema(), dto.getTargetTable()));

        Map<String, DataSourceCatalogColumnVO> targetByName = DataSyncCatalogColumns.indexByName(targetColumns);

        DataSyncMappingPreviewVO result = new DataSyncMappingPreviewVO();
        result.setMappings(sourceColumns.stream()
                .map(source ->
                        toFieldMapping(source, DataSyncCatalogColumns.findByName(targetByName, source.getName())))
                .toList());
        result.setCompatible(!sourceColumns.isEmpty()
                && result.getMappings().stream().allMatch(DataSyncFieldMappingVO::isCompatible));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO publishTask(String id) {
        return publishTask(id, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO publishTask(String id, String operatorUserId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, id);
        requireTaskStatus(task, DataSyncTaskStatus.UNPUBLISHED, "任务已经上线");
        validatePersistedTaskDefinition(task);
        task.setStatus(DataSyncTaskStatus.PUBLISHED);
        task.initUpdate(operatorUserId);
        if (taskRepository.update(workspaceId, task) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "上线任务失败");
        }
        return toTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO unpublishTask(String id) {
        return unpublishTask(id, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO unpublishTask(String id, String operatorUserId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, id);
        requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "任务已经下线");
        if (instanceRepository.existsActiveByTask(workspaceId, task.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS, "请先停止当前运行实例再下线任务");
        }
        disableScheduleForTask(workspaceId, task.getId());
        task.setDesiredState(DataSyncDesiredState.STOPPED);
        task.setStatus(DataSyncTaskStatus.UNPUBLISHED);
        task.initUpdate(operatorUserId);
        if (taskRepository.update(workspaceId, task) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "下线任务失败");
        }
        return toTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized DataSyncInstanceVO runTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, id);
        requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "任务尚未上线");
        DataSyncMappingPreviewDTO resolvedScope = validatePersistedTaskDefinition(task);
        if (instanceRepository.existsActiveByTask(workspaceId, task.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS);
        }
        if (task.getSyncType() == DataSyncType.REALTIME) {
            updateDesiredState(workspaceId, task, DataSyncDesiredState.RUNNING);
        }
        return createInstance(workspaceId, task, resolvedScope, DataSyncTriggerType.MANUAL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncScheduleVO saveSchedule(String taskId, DataSyncScheduleDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, taskId);
        requireOfflineTask(task);

        String cronExpression = StringUtils.trimToNull(dto.getCronExpression());
        String timeZone = normalizeTimeZone(dto.getTimeZone());
        if (cronExpression == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不能为空");
        }

        DataSyncScheduleEntity schedule =
                scheduleRepository.queryByTask(workspaceId, taskId).orElse(null);
        if (schedule == null) {
            schedule = new DataSyncScheduleEntity();
            schedule.setWorkspaceId(workspaceId);
            schedule.setTaskId(taskId);
            schedule.setEnabled(false);
            schedule.setCronExpression(cronExpression);
            schedule.setTimeZone(timeZone);
            schedule.initCreate();
            validateScheduleDefinition(toScheduleDefinition(schedule));
            if (scheduleRepository.add(schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        } else {
            schedule.setCronExpression(cronExpression);
            schedule.setTimeZone(timeZone);
            schedule.initUpdate();
            validateScheduleDefinition(toScheduleDefinition(schedule));
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
            if (Boolean.TRUE.equals(schedule.getEnabled())) {
                requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "启用中的调度要求任务保持上线");
                replaceScheduleAfterCommit(schedule);
            }
        }
        return toScheduleVO(schedule);
    }

    @Override
    public DataSyncSchedulePreviewVO previewSchedule(DataSyncScheduleDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE);

        String cronExpression = StringUtils.trimToNull(dto.getCronExpression());
        String timeZone = normalizeTimeZone(dto.getTimeZone());
        if (cronExpression == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不能为空");
        }

        ZoneId zoneId = ZoneId.of(timeZone);
        List<LocalDateTime> nextFireTimes;
        try {
            nextFireTimes = scheduleEngine.previewNextFireTimes(cronExpression, zoneId, 5).stream()
                    .map(instant -> LocalDateTime.ofInstant(instant, zoneId))
                    .toList();
        } catch (ScheduleEngineException | IllegalArgumentException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不合法", exception);
        }

        DataSyncSchedulePreviewVO result = new DataSyncSchedulePreviewVO();
        result.setCronExpression(cronExpression);
        result.setTimeZone(timeZone);
        result.setNextFireTimes(nextFireTimes);
        return result;
    }

    @Override
    public DataSyncScheduleVO querySchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireOfflineTask(requireTask(workspaceId, taskId));
        return toScheduleVO(requireSchedule(workspaceId, taskId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncScheduleVO enableSchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, taskId);
        requireOfflineTask(task);
        requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "任务上线后才能启用调度");

        DataSyncScheduleEntity schedule = requireSchedule(workspaceId, taskId);
        validateScheduleDefinition(toScheduleDefinition(schedule));
        if (!Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setEnabled(true);
            schedule.initUpdate();
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        }
        replaceScheduleAfterCommit(schedule);
        return toScheduleVO(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncScheduleVO disableSchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireOfflineTask(requireTask(workspaceId, taskId));
        DataSyncScheduleEntity schedule = requireSchedule(workspaceId, taskId);
        if (Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setEnabled(false);
            schedule.initUpdate();
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        }
        unscheduleAfterCommit(schedule.getId());
        return toScheduleVO(schedule);
    }

    @Override
    public void restoreScheduleRuntime() {
        for (DataSyncScheduleEntity schedule : scheduleRepository.queryEnabled()) {
            DataSyncTaskEntity task = taskRepository
                    .queryById(schedule.getWorkspaceId(), schedule.getTaskId())
                    .orElseThrow(() -> new DataSyncException(
                            DataSyncErrorCode.SCHEDULE_RUNTIME_FAILED, "启用中的调度关联任务不存在，scheduleId=" + schedule.getId()));
            requireOfflineTask(task);
            requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "启用中的调度关联任务必须保持上线");
            validateScheduleDefinition(toScheduleDefinition(schedule));
            replaceScheduleRuntime(schedule);
        }
    }

    @Override
    public void restoreRealtimeDesiredState() {
        for (DataSyncTaskEntity task : taskRepository.queryRealtimeDesiredRunning()) {
            String workspaceId = task.getWorkspaceId();
            WorkspaceContext.bind(workspaceId);
            try {
                if (taskDesiredState(task) != DataSyncDesiredState.RUNNING
                        || taskStatus(task) != DataSyncTaskStatus.PUBLISHED
                        || task.getSyncType() != DataSyncType.REALTIME) {
                    continue;
                }
                if (instanceRepository.existsActiveByTask(workspaceId, task.getId())) {
                    LOG.info("实时同步自动恢复跳过已有活动Execution，workspaceId={}, taskId={}", workspaceId, task.getId());
                    continue;
                }

                DataSyncMappingPreviewDTO resolvedScope = validatePersistedTaskDefinition(task);
                DataSyncInstanceVO recovered =
                        createInstance(workspaceId, task, resolvedScope, DataSyncTriggerType.AUTO_RECOVERY);
                LOG.info(
                        "实时同步自动恢复已创建新Execution，workspaceId={}, taskId={}, taskVersion={}, instanceId={}",
                        workspaceId,
                        task.getId(),
                        task.getDefinitionVersion(),
                        recovered.getId());
            } catch (Exception exception) {
                LOG.error(
                        "实时同步自动恢复失败，workspaceId={}, taskId={}, taskVersion={}, error={}",
                        workspaceId,
                        task.getId(),
                        task.getDefinitionVersion(),
                        SensitiveUtils.mask(exception.getMessage()));
            } finally {
                WorkspaceContext.clear();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void onFire(DataSyncScheduleFire fire) {
        if (fire == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE);

        WorkspaceContext.bind(fire.workspaceId());
        try {
            DataSyncScheduleEntity schedule = scheduleRepository
                    .queryById(fire.workspaceId(), fire.scheduleId())
                    .orElse(null);
            if (schedule == null
                    || !Boolean.TRUE.equals(schedule.getEnabled())
                    || !Objects.equals(schedule.getTaskId(), fire.taskId())) {
                LOG.info(
                        "离线调度触发已忽略，workspaceId={}, taskId={}, scheduleId={}",
                        fire.workspaceId(),
                        fire.taskId(),
                        fire.scheduleId());
                return;
            }

            DataSyncTaskEntity task =
                    taskRepository.queryById(fire.workspaceId(), fire.taskId()).orElse(null);
            if (task == null
                    || task.getSyncType() != DataSyncType.OFFLINE
                    || task.getStatus() != DataSyncTaskStatus.PUBLISHED) {
                LOG.info(
                        "离线调度触发因任务状态已忽略，workspaceId={}, taskId={}, scheduleId={}",
                        fire.workspaceId(),
                        fire.taskId(),
                        fire.scheduleId());
                return;
            }
            if (instanceRepository.existsActiveByTask(fire.workspaceId(), fire.taskId())) {
                LOG.info(
                        "离线调度触发因已有运行实例跳过，workspaceId={}, taskId={}, scheduleId={}",
                        fire.workspaceId(),
                        fire.taskId(),
                        fire.scheduleId());
                return;
            }

            DataSyncMappingPreviewDTO resolvedScope = validatePersistedTaskDefinition(task);
            DataSyncInstanceVO instance =
                    createInstance(fire.workspaceId(), task, resolvedScope, DataSyncTriggerType.SCHEDULE);
            LOG.info(
                    "离线调度已创建同步实例，workspaceId={}, taskId={}, scheduleId={}, instanceId={}",
                    fire.workspaceId(),
                    fire.taskId(),
                    fire.scheduleId(),
                    instance.getId());
        } finally {
            WorkspaceContext.clear();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity entity = requireTask(workspaceId, id);
        requireTaskStatus(entity, DataSyncTaskStatus.UNPUBLISHED, "已上线任务请先下线后再删除");
        if (instanceRepository.existsActiveByTask(workspaceId, entity.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS);
        }
        deleteScheduleForTask(workspaceId, entity.getId());
        if (taskRepository.deleteById(workspaceId, entity.getId()) <= 0) {
            throw new DataSyncException(DataSyncErrorCode.DELETE_TASK_FAILED);
        }
        return true;
    }

    @Override
    public DataSyncInstanceVO queryInstance(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return toInstanceVO(requireInstance(workspaceId, id), true);
    }

    @Override
    public List<DataSyncAttemptVO> queryAttempts(String instanceId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireInstance(workspaceId, instanceId);
        return attemptRepository.queryByExecution(workspaceId, instanceId).stream()
                .map(this::toAttemptVO)
                .toList();
    }

    @Override
    public List<DataSyncExecutionEventVO> queryExecutionEvents(String instanceId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireInstance(workspaceId, instanceId);
        return executionEventRepository.queryByExecution(workspaceId, instanceId).stream()
                .map(this::toExecutionEventVO)
                .toList();
    }

    @Override
    public PagingData<DataSyncInstanceVO> queryInstancePage(DataSyncInstanceQueryDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY);
        if (CollectionUtils.isNotEmpty(dto.getSorts())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "实例分页暂不支持自定义排序");
        }
        if (dto.getStartTimeStart() != null
                && dto.getStartTimeEnd() != null
                && dto.getStartTimeStart().isAfter(dto.getStartTimeEnd())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "开始时间范围不合法");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncInstancePageQuery query = new DataSyncInstancePageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getTaskId()),
                StringUtils.trimToNull(dto.getKeyword()),
                dto.getSyncType(),
                dto.getStatus(),
                dto.getTriggerType(),
                dto.getStartTimeStart(),
                dto.getStartTimeEnd());
        return PagingData.from(
                instanceRepository.queryPage(workspaceId, query).map(value -> toInstanceVO(value, false)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncInstanceVO cancelInstance(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncInstanceEntity instance = requireInstance(workspaceId, id);
        if (instance.getStatus() == null || instance.getStatus().isTerminal()) {
            return toInstanceVO(instance, true);
        }

        if (instance.getSyncType() == DataSyncType.REALTIME) {
            DataSyncTaskEntity task =
                    taskRepository.queryById(workspaceId, instance.getTaskId()).orElse(null);
            if (task != null) updateDesiredState(workspaceId, task, DataSyncDesiredState.STOPPED);
        }

        if (instance.getStatus() == DataSyncInstanceStatus.PENDING) {
            if (!instanceRepository.cancelExecution(workspaceId, id, DataSyncInstanceStatus.PENDING, DateUtils.now())) {
                throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
            }
            attemptLifecycle.cancelActiveAttempt(workspaceId, id);
            attemptLifecycle.recordExecutionCanceled(workspaceId, id);
        } else if (instance.getStatus() == DataSyncInstanceStatus.RETRY_WAITING) {
            if (!instanceRepository.cancelExecution(
                    workspaceId, id, DataSyncInstanceStatus.RETRY_WAITING, DateUtils.now())) {
                throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
            }
            attemptLifecycle.cancelActiveAttempt(workspaceId, id);
            attemptLifecycle.recordExecutionCanceled(workspaceId, id);
        } else if (instance.getStatus() == DataSyncInstanceStatus.RUNNING) {
            if (!executionRegistry.cancel(id)) {
                if (instanceRepository.transitionStatus(
                        workspaceId,
                        id,
                        DataSyncInstanceStatus.RUNNING,
                        DataSyncInstanceStatus.LOST,
                        null,
                        DateUtils.now(),
                        DataSyncErrorCode.EXECUTION_LOST.getCode(),
                        DataSyncErrorCode.EXECUTION_LOST.getMessage())) {
                    attemptLifecycle.recordExecutionLost(workspaceId, id, "无法定位进程内运行句柄，Execution 已标记为 LOST");
                }
            } else if (!instanceRepository.cancelExecution(
                    workspaceId, id, DataSyncInstanceStatus.RUNNING, DateUtils.now())) {
                throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
            } else {
                attemptLifecycle.cancelActiveAttempt(workspaceId, id);
                attemptLifecycle.recordExecutionCanceled(workspaceId, id);
            }
        } else {
            throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
        }

        return toInstanceVO(requireInstance(workspaceId, id), true);
    }

    private DataSyncInstanceVO createInstance(
            String workspaceId,
            DataSyncTaskEntity task,
            DataSyncMappingPreviewDTO resolvedScope,
            DataSyncTriggerType triggerType) {
        DataSyncDefinitionSnapshotVO snapshot = definitionSnapshot(task, resolvedScope);
        DataSyncInstanceEntity instance = new DataSyncInstanceEntity();
        instance.setWorkspaceId(workspaceId);
        instance.setTaskId(task.getId());
        instance.setTaskName(task.getName());
        instance.setTaskVersion(task.getDefinitionVersion());
        instance.setSyncType(task.getSyncType());
        instance.setTriggerType(triggerType);
        DataSyncRetryPolicyVO retryPolicy = snapshot.getRetryPolicy();
        instance.setMaxAttempts(retryPolicy.getMaxAttempts());
        instance.setBackoffSeconds(retryPolicy.getBackoffSeconds());
        instance.setCurrentAttempt(1);
        instance.setStatus(DataSyncInstanceStatus.PENDING);
        instance.setDefinitionSnapshot(JSONUtils.toJson(snapshot));
        instance.setReadRows(0L);
        instance.setWriteRows(0L);
        instance.initCreate();
        if (instanceRepository.add(instance) == null) {
            throw new DataSyncException(DataSyncErrorCode.EXECUTION_FAILED, "创建同步实例失败");
        }
        if (triggerType == DataSyncTriggerType.AUTO_RECOVERY) {
            attemptLifecycle.recordAutoRecoveryStarted(workspaceId, instance.getId());
        }
        submitAfterCommit(workspaceId, instance.getId(), snapshot);
        return toInstanceVO(instance, true);
    }

    private void requireOfflineTask(DataSyncTaskEntity task) {
        if (task == null || task.getSyncType() != DataSyncType.OFFLINE) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "只有离线同步任务支持 Cron 调度");
        }
    }

    private DataSyncScheduleEntity requireSchedule(String workspaceId, String taskId) {
        return scheduleRepository
                .queryByTask(workspaceId, taskId)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.SCHEDULE_NOT_FOUND));
    }

    private String normalizeTimeZone(String value) {
        String timeZone = StringUtils.trimToNull(value);
        if (timeZone == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "时区不能为空");
        try {
            return ZoneId.of(timeZone).getId();
        } catch (DateTimeException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "时区不合法", exception);
        }
    }

    private DataSyncScheduleDefinition toScheduleDefinition(DataSyncScheduleEntity schedule) {
        try {
            return new DataSyncScheduleDefinition(
                    schedule.getId(),
                    schedule.getWorkspaceId(),
                    schedule.getTaskId(),
                    schedule.getCronExpression(),
                    ZoneId.of(schedule.getTimeZone()));
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "调度定义不完整", exception);
        }
    }

    private void validateScheduleDefinition(DataSyncScheduleDefinition definition) {
        try {
            scheduleEngine.validate(definition);
        } catch (ScheduleEngineException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不合法", exception);
        }
    }

    private void replaceScheduleAfterCommit(DataSyncScheduleEntity schedule) {
        runAfterCommit(() -> replaceScheduleRuntime(schedule));
    }

    private void unscheduleAfterCommit(String scheduleId) {
        runAfterCommit(() -> {
            try {
                scheduleEngine.unschedule(scheduleId);
            } catch (ScheduleEngineException exception) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_RUNTIME_FAILED, "移除调度失败", exception);
            }
        });
    }

    private void replaceScheduleRuntime(DataSyncScheduleEntity schedule) {
        try {
            scheduleEngine.unschedule(schedule.getId());
            scheduleEngine.schedule(toScheduleDefinition(schedule));
        } catch (ScheduleEngineException exception) {
            throw new DataSyncException(DataSyncErrorCode.SCHEDULE_RUNTIME_FAILED, "注册调度失败", exception);
        }
    }

    private void disableScheduleForTask(String workspaceId, String taskId) {
        DataSyncScheduleEntity schedule =
                scheduleRepository.queryByTask(workspaceId, taskId).orElse(null);
        if (schedule == null) return;
        if (Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setEnabled(false);
            schedule.initUpdate();
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        }
        unscheduleAfterCommit(schedule.getId());
    }

    private void deleteScheduleForTask(String workspaceId, String taskId) {
        DataSyncScheduleEntity schedule =
                scheduleRepository.queryByTask(workspaceId, taskId).orElse(null);
        if (schedule == null) return;
        if (scheduleRepository.deleteByTask(workspaceId, taskId) <= 0) {
            throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED, "删除任务调度失败");
        }
        unscheduleAfterCommit(schedule.getId());
    }

    private DataSyncScheduleVO toScheduleVO(DataSyncScheduleEntity entity) {
        return BeanCopyUtils.copy(entity, DataSyncScheduleVO.class);
    }

    private DataSyncTaskOperationVO toTaskOperationVO(String workspaceId, DataSyncTaskEntity task) {
        DataSyncTaskOperationVO target = new DataSyncTaskOperationVO();
        target.setId(task.getId());
        target.setName(task.getName());
        target.setSyncType(
                task.getSyncType() == null ? null : task.getSyncType().name());
        target.setDesiredState(taskDesiredState(task).name());
        target.setDefinitionVersion(task.getDefinitionVersion());
        target.setRetryPolicy(toRetryPolicyVO(task.getRetryPolicy()));
        instanceRepository
                .queryLatestByTask(workspaceId, task.getId())
                .ifPresent(instance -> target.setLatestInstance(toInstanceVO(instance, false)));
        if (task.getSyncType() == DataSyncType.OFFLINE) {
            scheduleRepository
                    .queryByTask(workspaceId, task.getId())
                    .ifPresent(schedule -> target.setSchedule(toRuntimeScheduleVO(schedule)));
        }
        return target;
    }

    private DataSyncScheduleVO toRuntimeScheduleVO(DataSyncScheduleEntity entity) {
        DataSyncScheduleVO target = toScheduleVO(entity);
        if (!Boolean.TRUE.equals(entity.getEnabled())) return target;
        try {
            scheduleEngine
                    .queryNextFireTime(entity.getId())
                    .ifPresent(nextFireTime -> target.setNextFireTime(
                            LocalDateTime.ofInstant(nextFireTime, ZoneId.of(entity.getTimeZone()))));
        } catch (ScheduleEngineException exception) {
            LOG.warn("查询调度下一次触发时间失败，scheduleId={}, error={}", entity.getId(), exception.getMessage());
        }
        return target;
    }

    private DataSyncMappingPreviewDTO validatePersistedTaskDefinition(DataSyncTaskEntity task) {
        DataSyncMappingPreviewDTO resolvedScope =
                resolveMappingScope(BeanCopyUtils.copy(task, DataSyncMappingPreviewDTO.class));
        DataSyncWriteMode writeMode = taskWriteMode(task);
        validateWriteMode(task.getSyncType(), writeMode);
        if (task.getSyncType() == DataSyncType.REALTIME) {
            validateRealtimeTopology(task.getSourceDataSourceId(), task.getTargetDataSourceId(), resolvedScope);
        } else {
            validateOfflineUpsertTarget(
                    task.getSourceDataSourceId(), task.getTargetDataSourceId(), resolvedScope, writeMode);
        }
        requireCompatibleMapping(resolvedScope);
        return resolvedScope;
    }

    private boolean executableDefinitionChanged(
            DataSyncTaskEntity entity, DataSyncTaskDTO dto, DataSyncMappingPreviewDTO resolvedScope) {
        return !Objects.equals(
                        entity.getSourceDataSourceId(),
                        dto.getSourceDataSourceId().trim())
                || !Objects.equals(entity.getSourceDatabase(), resolvedScope.getSourceDatabase())
                || !Objects.equals(entity.getSourceSchema(), resolvedScope.getSourceSchema())
                || !Objects.equals(entity.getSourceTable(), dto.getSourceTable().trim())
                || !Objects.equals(
                        entity.getTargetDataSourceId(),
                        dto.getTargetDataSourceId().trim())
                || !Objects.equals(entity.getTargetDatabase(), resolvedScope.getTargetDatabase())
                || !Objects.equals(entity.getTargetSchema(), resolvedScope.getTargetSchema())
                || !Objects.equals(entity.getTargetTable(), dto.getTargetTable().trim())
                || taskWriteMode(entity) != requireWriteMode(dto.getWriteMode())
                || !jsonEquals(entity.getRuntimeConfig(), runtimeConfigJson(entity.getSyncType(), dto))
                || !jsonEquals(normalizedRetryPolicyJson(entity.getRetryPolicy()), retryPolicyJson(dto));
    }

    private boolean jsonEquals(String left, String right) {
        if (Objects.equals(left, right)) return true;
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) return false;
        return JSONUtils.readTree(left).equals(JSONUtils.readTree(right));
    }

    private void applyDefinition(
            DataSyncTaskEntity entity, DataSyncTaskDTO dto, DataSyncMappingPreviewDTO resolvedScope) {
        entity.setSourceDataSourceId(dto.getSourceDataSourceId().trim());
        entity.setSourceDatabase(resolvedScope.getSourceDatabase());
        entity.setSourceSchema(resolvedScope.getSourceSchema());
        entity.setSourceTable(dto.getSourceTable().trim());
        entity.setTargetDataSourceId(dto.getTargetDataSourceId().trim());
        entity.setTargetDatabase(resolvedScope.getTargetDatabase());
        entity.setTargetSchema(resolvedScope.getTargetSchema());
        entity.setTargetTable(dto.getTargetTable().trim());
        entity.setWriteMode(requireWriteMode(dto.getWriteMode()));
        entity.setRuntimeConfig(runtimeConfigJson(entity.getSyncType(), dto));
        entity.setRetryPolicy(retryPolicyJson(dto));
        entity.setRemark(StringUtils.trimToNull(dto.getRemark()));
    }

    private void validateTaskDefinition(
            DataSyncType syncType, DataSyncTaskDTO dto, DataSyncMappingPreviewDTO resolvedScope) {
        validateWriteMode(syncType, dto.getWriteMode());
        if (dto.getRetryPolicy() == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "Retry Policy 不能为空");
        }
        if (syncType == DataSyncType.OFFLINE) {
            if (dto.getRuntimeConfig() == null) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "OFFLINE 运行参数不能为空");
            }
            validateOfflineUpsertTarget(
                    dto.getSourceDataSourceId(), dto.getTargetDataSourceId(), resolvedScope, dto.getWriteMode());
            return;
        }
        if (dto.getRealtimeConfig() == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "REALTIME 运行参数不能为空");
        }

        validateRealtimeTopology(dto.getSourceDataSourceId(), dto.getTargetDataSourceId(), resolvedScope);
    }

    private void validateOfflineUpsertTarget(
            String sourceDataSourceId,
            String targetDataSourceId,
            DataSyncMappingPreviewDTO resolvedScope,
            DataSyncWriteMode writeMode) {
        if (writeMode != DataSyncWriteMode.UPSERT) return;

        List<DataSourceCatalogColumnVO> sourceColumns = dataSourceService.queryCatalogColumns(
                sourceDataSourceId,
                tablePath(
                        resolvedScope.getSourceDatabase(),
                        resolvedScope.getSourceSchema(),
                        resolvedScope.getSourceTable()));
        List<DataSourceCatalogColumnVO> targetColumns = dataSourceService.queryCatalogColumns(
                targetDataSourceId,
                tablePath(
                        resolvedScope.getTargetDatabase(),
                        resolvedScope.getTargetSchema(),
                        resolvedScope.getTargetTable()));
        List<DataSourceCatalogColumnVO> targetPrimaryKeys = targetColumns.stream()
                .filter(column -> Boolean.TRUE.equals(column.getPrimaryKey()))
                .toList();
        if (targetPrimaryKeys.isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "UPSERT 写入要求目标表存在主键");
        }

        Map<String, DataSourceCatalogColumnVO> sourceByName = DataSyncCatalogColumns.indexByName(sourceColumns);
        if (targetPrimaryKeys.stream()
                .anyMatch(
                        primaryKey -> DataSyncCatalogColumns.findByName(sourceByName, primaryKey.getName()) == null)) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "UPSERT 写入要求来源包含目标表全部主键字段");
        }
    }

    private void validateRealtimeTopology(
            String sourceDataSourceId, String targetDataSourceId, DataSyncMappingPreviewDTO resolvedScope) {
        DataSourceVO source = dataSourceService.queryDataSource(sourceDataSourceId);
        DataSourceVO target = dataSourceService.queryDataSource(targetDataSourceId);
        if (!"MYSQL".equals(source.getDbType())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步来源数据源仅支持 MYSQL");
        }
        if (!REALTIME_TARGET_TYPES.contains(target.getDbType())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步目标数据源仅支持 MYSQL / POSTGRE_SQL / ORACLE");
        }

        List<DataSourceCatalogColumnVO> sourceColumns = dataSourceService.queryCatalogColumns(
                sourceDataSourceId,
                tablePath(
                        resolvedScope.getSourceDatabase(),
                        resolvedScope.getSourceSchema(),
                        resolvedScope.getSourceTable()));
        Set<String> sourcePrimaryKeys = DataSyncCatalogColumns.primaryKeyNames(sourceColumns);
        if (sourcePrimaryKeys.isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步来源表必须包含主键");
        }

        List<DataSourceCatalogColumnVO> targetColumns = dataSourceService.queryCatalogColumns(
                targetDataSourceId,
                tablePath(
                        resolvedScope.getTargetDatabase(),
                        resolvedScope.getTargetSchema(),
                        resolvedScope.getTargetTable()));
        Set<String> targetPrimaryKeys = DataSyncCatalogColumns.primaryKeyNames(targetColumns);
        if (!sourcePrimaryKeys.equals(targetPrimaryKeys)) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步目标表主键必须与来源表主键一致");
        }
    }

    private String runtimeConfigJson(DataSyncType syncType, DataSyncTaskDTO dto) {
        return syncType == DataSyncType.REALTIME
                ? JSONUtils.toJson(dto.getRealtimeConfig())
                : JSONUtils.toJson(dto.getRuntimeConfig());
    }

    private String retryPolicyJson(DataSyncTaskDTO dto) {
        DataSyncRetryPolicyDTO policy =
                dto.getRetryPolicy() == null ? new DataSyncRetryPolicyDTO() : dto.getRetryPolicy();
        return JSONUtils.toJson(policy);
    }

    private String normalizedRetryPolicyJson(String json) {
        return StringUtils.isBlank(json) ? JSONUtils.toJson(new DataSyncRetryPolicyDTO()) : json;
    }

    private void requireCompatibleMapping(DataSyncMappingPreviewDTO dto) {
        DataSyncMappingPreviewVO preview = previewResolvedMapping(dto);
        if (!preview.isCompatible()) {
            throw new DataSyncException(DataSyncErrorCode.FIELD_MAPPING_INCOMPATIBLE);
        }
    }

    private DataSyncDefinitionSnapshotVO definitionSnapshot(
            DataSyncTaskEntity task, DataSyncMappingPreviewDTO resolvedScope) {
        DataSourceVO source = dataSourceService.queryDataSource(task.getSourceDataSourceId());
        DataSourceVO target = dataSourceService.queryDataSource(task.getTargetDataSourceId());

        DataSyncDefinitionSnapshotVO snapshot = new DataSyncDefinitionSnapshotVO();
        snapshot.setTaskId(task.getId());
        snapshot.setTaskName(task.getName());
        snapshot.setTaskVersion(task.getDefinitionVersion());
        snapshot.setSyncType(task.getSyncType().name());
        snapshot.setWriteMode(taskWriteMode(task).name());
        snapshot.setRetryPolicy(toRetryPolicyVO(task.getRetryPolicy()));
        snapshot.setSource(endpointSnapshot(
                source, resolvedScope.getSourceDatabase(), resolvedScope.getSourceSchema(), task.getSourceTable()));
        snapshot.setTarget(endpointSnapshot(
                target, resolvedScope.getTargetDatabase(), resolvedScope.getTargetSchema(), task.getTargetTable()));
        if (task.getSyncType() == DataSyncType.REALTIME) {
            snapshot.setRealtimeConfig(toRealtimeConfigVO(task.getRuntimeConfig()));
        } else {
            snapshot.setRuntimeConfig(toRuntimeConfigVO(task.getRuntimeConfig()));
        }
        return snapshot;
    }

    private DataSyncEndpointSnapshotVO endpointSnapshot(
            DataSourceVO dataSource, String database, String schema, String table) {
        DataSyncEndpointSnapshotVO endpoint = new DataSyncEndpointSnapshotVO();
        endpoint.setDataSourceId(dataSource.getId());
        endpoint.setDataSourceName(dataSource.getName());
        endpoint.setDataSourceType(dataSource.getDbType());
        endpoint.setDatabase(database);
        endpoint.setSchema(schema);
        endpoint.setTable(table);
        return endpoint;
    }

    private void submitAfterCommit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        Runnable submit = DataSyncType.REALTIME.name().equals(snapshot.getSyncType())
                ? () -> realtimeSyncExecutor.submit(workspaceId, instanceId, snapshot)
                : () -> offlineSyncExecutor.submit(workspaceId, instanceId, snapshot);
        runAfterCommit(submit);
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private DataSourceTablePathDTO tablePath(String database, String schema, String table) {
        String tableName = StringUtils.trimToNull(table);
        if (tableName == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "表名称不能为空");

        DataSourceTablePathDTO path = new DataSourceTablePathDTO();
        path.setDatabase(StringUtils.trimToNull(database));
        path.setSchema(StringUtils.trimToNull(schema));
        path.setTable(tableName);
        return path;
    }

    private DataSyncFieldMappingVO toFieldMapping(DataSourceCatalogColumnVO source, DataSourceCatalogColumnVO target) {
        DataSyncFieldMappingVO mapping = new DataSyncFieldMappingVO();
        mapping.setSourceName(source.getName());
        mapping.setSourceType(source.getTypeName());
        mapping.setTargetName(target == null ? null : target.getName());
        mapping.setTargetType(target == null ? null : target.getTypeName());
        mapping.setCompatible(target != null && compatibleType(source, target));
        if (target == null) {
            mapping.setMessage("目标表缺少同名字段");
        } else if (!mapping.isCompatible()) {
            mapping.setMessage("字段类型或容量不兼容");
        }
        return mapping;
    }

    private boolean compatibleType(DataSourceCatalogColumnVO source, DataSourceCatalogColumnVO target) {
        DataSourceColumn sourceColumn = DataSyncCatalogColumns.toColumn(source);
        DataSourceColumn targetColumn = DataSyncCatalogColumns.toColumn(target);
        if (sourceColumn == null || targetColumn == null) return false;
        try {
            YakColumn sourceYakColumn = JdbcSchemaMapper.toYakColumn(sourceColumn);
            YakColumn targetYakColumn = JdbcSchemaMapper.toYakColumn(targetColumn);
            return JdbcSchemaCompatibility.isCompatible(sourceYakColumn, targetYakColumn);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private DataSyncMappingPreviewDTO resolveMappingScope(DataSyncMappingPreviewDTO dto) {
        DataSourceVO source = dataSourceService.queryDataSource(dto.getSourceDataSourceId());
        DataSourceVO target = dataSourceService.queryDataSource(dto.getTargetDataSourceId());

        DataSyncMappingPreviewDTO resolved = BeanCopyUtils.copy(dto, DataSyncMappingPreviewDTO.class);
        resolved.setSourceDatabase(scopeValue(source.getDatabase(), dto.getSourceDatabase()));
        resolved.setSourceSchema(scopeValue(source.getSchema(), dto.getSourceSchema()));
        resolved.setTargetDatabase(scopeValue(target.getDatabase(), dto.getTargetDatabase()));
        resolved.setTargetSchema(scopeValue(target.getSchema(), dto.getTargetSchema()));
        return resolved;
    }

    private String scopeValue(String boundValue, String requestedValue) {
        String bound = StringUtils.trimToNull(boundValue);
        return bound != null ? bound : StringUtils.trimToNull(requestedValue);
    }

    private DataSyncType requireSyncType(DataSyncType syncType) {
        if (syncType == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "同步类型不能为空");
        }
        return syncType;
    }

    private DataSyncWriteMode requireWriteMode(DataSyncWriteMode writeMode) {
        if (writeMode == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "写入方式不能为空");
        }
        return writeMode;
    }

    private void validateWriteMode(DataSyncType syncType, DataSyncWriteMode writeMode) {
        DataSyncWriteMode resolved = requireWriteMode(writeMode);
        if (syncType == DataSyncType.REALTIME && resolved != DataSyncWriteMode.APPEND) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "REALTIME 当前固定使用 APPEND 写入方式");
        }
    }

    private DataSyncWriteMode taskWriteMode(DataSyncTaskEntity task) {
        return task.getWriteMode() == null ? DataSyncWriteMode.APPEND : task.getWriteMode();
    }

    private DataSyncTaskStatus taskStatus(DataSyncTaskEntity task) {
        return task.getStatus() == null ? DataSyncTaskStatus.PUBLISHED : task.getStatus();
    }

    private DataSyncDesiredState taskDesiredState(DataSyncTaskEntity task) {
        return task.getDesiredState() == null ? DataSyncDesiredState.STOPPED : task.getDesiredState();
    }

    private void updateDesiredState(String workspaceId, DataSyncTaskEntity task, DataSyncDesiredState desiredState) {
        if (taskDesiredState(task) == desiredState) return;
        task.setDesiredState(desiredState);
        task.initUpdate();
        if (taskRepository.update(workspaceId, task) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "更新实时同步期望状态失败");
        }
    }

    private void requireTaskStatus(DataSyncTaskEntity task, DataSyncTaskStatus expected, String detail) {
        if (taskStatus(task) != expected) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK_STATUS, detail);
        }
    }

    private DataSyncTaskEntity requireTask(String workspaceId, String id) {
        if (StringUtils.isBlank(id)) throw new DataSyncException(DataSyncErrorCode.TASK_NOT_FOUND);
        return taskRepository
                .queryById(workspaceId, id)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.TASK_NOT_FOUND));
    }

    private DataSyncInstanceEntity requireInstance(String workspaceId, String id) {
        if (StringUtils.isBlank(id)) throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_FOUND);
        return instanceRepository
                .queryById(workspaceId, id)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_FOUND));
    }

    private void ensureTaskNameAvailable(String workspaceId, String name, String excludeId) {
        if (taskRepository.existsByName(workspaceId, name, excludeId)) {
            throw new DataSyncException(DataSyncErrorCode.DUPLICATE_TASK_NAME);
        }
    }

    private DataSyncTaskVO toTaskListVO(DataSyncTaskEntity source, DataSyncScheduleEntity schedule) {
        DataSyncTaskVO target = toTaskVO(source);
        if (schedule != null) {
            target.setScheduleCronExpression(schedule.getCronExpression());
            target.setScheduleTimeZone(schedule.getTimeZone());
            target.setScheduleEnabled(Boolean.TRUE.equals(schedule.getEnabled()));
        }
        return target;
    }

    private LocalDateTime operationsRangeStart(DataSyncOperationsRange range, LocalDateTime now) {
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        return range == DataSyncOperationsRange.TODAY ? today : today.minusDays(range.getDays() - 1L);
    }

    private DataSyncOperationsSummaryVO toOperationsSummaryVO(DataSyncOperationsSummaryStats source) {
        DataSyncOperationsSummaryVO target = BeanCopyUtils.copy(source, DataSyncOperationsSummaryVO.class);
        target.setExecutionCount(zero(target.getExecutionCount()));
        target.setSucceededCount(zero(target.getSucceededCount()));
        target.setFailedCount(zero(target.getFailedCount()));
        target.setLostCount(zero(target.getLostCount()));
        target.setAbnormalTaskCount(zero(target.getAbnormalTaskCount()));
        target.setCurrentActiveTaskCount(zero(target.getCurrentActiveTaskCount()));
        target.setAutoRecoveryCount(zero(target.getAutoRecoveryCount()));
        target.setReadRows(zero(target.getReadRows()));
        target.setWriteRows(zero(target.getWriteRows()));
        target.setAverageDurationMillis(zero(target.getAverageDurationMillis()));
        return target;
    }

    private List<DataSyncOperationsTrendPointVO> toOperationsTrendVO(
            List<DataSyncOperationsTrendStats> source,
            DataSyncOperationsRange range,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {
        Map<LocalDateTime, DataSyncOperationsTrendStats> byBucket = new HashMap<>();
        for (DataSyncOperationsTrendStats item : source) {
            if (item.getBucketStart() != null) byBucket.put(item.getBucketStart(), item);
        }

        LocalDateTime bucket = range.isHourly()
                ? rangeStart.truncatedTo(ChronoUnit.HOURS)
                : rangeStart.toLocalDate().atStartOfDay();
        LocalDateTime endBucket = range.isHourly()
                ? rangeEnd.truncatedTo(ChronoUnit.HOURS)
                : rangeEnd.toLocalDate().atStartOfDay();
        List<DataSyncOperationsTrendPointVO> result = new ArrayList<>();
        while (!bucket.isAfter(endBucket)) {
            DataSyncOperationsTrendStats stats = byBucket.get(bucket);
            DataSyncOperationsTrendPointVO point = stats == null
                    ? new DataSyncOperationsTrendPointVO()
                    : BeanCopyUtils.copy(stats, DataSyncOperationsTrendPointVO.class);
            point.setBucketStart(bucket);
            point.setExecutionCount(zero(point.getExecutionCount()));
            point.setSucceededCount(zero(point.getSucceededCount()));
            point.setFailedCount(zero(point.getFailedCount()));
            point.setLostCount(zero(point.getLostCount()));
            point.setAutoRecoveryCount(zero(point.getAutoRecoveryCount()));
            point.setReadRows(zero(point.getReadRows()));
            point.setWriteRows(zero(point.getWriteRows()));
            point.setAverageDurationMillis(zero(point.getAverageDurationMillis()));
            result.add(point);
            bucket = range.isHourly() ? bucket.plusHours(1) : bucket.plusDays(1);
        }
        return result;
    }

    private List<DataSyncOperationsStatusMetricVO> toOperationsStatusDistribution(
            List<DataSyncOperationsStatusStats> source) {
        Map<Integer, Long> countByStatus = new HashMap<>();
        for (DataSyncOperationsStatusStats item : source) {
            if (item.getStatus() != null) countByStatus.put(item.getStatus(), zero(item.getCount()));
        }

        List<DataSyncOperationsStatusMetricVO> result = new ArrayList<>();
        for (DataSyncInstanceStatus status : DataSyncInstanceStatus.values()) {
            DataSyncOperationsStatusMetricVO item = new DataSyncOperationsStatusMetricVO();
            item.setStatus(status.name());
            item.setCount(countByStatus.getOrDefault(status.getValue(), 0L));
            result.add(item);
        }
        return result;
    }

    private DataSyncOperationsFailureRankVO toOperationsFailureRankVO(DataSyncOperationsFailureStats source) {
        DataSyncOperationsFailureRankVO target = BeanCopyUtils.copy(source, DataSyncOperationsFailureRankVO.class);
        target.setFailedCount(zero(target.getFailedCount()));
        target.setLostCount(zero(target.getLostCount()));
        target.setAbnormalCount(zero(target.getAbnormalCount()));
        return target;
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

    private DataSyncTaskVO toTaskVO(DataSyncTaskEntity source) {
        DataSyncTaskVO target = BeanCopyUtils.copy(
                source,
                DataSyncTaskVO.class,
                "syncType",
                "status",
                "desiredState",
                "writeMode",
                "runtimeConfig",
                "retryPolicy");
        target.setSyncType(
                source.getSyncType() == null ? null : source.getSyncType().name());
        target.setStatus(taskStatus(source).name());
        target.setDesiredState(taskDesiredState(source).name());
        target.setWriteMode(taskWriteMode(source).name());
        target.setRetryPolicy(toRetryPolicyVO(source.getRetryPolicy()));
        if (source.getSyncType() == DataSyncType.REALTIME) {
            target.setRealtimeConfig(toRealtimeConfigVO(source.getRuntimeConfig()));
        } else {
            target.setRuntimeConfig(toRuntimeConfigVO(source.getRuntimeConfig()));
        }
        return target;
    }

    private DataSyncRuntimeConfigVO toRuntimeConfigVO(String json) {
        DataSyncRuntimeConfigDTO source = JSONUtils.parseObject(json, DataSyncRuntimeConfigDTO.class);
        return BeanCopyUtils.copy(source, DataSyncRuntimeConfigVO.class);
    }

    private DataSyncRealtimeConfigVO toRealtimeConfigVO(String json) {
        DataSyncRealtimeConfigDTO source = JSONUtils.parseObject(json, DataSyncRealtimeConfigDTO.class);
        return BeanCopyUtils.copy(source, DataSyncRealtimeConfigVO.class);
    }

    private DataSyncRetryPolicyVO toRetryPolicyVO(String json) {
        DataSyncRetryPolicyDTO source = StringUtils.isBlank(json)
                ? new DataSyncRetryPolicyDTO()
                : JSONUtils.parseObject(json, DataSyncRetryPolicyDTO.class);
        return BeanCopyUtils.copy(source, DataSyncRetryPolicyVO.class);
    }

    private DataSyncAttemptVO toAttemptVO(DataSyncAttemptEntity source) {
        DataSyncAttemptVO target = BeanCopyUtils.copy(source, DataSyncAttemptVO.class, "status");
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        return target;
    }

    private DataSyncExecutionEventVO toExecutionEventVO(DataSyncExecutionEventEntity source) {
        DataSyncExecutionEventVO target =
                BeanCopyUtils.copy(source, DataSyncExecutionEventVO.class, "level", "eventType");
        target.setLevel(source.getLevel() == null ? null : source.getLevel().name());
        target.setEventType(
                source.getEventType() == null ? null : source.getEventType().name());
        return target;
    }

    private DataSyncInstanceVO toInstanceVO(DataSyncInstanceEntity source, boolean includeSnapshot) {
        DataSyncInstanceVO target = BeanCopyUtils.copy(
                source, DataSyncInstanceVO.class, "syncType", "triggerType", "status", "definitionSnapshot");
        target.setSyncType(
                source.getSyncType() == null ? null : source.getSyncType().name());
        target.setTriggerType(
                source.getTriggerType() == null ? null : source.getTriggerType().name());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        if (includeSnapshot && StringUtils.isNotBlank(source.getDefinitionSnapshot())) {
            target.setDefinitionSnapshot(
                    JSONUtils.parseObject(source.getDefinitionSnapshot(), DataSyncDefinitionSnapshotVO.class));
        }
        return target;
    }
}
