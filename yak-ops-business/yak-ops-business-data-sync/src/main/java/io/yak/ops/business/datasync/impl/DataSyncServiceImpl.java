package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.DataSyncService;
import io.yak.ops.business.datasync.catalog.DataSyncCatalogColumns;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.execution.OfflineSyncExecutionRegistry;
import io.yak.ops.business.datasync.execution.OfflineSyncExecutor;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingPreviewDTO;
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
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
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
import jakarta.annotation.Resource;
import java.sql.Types;
import java.util.List;
import java.util.Map;
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

    @Resource
    private DataSyncTaskRepository taskRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private OfflineSyncExecutor offlineSyncExecutor;

    @Resource
    private OfflineSyncExecutionRegistry executionRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO createTask(DataSyncTaskDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        String name = StringUtils.trimToNull(dto.getName());
        if (name == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "任务名称不能为空");
        ensureTaskNameAvailable(workspaceId, name, null);
        DataSyncMappingPreviewDTO resolvedScope =
                resolveMappingScope(BeanCopyUtils.copy(dto, DataSyncMappingPreviewDTO.class));
        requireCompatibleMapping(resolvedScope);

        DataSyncTaskEntity entity = new DataSyncTaskEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setName(name);
        entity.setSyncType(requireOffline(dto.getSyncType()));
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
        DataSyncMappingPreviewDTO resolvedScope =
                resolveMappingScope(BeanCopyUtils.copy(dto, DataSyncMappingPreviewDTO.class));
        requireCompatibleMapping(resolvedScope);

        entity.setName(name);
        entity.setSyncType(requireOffline(dto.getSyncType()));
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
        entity.setRuntimeConfig(JSONUtils.toJson(dto.getRuntimeConfig()));
        entity.setRemark(StringUtils.trimToNull(dto.getRemark()));
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
        snapshot.setSource(endpointSnapshot(
                source, resolvedScope.getSourceDatabase(), resolvedScope.getSourceSchema(), task.getSourceTable()));
        snapshot.setTarget(endpointSnapshot(
                target, resolvedScope.getTargetDatabase(), resolvedScope.getTargetSchema(), task.getTargetTable()));
        snapshot.setRuntimeConfig(toRuntimeConfigVO(task.getRuntimeConfig()));
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
        Runnable submit = () -> offlineSyncExecutor.submit(workspaceId, instanceId, snapshot);
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
        if (source.getJdbcType() == null || target.getJdbcType() == null) return false;
        int sourceType = source.getJdbcType();
        int targetType = target.getJdbcType();

        boolean familyCompatible = sourceType == targetType
                || (isBoolean(sourceType) && isBoolean(targetType))
                || (isInteger(sourceType) && (isInteger(targetType) || isDecimal(targetType)))
                || (isDecimal(sourceType) && isDecimal(targetType))
                || (isString(sourceType) && isString(targetType))
                || (isBinary(sourceType) && isBinary(targetType))
                || (isDate(sourceType) && isDate(targetType))
                || (isTime(sourceType) && isTime(targetType))
                || (isTimestamp(sourceType) && isTimestamp(targetType));
        if (!familyCompatible) return false;

        if ((isString(sourceType) || isBinary(sourceType))
                && positive(source.getSize())
                && positive(target.getSize())
                && source.getSize() > target.getSize()) {
            return false;
        }
        if (isDecimal(sourceType) && isDecimal(targetType)) {
            if (positive(source.getSize()) && positive(target.getSize()) && source.getSize() > target.getSize()) {
                return false;
            }
            if (source.getScale() != null && target.getScale() != null && source.getScale() > target.getScale()) {
                return false;
            }
        }
        return true;
    }

    private boolean isBoolean(int type) {
        return type == Types.BOOLEAN || type == Types.BIT;
    }

    private boolean isInteger(int type) {
        return type == Types.TINYINT || type == Types.SMALLINT || type == Types.INTEGER || type == Types.BIGINT;
    }

    private boolean isDecimal(int type) {
        return type == Types.REAL
                || type == Types.FLOAT
                || type == Types.DOUBLE
                || type == Types.NUMERIC
                || type == Types.DECIMAL;
    }

    private boolean isString(int type) {
        return type == Types.CHAR
                || type == Types.VARCHAR
                || type == Types.LONGVARCHAR
                || type == Types.NCHAR
                || type == Types.NVARCHAR
                || type == Types.LONGNVARCHAR
                || type == Types.CLOB
                || type == Types.NCLOB;
    }

    private boolean isBinary(int type) {
        return type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY || type == Types.BLOB;
    }

    private boolean isDate(int type) {
        return type == Types.DATE;
    }

    private boolean isTime(int type) {
        return type == Types.TIME || type == Types.TIME_WITH_TIMEZONE;
    }

    private boolean isTimestamp(int type) {
        return type == Types.TIMESTAMP || type == Types.TIMESTAMP_WITH_TIMEZONE;
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
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

    private DataSyncType requireOffline(DataSyncType syncType) {
        if (syncType != DataSyncType.OFFLINE) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "当前阶段只支持 OFFLINE");
        }
        return syncType;
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
        DataSyncTaskVO target = BeanCopyUtils.copy(source, DataSyncTaskVO.class, "syncType", "runtimeConfig");
        target.setSyncType(
                source.getSyncType() == null ? null : source.getSyncType().name());
        target.setRuntimeConfig(toRuntimeConfigVO(source.getRuntimeConfig()));
        return target;
    }

    private DataSyncRuntimeConfigVO toRuntimeConfigVO(String json) {
        DataSyncRuntimeConfigDTO source = JSONUtils.parseObject(json, DataSyncRuntimeConfigDTO.class);
        return BeanCopyUtils.copy(source, DataSyncRuntimeConfigVO.class);
    }

    private DataSyncInstanceVO toInstanceVO(DataSyncInstanceEntity source, boolean includeSnapshot) {
        DataSyncInstanceVO target =
                BeanCopyUtils.copy(source, DataSyncInstanceVO.class, "triggerType", "status", "definitionSnapshot");
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
