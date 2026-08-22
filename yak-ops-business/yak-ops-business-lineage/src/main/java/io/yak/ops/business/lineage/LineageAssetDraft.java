package io.yak.ops.business.lineage;

import com.fasterxml.jackson.databind.JsonNode;

/** Validated domain write model for registering a metadata asset. */
public record LineageAssetDraft(
    String assetKey,
    LineageAssetType assetType,
    String name,
    String sourceType,
    String sourceId,
    Long parentAssetId,
    String dataSourceId,
    String databaseName,
    String schemaName,
    String tableName,
    String columnName,
    JsonNode properties) {
}
