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

    // VIEW_AND_PARAMETERS
}
