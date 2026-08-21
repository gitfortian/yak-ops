package io.yak.ops.business.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

interface LineageRepository {

  LineageAsset upsertAsset(AssetWrite write);

  LineageRelation upsertRelation(RelationWrite write);

  default Map<String, LineageAsset> upsertAssets(List<AssetWrite> writes, int batchSize) {
    throw new UnsupportedOperationException("Batch asset registration is not supported");
  }

  default void upsertRelations(List<RelationWrite> writes, int batchSize) {
    throw new UnsupportedOperationException("Batch relation registration is not supported");
  }

  default int deleteRelationsByEvidence(String sourceType, String sourceId) {
    throw new UnsupportedOperationException("Relation evidence cleanup is not supported");
  }

  default Set<Long> findAssetIdsByEvidence(String sourceType, String sourceId) {
    throw new UnsupportedOperationException("Relation evidence lookup is not supported");
  }

  default int deleteUnreferencedOwnedAssets(Set<Long> assetIds, String ownerType, String ownerId) {
    throw new UnsupportedOperationException("Owned asset cleanup is not supported");
  }

  default Optional<LineageAsset> lockAssetByKey(String assetKey) {
    return findAssetByKey(assetKey);
  }

  Optional<LineageAsset> findAsset(long assetId);

  Optional<LineageAsset> findAssetByKey(String assetKey);

  List<LineageAsset> searchAssets(String keyword, LineageAssetType assetType, int limit);

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
