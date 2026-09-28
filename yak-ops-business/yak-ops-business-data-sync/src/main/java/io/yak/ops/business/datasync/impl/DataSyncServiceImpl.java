package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.DataSyncService;
import io.yak.ops.business.datasync.catalog.DataSyncCatalogColumns;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.execution.executor.OfflineSyncExecutor;
import io.yak.ops.business.datasync.execution.executor.RealtimeSyncExecutor;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionRegistry;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingPreviewDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRealtimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRuntimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncFieldMappingVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingPreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRealtimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstancePageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskPageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaCompatibility;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaMapper;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class DataSyncServiceImpl implements DataSyncService {

    private static final Set<String> REALTIME_TARGET_TYPES = Set.of("MYSQL", "POSTGRE_SQL", "ORACLE");

    @Resource
    private DataSyncTaskRepository taskRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private OfflineSyncExecutor offlineSyncExecutor;

    @Resource
    private RealtimeSyncExecutor realtimeSyncExecutor;

    @Resource
    private DataSyncExecutionRegistry executionRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO createTask(DataSyncTaskDTO dto) {
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
        applyDefinition(entity, dto, resolvedScope);
        entity.setDefinitionVersion(1);
        entity.initCreate();

        if (taskRepository.add(entity) == null) {
            throw new DataSyncException(DataSyncErrorCode.CREATE_TASK_FAILED);
        }
        return toTaskVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity entity = requireTask(workspaceId, id);
        String name = StringUtils.trimToNull(dto.getName());
        if (name == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "任务名称不能为空");
        ensureTaskNameAvailable(workspaceId, name, id);
        DataSyncType syncType = requireSyncType(dto.getSyncType());
        DataSyncMappingPreviewDTO resolvedScope =
                resolveMappingScope(BeanCopyUtils.copy(dto, DataSyncMappingPreviewDTO.class));
        validateTaskDefinition(syncType, dto, resolvedScope);
        requireCompatibleMapping(resolvedScope);

        entity.setName(name);
        entity.setSyncType(syncType);
        applyDefinition(entity, dto, resolvedScope);
        entity.setDefinitionVersion(Math.max(1, entity.getDefinitionVersion()) + 1);
        entity.initUpdate();

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
                StringUtils.trimToNull(dto.getSourceDataSourceId()),
                StringUtils.trimToNull(dto.getTargetDataSourceId()));
        return PagingData.from(taskRepository.queryPage(workspaceId, query).map(this::toTaskVO));
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
    public synchronized DataSyncInstanceVO runTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, id);
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
        if (instanceRepository.existsActiveByTask(workspaceId, task.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS);
        }

        DataSyncDefinitionSnapshotVO snapshot = definitionSnapshot(task, resolvedScope);
        DataSyncInstanceEntity instance = new DataSyncInstanceEntity();
        instance.setWorkspaceId(workspaceId);
        instance.setTaskId(task.getId());
        instance.setTaskName(task.getName());
        instance.setTaskVersion(task.getDefinitionVersion());
        instance.setSyncType(task.getSyncType());
        instance.setTriggerType(DataSyncTriggerType.MANUAL);
        instance.setStatus(DataSyncInstanceStatus.PENDING);
        instance.setDefinitionSnapshot(JSONUtils.toJson(snapshot));
        instance.setReadRows(0L);
        instance.setWriteRows(0L);
        instance.initCreate();
        if (instanceRepository.add(instance) == null) {
            throw new DataSyncException(DataSyncErrorCode.EXECUTION_FAILED, "创建同步实例失败");
        }

        submitAfterCommit(workspaceId, instance.getId(), snapshot);
        return toInstanceVO(instance, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity entity = requireTask(workspaceId, id);
        if (instanceRepository.existsActiveByTask(workspaceId, entity.getId())) {
            throw new DataSyncException(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS);
        }
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

        if (instance.getStatus() == DataSyncInstanceStatus.PENDING) {
            if (!instanceRepository.transitionStatus(
                    workspaceId,
                    id,
                    DataSyncInstanceStatus.PENDING,
                    DataSyncInstanceStatus.CANCELED,
                    null,
                    DateUtils.now(),
                    null,
                    null)) {
                throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
            }
        } else if (instance.getStatus() == DataSyncInstanceStatus.RUNNING) {
            if (!executionRegistry.cancel(id)) {
                instanceRepository.transitionStatus(
                        workspaceId,
                        id,
                        DataSyncInstanceStatus.RUNNING,
                        DataSyncInstanceStatus.LOST,
                        null,
                        DateUtils.now(),
                        DataSyncErrorCode.EXECUTION_LOST.getCode(),
                        DataSyncErrorCode.EXECUTION_LOST.getMessage());
            } else {
                instanceRepository.transitionStatus(
                        workspaceId,
                        id,
                        DataSyncInstanceStatus.RUNNING,
                        DataSyncInstanceStatus.CANCELED,
                        null,
                        DateUtils.now(),
                        null,
                        null);
            }
        } else {
            throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_CANCELABLE);
        }

        return toInstanceVO(requireInstance(workspaceId, id), true);
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
        entity.setRemark(StringUtils.trimToNull(dto.getRemark()));
    }

    private void validateTaskDefinition(
            DataSyncType syncType, DataSyncTaskDTO dto, DataSyncMappingPreviewDTO resolvedScope) {
        validateWriteMode(syncType, dto.getWriteMode());
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
        if (sourceColumns.stream().noneMatch(column -> Boolean.TRUE.equals(column.getPrimaryKey()))) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步来源表必须包含主键");
        }
    }

    private String runtimeConfigJson(DataSyncType syncType, DataSyncTaskDTO dto) {
        return syncType == DataSyncType.REALTIME
                ? JSONUtils.toJson(dto.getRealtimeConfig())
                : JSONUtils.toJson(dto.getRuntimeConfig());
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
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit.run();
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

    private DataSyncTaskVO toTaskVO(DataSyncTaskEntity source) {
        DataSyncTaskVO target =
                BeanCopyUtils.copy(source, DataSyncTaskVO.class, "syncType", "writeMode", "runtimeConfig");
        target.setSyncType(
                source.getSyncType() == null ? null : source.getSyncType().name());
        target.setWriteMode(taskWriteMode(source).name());
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
