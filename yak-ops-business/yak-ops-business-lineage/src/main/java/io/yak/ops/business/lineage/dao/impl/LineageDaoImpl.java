package io.yak.ops.business.lineage.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.lineage.dao.LineageDao;
import io.yak.ops.business.lineage.dao.mapper.LineageAssetMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageQueryMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageRelationMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageWriteMapper;
import io.yak.ops.business.lineage.dao.model.LineageAssetPO;
import io.yak.ops.business.lineage.dao.model.LineageRelationPO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis-Plus DAO with XML reserved for complex/atomic SQL. */
@Repository
@DependsOn("yakLineageFlyway")
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class LineageDaoImpl implements LineageDao {

  private final LineageAssetMapper assetMapper;
  private final LineageRelationMapper relationMapper;
  private final LineageWriteMapper writeMapper;
  private final LineageQueryMapper queryMapper;

  @Override
  public int upsertAsset(LineageAssetPO asset) {
    return writeMapper.upsertAsset(asset);
  }

  @Override
  public int upsertRelation(LineageRelationPO relation) {
    return writeMapper.upsertRelation(relation);
  }

  @Override
  public List<LineageAssetPO> upsertAssets(List<LineageAssetPO> assets, int batchSize) {
    if (assets == null || assets.isEmpty()) return List.of();
    Map<String, LineageAssetPO> result = new LinkedHashMap<>();
    for (int start = 0; start < assets.size(); start += batchSize) {
      List<LineageAssetPO> batch = assets.subList(start, Math.min(start + batchSize, assets.size()));
      writeMapper.upsertAssets(batch);
      List<String> keys = batch.stream().map(LineageAssetPO::getAssetKey).toList();
      assetMapper.selectList(
          Wrappers.<LineageAssetPO>lambdaQuery().in(LineageAssetPO::getAssetKey, keys))
          .forEach(row -> result.put(row.getAssetKey(), row));
    }
    return new ArrayList<>(result.values());
  }

  @Override
  public void upsertRelations(List<LineageRelationPO> relations, int batchSize) {
    if (relations == null || relations.isEmpty()) return;
    for (int start = 0; start < relations.size(); start += batchSize) {
      List<LineageRelationPO> batch =
          relations.subList(start, Math.min(start + batchSize, relations.size()));
      writeMapper.upsertRelations(batch);
    }
  }

  @Override
  public int deleteRelationsByEvidence(String sourceType, String sourceId) {
    return relationMapper.delete(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .eq(LineageRelationPO::getSourceType, sourceType)
            .eq(LineageRelationPO::getSourceId, sourceId));
  }

  @Override
  public Set<Long> selectAssetIdsByEvidence(String sourceType, String sourceId) {
    List<Long> ids = queryMapper.selectAssetIdsByEvidence(sourceType, sourceId);
    return ids == null || ids.isEmpty() ? Set.of() : Set.copyOf(ids);
  }

  @Override
  public int deleteUnreferencedOwnedAssets(Set<Long> assetIds, String ownerType, String ownerId) {
    if (assetIds == null || assetIds.isEmpty()) return 0;
    return writeMapper.deleteUnreferencedOwnedAssets(assetIds, ownerType, ownerId);
  }

  @Override
  public LineageAssetPO selectAssetForUpdate(String assetKey) {
    return writeMapper.selectAssetForUpdate(assetKey);
  }

  @Override
  public LineageAssetPO selectAsset(long assetId) {
    return assetMapper.selectById(assetId);
  }

  @Override
  public LineageAssetPO selectAssetByKey(String assetKey) {
    return assetMapper.selectOne(
        Wrappers.<LineageAssetPO>lambdaQuery().eq(LineageAssetPO::getAssetKey, assetKey));
  }

  @Override
  public List<LineageAssetPO> selectAssets(AssetSearch query) {
    AssetSearch condition = query == null ? new AssetSearch(null, null, 30) : query;
    return assetMapper.selectList(
        Wrappers.<LineageAssetPO>lambdaQuery()
            .and(
                StringUtils.hasText(condition.keyword()),
                nested -> nested.like(LineageAssetPO::getName, condition.keyword())
                    .or().like(LineageAssetPO::getAssetKey, condition.keyword())
                    .or().like(LineageAssetPO::getTableName, condition.keyword())
                    .or().like(LineageAssetPO::getColumnName, condition.keyword()))
            .eq(StringUtils.hasText(condition.assetType()),
                LineageAssetPO::getAssetType, condition.assetType())
            .orderByDesc(LineageAssetPO::getUpdateTime)
            .orderByDesc(LineageAssetPO::getId)
            .last("LIMIT " + Math.max(1, condition.limit())));
  }

  @Override
  public List<LineageAssetPO> selectAssetsByIds(Set<Long> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) return List.of();
    return assetMapper.selectList(
        Wrappers.<LineageAssetPO>lambdaQuery()
            .in(LineageAssetPO::getId, assetIds)
            .orderByAsc(LineageAssetPO::getId));
  }

  @Override
  public LineageRelationPO selectRelationByIdentity(LineageRelationPO identity) {
    return relationMapper.selectOne(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .eq(LineageRelationPO::getSourceAssetId, identity.getSourceAssetId())
            .eq(LineageRelationPO::getTargetAssetId, identity.getTargetAssetId())
            .eq(LineageRelationPO::getRelationType, identity.getRelationType())
            .eq(LineageRelationPO::getSourceType, identity.getSourceType())
            .eq(LineageRelationPO::getSourceId, identity.getSourceId())
            .eq(LineageRelationPO::getVersion, identity.getVersion()));
  }

  @Override
  public List<LineageRelationPO> selectOutgoingRelations(Set<Long> sourceAssetIds) {
    if (sourceAssetIds == null || sourceAssetIds.isEmpty()) return List.of();
    return relationMapper.selectList(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .in(LineageRelationPO::getSourceAssetId, sourceAssetIds)
            .orderByAsc(LineageRelationPO::getId));
  }

  @Override
  public List<LineageRelationPO> selectIncomingRelations(Set<Long> targetAssetIds) {
    if (targetAssetIds == null || targetAssetIds.isEmpty()) return List.of();
    return relationMapper.selectList(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .in(LineageRelationPO::getTargetAssetId, targetAssetIds)
            .orderByAsc(LineageRelationPO::getId));
  }
}
