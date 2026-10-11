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
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.JSONUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Only an HTTP DTO/VO translator; no data sync service or persistence behavior. */
final class SyncDefinitionHttpMapper {
    private SyncDefinitionHttpMapper() {}

    static CreateDefinitionDTO toCreate(DataSyncTaskDTO source) {
        CreateDefinitionDTO dto = new CreateDefinitionDTO();
        dto.setName(source.getName());
        dto.setTaskType("DATA_SYNC");
        dto.setRemark(source.getRemark());
        dto.setParameters(parameters(source, null));
        return dto;
    }

    static UpdateDefinitionDTO toUpdate(DataSyncTaskDTO source, DefinitionVO old) {
        UpdateDefinitionDTO dto = new UpdateDefinitionDTO();
        dto.setName(source.getName());
        dto.setRemark(source.getRemark());
        dto.setParameters(parameters(source, old.getParameters()));
        return dto;
    }

    static DefinitionQueryDTO toQuery(DataSyncTaskQueryDTO source) {
        DefinitionQueryDTO dto = new DefinitionQueryDTO();
        dto.setPageNo(source.getPageNo());
        dto.setPageSize(source.getPageSize());
        dto.setSorts(source.getSorts());
        dto.setKeyword(source.getKeyword());
        dto.setTaskType("DATA_SYNC");
        dto.setStatus(source.getStatus() == null ? null : DefinitionStatus.valueOf(source.getStatus().name()));
        Map<String, String> filters = new HashMap<>();
        if (source.getSyncType() != null) filters.put("syncType", source.getSyncType().name());
        if (source.getSourceDataSourceId() != null) filters.put("sourceDataSourceId", source.getSourceDataSourceId());
        if (source.getTargetDataSourceId() != null) filters.put("targetDataSourceId", source.getTargetDataSourceId());
        dto.setParameterFilters(filters);
        return dto;
    }


    private static String parameters(DataSyncTaskDTO dto, String existing) {
        ObjectNode target = existing == null ? JSONUtils.createObjectNode()
                : (ObjectNode) JSONUtils.readTree(existing);
        JsonNode input = JSONUtils.readTree(JSONUtils.toJson(dto));
        for (String key : List.of("syncType", "writeMode", "sourceDataSourceId", "sourceDatabase",
                "sourceSchema", "sourceTable", "targetDataSourceId", "targetDatabase",
                "targetSchema", "targetTable")) {
            if (input.has(key)) target.set(key, input.get(key));
        }
        for (String key : List.of("runtimeConfig", "realtimeConfig", "retryPolicy")) {
            if (input.hasNonNull(key)) target.set(key, input.get(key));
        }
        return JSONUtils.toJson(target);
    }


    static DataSyncTaskVO toSync(DefinitionVO source) {
        JsonNode p = JSONUtils.readTree(source.getParameters());
        DataSyncTaskVO vo = new DataSyncTaskVO();
        vo.setId(source.getId());
        vo.setName(source.getName());
        vo.setStatus(source.getStatus());
        vo.setDesiredState("STOPPED");
        vo.setDefinitionVersion(source.getDefinitionVersion());
        vo.setRemark(source.getRemark());
        vo.setCreateTime(source.getCreateTime());
        vo.setUpdateTime(source.getUpdateTime());
        vo.setUpdateBy(source.getUpdateBy());
        vo.setSyncType(value(p, "syncType"));
        vo.setWriteMode(value(p, "writeMode"));
        vo.setSourceDataSourceId(value(p, "sourceDataSourceId"));
        vo.setSourceDatabase(value(p, "sourceDatabase"));
        vo.setSourceSchema(value(p, "sourceSchema"));
        vo.setSourceTable(value(p, "sourceTable"));
        vo.setTargetDataSourceId(value(p, "targetDataSourceId"));
        vo.setTargetDatabase(value(p, "targetDatabase"));
        vo.setTargetSchema(value(p, "targetSchema"));
        vo.setTargetTable(value(p, "targetTable"));
        if (p.hasNonNull("runtimeConfig")) {
            vo.setRuntimeConfig(JSONUtils.parseObject(p.get("runtimeConfig").toString(),
                    io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO.class));
        }
        if (p.hasNonNull("realtimeConfig")) {
            vo.setRealtimeConfig(JSONUtils.parseObject(p.get("realtimeConfig").toString(),
                    io.yak.ops.common.bean.vo.datasync.DataSyncRealtimeConfigVO.class));
        }
        if (p.hasNonNull("retryPolicy")) {
            vo.setRetryPolicy(JSONUtils.parseObject(p.get("retryPolicy").toString(),
                    io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO.class));
        }
        return vo;
    }

    static PagingData<DataSyncTaskVO> toPage(PagingData<DefinitionVO> source) {
        return new PagingData<>(source.getBizData().stream().map(SyncDefinitionHttpMapper::toSync).toList(),
                source.getPagination());
    }

    private static String value(JsonNode node, String key) {
        JsonNode field = node.get(key);
        return field == null || field.isNull() ? null : field.asText();
    }

}
