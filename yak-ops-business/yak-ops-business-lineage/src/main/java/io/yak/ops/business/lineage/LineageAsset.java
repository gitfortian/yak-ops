package io.yak.ops.business.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** Stable identity for an object participating in the Yak Ops metadata graph. */
public record LineageAsset(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    String assetKey,
    LineageAssetType assetType,
    String name,
    String sourceType,
    String sourceId,
    @JsonSerialize(using = ToStringSerializer.class) Long parentAssetId,
    String dataSourceId,
    String databaseName,
    String schemaName,
    String tableName,
    String columnName,
    JsonNode properties,
    Instant createTime,
    Instant updateTime) {
}
