package io.yak.ops.boot.controller.datasync.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.JSONUtils;
import org.junit.jupiter.api.Test;

/** Retired flat HTTP DTOs must project onto v2 pipeline without a second persistence contract. */
class SyncDefinitionHttpMapperTest {
    @Test
    void mapsLegacyOfflineEditorIntoOnlySourceTransformAndSink() {
        DataSyncTaskDTO dto = offline();
        var created = SyncDefinitionHttpMapper.toCreate(dto);
        var json = JSONUtils.readTree(created.getParameters());
        assertEquals(3, json.size());
        assertEquals("JDBC", json.path("source").path("type").asText());
        assertEquals("source", json.path("source").path("datasourceId").asText());
        assertEquals("target", json.path("sink").path("datasourceId").asText());
        assertEquals("APPEND", json.path("sink").path("writeMode").asText());
        DefinitionVO definition = new DefinitionVO();
        definition.setTaskType("DATA_SYNC");
        definition.setParameters(created.getParameters());
        assertEquals("OFFLINE", SyncDefinitionHttpMapper.toSync(definition).getSyncType());
    }

    @Test
    void explicitlyRejectsLegacyRealtimeAppendInsteadOfSilentlyChangingItsSemantics() {
        DataSyncTaskDTO dto = offline();
        dto.setSyncType(DataSyncType.REALTIME);
        assertThrows(BusinessException.class, () -> SyncDefinitionHttpMapper.toCreate(dto));
        dto.setWriteMode(DataSyncWriteMode.UPSERT);
        var created = SyncDefinitionHttpMapper.toCreate(dto);
        assertEquals("MYSQL_CDC", JSONUtils.readTree(created.getParameters()).path("source").path("type").asText());
        DefinitionVO definition = new DefinitionVO();
        definition.setTaskType("DATA_SYNC");
        definition.setParameters(created.getParameters());
        assertEquals("UPSERT", SyncDefinitionHttpMapper.toSync(definition).getWriteMode());
    }

    @Test
    void mapsDefinitionFiltersToBoundNestedJsonPaths() {
        DataSyncTaskQueryDTO dto = new DataSyncTaskQueryDTO();
        dto.setSyncType(DataSyncType.REALTIME);
        dto.setSourceDataSourceId("source");
        dto.setTargetDataSourceId("target");
        var filters = SyncDefinitionHttpMapper.toQuery(dto).getParameterFilters();
        assertEquals("MYSQL_CDC", filters.get("sourceType"));
        assertEquals("source", filters.get("sourceDataSourceId"));
        assertEquals("target", filters.get("targetDataSourceId"));
    }

    private static DataSyncTaskDTO offline() {
        DataSyncTaskDTO dto = new DataSyncTaskDTO();
        dto.setName("orders");
        dto.setSourceDataSourceId("source");
        dto.setSourceTable("orders");
        dto.setTargetDataSourceId("target");
        dto.setTargetTable("orders_copy");
        return dto;
    }
}
