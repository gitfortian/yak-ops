package io.yak.ops.business.dataset.gateway.lineage;

import io.yak.ops.business.lineage.LineageAsset;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageMaintenanceService;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.LineageService;
import org.springframework.stereotype.Component;

/** Adapts the shared lineage graph API to Dataset-owned asset/relation values. */
@Component
public class LineageGraphDatasetAdapter implements DatasetLineageGraphGateway {

  private final LineageService lineageService;
  private final LineageMaintenanceService maintenanceService;

  public LineageGraphDatasetAdapter(
      LineageService lineageService, LineageMaintenanceService maintenanceService) {
    this.lineageService = lineageService;
    this.maintenanceService = maintenanceService;
  }

  @Override
  public void clearRelationsByEvidence(String evidenceSourceType, String evidenceId) {
    maintenanceService.clearRelationsByEvidence(evidenceSourceType, evidenceId);
  }

  @Override
  public Asset registerAsset(AssetSpec spec) {
    LineageAsset asset =
        lineageService.registerAsset(
            new LineageService.RegisterAssetCommand(
                spec.assetKey(),
                LineageAssetType.valueOf(spec.assetType().name()),
                spec.name(),
                spec.sourceType(),
                spec.sourceId(),
                spec.parentAssetId(),
                spec.dataSourceId(),
                spec.databaseName(),
                spec.schemaName(),
                spec.tableName(),
                spec.columnName(),
                spec.properties()));
    return new Asset(asset.id(), asset.assetKey());
  }

  @Override
  public Asset requireAssetByKey(String assetKey) {
    LineageAsset asset = lineageService.getAssetByKey(assetKey);
    return new Asset(asset.id(), asset.assetKey());
  }

  @Override
  public void registerRelation(RelationSpec spec) {
    lineageService.registerRelation(
        new LineageService.RegisterRelationCommand(
            spec.sourceAssetId(),
            spec.targetAssetId(),
            LineageRelationType.valueOf(spec.relationType().name()),
            spec.sourceType(),
            spec.sourceId(),
            spec.expression(),
            spec.confidence(),
            spec.version(),
            spec.observedAt(),
            spec.properties()));
  }
}
