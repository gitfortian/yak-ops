package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.DataSyncService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRuntimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstancePageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskPageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncTaskVO createTask(DataSyncTaskDTO dto) {
        requireTaskDto(dto);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        String name = normalizeRequired(dto.getName(), "任务名称不能为空");
        ensureTaskNameAvailable(workspaceId, name, null);
        validateDatasourceReferences(dto);

        DataSyncTaskEntity entity = new DataSyncTaskEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setName(name);
        entity.setSyncType(requireOffline(dto.getSyncType()));
        applyDefinition(entity, dto);
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
        requireTaskDto(dto);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity entity = requireTask(workspaceId, id);
        String name = normalizeRequired(dto.getName(), "任务名称不能为空");
        ensureTaskNameAvailable(workspaceId, name, id);
        validateDatasourceReferences(dto);

        entity.setName(name);
        entity.setSyncType(requireOffline(dto.getSyncType()));
        applyDefinition(entity, dto);
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
        if (dto.getSorts() != null && !dto.getSorts().isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "任务分页暂不支持自定义排序");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskPageQuery query = new DataSyncTaskPageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                normalizeNullable(dto.getKeyword()),
                dto.getSyncType(),
                normalizeNullable(dto.getSourceDataSourceId()),
                normalizeNullable(dto.getTargetDataSourceId()));
        return PagingData.from(taskRepository.queryPage(workspaceId, query).map(this::toTaskVO));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity entity = requireTask(workspaceId, id);
        if (taskRepository.deleteById(workspaceId, entity.getId()) <= 0) {
            throw new DataSyncException(DataSyncErrorCode.DELETE_TASK_FAILED);
        }
        return true;
    }

    @Override
    public DataSyncInstanceVO queryInstance(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return toInstanceVO(requireInstance(workspaceId, id));
    }

    @Override
    public PagingData<DataSyncInstanceVO> queryInstancePage(DataSyncInstanceQueryDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY);
        if (dto.getSorts() != null && !dto.getSorts().isEmpty()) {
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
                normalizeNullable(dto.getTaskId()),
                normalizeNullable(dto.getKeyword()),
                dto.getStatus(),
                dto.getTriggerType(),
                dto.getStartTimeStart(),
                dto.getStartTimeEnd());
        return PagingData.from(instanceRepository.queryPage(workspaceId, query).map(this::toInstanceVO));
    }

    private void applyDefinition(DataSyncTaskEntity entity, DataSyncTaskDTO dto) {
        entity.setSourceDataSourceId(dto.getSourceDataSourceId().trim());
        entity.setSourceDatabase(normalizeNullable(dto.getSourceDatabase()));
        entity.setSourceSchema(normalizeNullable(dto.getSourceSchema()));
        entity.setSourceTable(normalizeRequired(dto.getSourceTable(), "来源表不能为空"));
        entity.setTargetDataSourceId(dto.getTargetDataSourceId().trim());
        entity.setTargetDatabase(normalizeNullable(dto.getTargetDatabase()));
        entity.setTargetSchema(normalizeNullable(dto.getTargetSchema()));
        entity.setTargetTable(normalizeRequired(dto.getTargetTable(), "目标表不能为空"));
        entity.setRuntimeConfig(JSONUtils.toJson(requireRuntimeConfig(dto.getRuntimeConfig())));
        entity.setRemark(normalizeNullable(dto.getRemark()));
    }

    private void validateDatasourceReferences(DataSyncTaskDTO dto) {
        dataSourceService.queryDataSource(dto.getSourceDataSourceId());
        dataSourceService.queryDataSource(dto.getTargetDataSourceId());
    }

    private DataSyncType requireOffline(DataSyncType syncType) {
        if (syncType != DataSyncType.OFFLINE) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "当前阶段只支持 OFFLINE");
        }
        return syncType;
    }

    private DataSyncRuntimeConfigDTO requireRuntimeConfig(DataSyncRuntimeConfigDTO config) {
        if (config == null
                || config.getFetchSize() == null
                || config.getReadBatchSize() == null
                || config.getWriteBatchSize() == null
                || config.getTimeoutSeconds() == null
                || config.getFetchSize() < 1
                || config.getFetchSize() > 100000
                || config.getReadBatchSize() < 1
                || config.getReadBatchSize() > 100000
                || config.getWriteBatchSize() < 1
                || config.getWriteBatchSize() > 100000
                || config.getTimeoutSeconds() < 1
                || config.getTimeoutSeconds() > 600) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "运行参数超出允许范围");
        }
        return config;
    }

    private void requireTaskDto(DataSyncTaskDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK);
        if (!StringUtils.hasText(dto.getSourceDataSourceId()) || !StringUtils.hasText(dto.getTargetDataSourceId())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "来源和目标数据源不能为空");
        }
    }

    private DataSyncTaskEntity requireTask(String workspaceId, String id) {
        if (!StringUtils.hasText(id)) throw new DataSyncException(DataSyncErrorCode.TASK_NOT_FOUND);
        return taskRepository
                .queryById(workspaceId, id)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.TASK_NOT_FOUND));
    }

    private DataSyncInstanceEntity requireInstance(String workspaceId, String id) {
        if (!StringUtils.hasText(id)) throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_FOUND);
        return instanceRepository
                .queryById(workspaceId, id)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_FOUND));
    }

    private void ensureTaskNameAvailable(String workspaceId, String name, String excludeId) {
        if (taskRepository.existsByName(workspaceId, name, excludeId)) {
            throw new DataSyncException(DataSyncErrorCode.DUPLICATE_TASK_NAME);
        }
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, message);
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DataSyncTaskVO toTaskVO(DataSyncTaskEntity source) {
        DataSyncTaskVO target = new DataSyncTaskVO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setSyncType(source.getSyncType() == null ? null : source.getSyncType().name());
        target.setSourceDataSourceId(source.getSourceDataSourceId());
        target.setSourceDatabase(source.getSourceDatabase());
        target.setSourceSchema(source.getSourceSchema());
        target.setSourceTable(source.getSourceTable());
        target.setTargetDataSourceId(source.getTargetDataSourceId());
        target.setTargetDatabase(source.getTargetDatabase());
        target.setTargetSchema(source.getTargetSchema());
        target.setTargetTable(source.getTargetTable());
        target.setRuntimeConfig(toRuntimeConfigVO(source.getRuntimeConfig()));
        target.setDefinitionVersion(source.getDefinitionVersion());
        target.setRemark(source.getRemark());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private DataSyncRuntimeConfigVO toRuntimeConfigVO(String json) {
        DataSyncRuntimeConfigDTO source = JSONUtils.parseObject(json, DataSyncRuntimeConfigDTO.class);
        DataSyncRuntimeConfigVO target = new DataSyncRuntimeConfigVO();
        target.setFetchSize(source.getFetchSize());
        target.setReadBatchSize(source.getReadBatchSize());
        target.setWriteBatchSize(source.getWriteBatchSize());
        target.setTimeoutSeconds(source.getTimeoutSeconds());
        return target;
    }

    private DataSyncInstanceVO toInstanceVO(DataSyncInstanceEntity source) {
        DataSyncInstanceVO target = new DataSyncInstanceVO();
        target.setId(source.getId());
        target.setTaskId(source.getTaskId());
        target.setTaskName(source.getTaskName());
        target.setTaskVersion(source.getTaskVersion());
        target.setTriggerType(source.getTriggerType() == null ? null : source.getTriggerType().name());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        target.setReadRows(source.getReadRows());
        target.setWriteRows(source.getWriteRows());
        target.setStartTime(source.getStartTime());
        target.setFinishTime(source.getFinishTime());
        target.setErrorCode(source.getErrorCode());
        target.setErrorMessage(source.getErrorMessage());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }
}
