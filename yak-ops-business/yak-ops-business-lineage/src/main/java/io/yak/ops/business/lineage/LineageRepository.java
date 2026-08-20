package io.yak.ops.business.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

interface LineageRepository {

  LineageAsset upsertAsset(AssetWrite write);

  LineageRelation upsertRelation(RelationWrite write);

  Optional<LineageAsset> findAsset(long assetId);

  Optional<LineageAsset> findAssetByKey(String assetKey);

  List<LineageAsset> findAssetsByIds(Set<Long> assetIds);

  List<LineageRelation> findOutgoingRelations(Set<Long> sourceAssetIds);

  List<LineageRelation> findIncomingRelations(Set<Long> targetAssetIds);

  record AssetWrite(
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

  record RelationWrite(
      long sourceAssetId,
      long targetAssetId,
      LineageRelationType relationType,
      String sourceType,
      String sourceId,
      String expression,
      BigDecimal confidence,
      String version,
      Instant observedAt,
      JsonNode properties) {
  }
}
