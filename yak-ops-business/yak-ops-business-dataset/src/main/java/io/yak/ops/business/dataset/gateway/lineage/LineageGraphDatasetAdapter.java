package io.yak.ops.business.dataset.gateway.lineage;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.service.LineageMaintenanceService;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.service.LineageQueryService;
import io.yak.ops.business.lineage.service.LineageWriteService;
import org.springframework.stereotype.Component;

/** Adapts the shared lineage graph API to Dataset-owned asset/relation values. */
@Component
public class LineageGraphDatasetAdapter implements DatasetLineageGraphGateway {

  private final LineageQueryService queryService;
  private final LineageWriteService writeService;
  private final LineageMaintenanceService maintenanceService;

  public LineageGraphDatasetAdapter(
      LineageQueryService queryService,
      LineageWriteService writeService,
      LineageMaintenanceService maintenanceService) {
    this.queryService = queryService;
    this.writeService = writeService;
    this.maintenanceService = maintenanceService;
  }

  @Override
  public void clearRelationsByEvidence(String evidenceSourceType, String evidenceId) {
    maintenanceService.clearRelationsByEvidence(evidenceSourceType, evidenceId);
  }

  @Override
  public Asset registerAsset(AssetSpec spec) {
    LineageAsset asset =
        writeService.registerAsset(
            new LineageWriteService.RegisterAssetCommand(
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
    LineageAsset asset = queryService.getAssetByKey(assetKey);
    return new Asset(asset.id(), asset.assetKey());
  }

  @Override
  public void registerRelation(RelationSpec spec) {
    writeService.registerRelation(
        new LineageWriteService.RegisterRelationCommand(
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
