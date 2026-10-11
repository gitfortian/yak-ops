package io.yak.ops.boot.controller.datasync.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.dto.task.CreateDefinitionDTO;
import io.yak.ops.common.bean.dto.task.DefinitionQueryDTO;
import io.yak.ops.common.bean.dto.task.UpdateDefinitionDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.JSONUtils;
import java.util.HashMap;
import java.util.Map;

/** HTTP-only adapter for the old single-table editor; never stores a second flat model. */
final class SyncDefinitionHttpMapper {
    private SyncDefinitionHttpMapper() {}

    static CreateDefinitionDTO toCreate(DataSyncTaskDTO request) {
        CreateDefinitionDTO result = new CreateDefinitionDTO();
        result.setName(request.getName());
        result.setTaskType("DATA_SYNC");
        result.setRemark(request.getRemark());
        result.setParameters(parameters(request));
        return result;
    }

    static UpdateDefinitionDTO toUpdate(DataSyncTaskDTO request, DefinitionVO current) {
        if (!"DATA_SYNC".equals(current.getTaskType())) {
            throw new BusinessException("该任务不是数据同步类型");
        }
        UpdateDefinitionDTO result = new UpdateDefinitionDTO();
        result.setName(request.getName());
        result.setRemark(request.getRemark());
        result.setParameters(parameters(request));
        return result;
    }

    static DefinitionQueryDTO toQuery(DataSyncTaskQueryDTO request) {
        DefinitionQueryDTO result = new DefinitionQueryDTO();
        result.setPageNo(request.getPageNo());
        result.setPageSize(request.getPageSize());
        result.setSorts(request.getSorts());
        result.setKeyword(request.getKeyword());
        result.setTaskType("DATA_SYNC");
        result.setStatus(request.getStatus() == null
                ? null : DefinitionStatus.valueOf(request.getStatus().name()));
        Map<String, String> filters = new HashMap<>();
        if (request.getSyncType() != null) {
            filters.put("sourceType", request.getSyncType() == DataSyncType.REALTIME ? "MYSQL_CDC" : "JDBC");
        }
        if (request.getSourceDataSourceId() != null) {
            filters.put("sourceDataSourceId", request.getSourceDataSourceId());
        }
        if (request.getTargetDataSourceId() != null) {
            filters.put("targetDataSourceId", request.getTargetDataSourceId());
        }
        result.setParameterFilters(filters);
        return result;
    }

    private static String parameters(DataSyncTaskDTO request) {
        if (request.getSyncType() == DataSyncType.REALTIME
                && request.getWriteMode() != DataSyncWriteMode.UPSERT) {
            throw new BusinessException("实时 CDC 任务必须使用 UPSERT；旧表单 APPEND 不会自动转换");
        }
        ObjectNode source = JSONUtils.createObjectNode();
        source.put("type", request.getSyncType() == DataSyncType.REALTIME ? "MYSQL_CDC" : "JDBC");
        source.put("datasourceId", request.getSourceDataSourceId());
        put(source, "database", request.getSourceDatabase());
        put(source, "schema", request.getSourceSchema());
        source.put("table", request.getSourceTable());

        ObjectNode sink = JSONUtils.createObjectNode();
        sink.put("type", "JDBC");
        sink.put("datasourceId", request.getTargetDataSourceId());
        put(sink, "database", request.getTargetDatabase());
        put(sink, "schema", request.getTargetSchema());
        sink.put("table", request.getTargetTable());
        if (request.getWriteMode() != null) sink.put("writeMode", request.getWriteMode().name());

        ObjectNode pipeline = JSONUtils.createObjectNode();
        pipeline.set("source", source);
        pipeline.set("transform", JSONUtils.readTree("[]"));
        pipeline.set("sink", sink);
        return JSONUtils.toJson(pipeline);
    }

    static DataSyncTaskVO toSync(DefinitionVO definition) {
        if (!"DATA_SYNC".equals(definition.getTaskType())) {
            throw new BusinessException("该任务不是数据同步类型");
        }
        JsonNode parameters = JSONUtils.readTree(definition.getParameters());
        JsonNode source = parameters.path("source");
        JsonNode sink = parameters.path("sink");
        DataSyncTaskVO vo = new DataSyncTaskVO();
        vo.setId(definition.getId());
        vo.setName(definition.getName());
        vo.setStatus(definition.getStatus());
        vo.setDesiredState("STOPPED");
        vo.setDefinitionVersion(definition.getDefinitionVersion());
        vo.setRemark(definition.getRemark());
        vo.setCreateTime(definition.getCreateTime());
        vo.setUpdateTime(definition.getUpdateTime());
        vo.setUpdateBy(definition.getUpdateBy());
        vo.setSyncType("MYSQL_CDC".equals(text(source, "type")) ? "REALTIME" : "OFFLINE");
        vo.setWriteMode(text(sink, "writeMode"));
        vo.setSourceDataSourceId(text(source, "datasourceId"));
        vo.setSourceDatabase(text(source, "database"));
        vo.setSourceSchema(text(source, "schema"));
        vo.setSourceTable(text(source, "table"));
        vo.setTargetDataSourceId(text(sink, "datasourceId"));
        vo.setTargetDatabase(text(sink, "database"));
        vo.setTargetSchema(text(sink, "schema"));
        vo.setTargetTable(text(sink, "table"));
        return vo;
    }

    static PagingData<DataSyncTaskVO> toPage(PagingData<DefinitionVO> page) {
        return new PagingData<>(page.getBizData().stream().map(SyncDefinitionHttpMapper::toSync).toList(),
                page.getPagination());
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static void put(ObjectNode object, String field, String value) {
        if (value != null) object.put(field, value);
    }
}
