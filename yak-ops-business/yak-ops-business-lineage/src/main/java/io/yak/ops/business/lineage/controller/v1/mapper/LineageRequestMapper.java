package io.yak.ops.business.lineage.controller.v1.mapper;

import io.yak.ops.business.lineage.LineageService;
import io.yak.ops.business.lineage.controller.v1.dto.LineageRequests.RegisterAssetRequest;
import io.yak.ops.business.lineage.controller.v1.dto.LineageRequests.RegisterRelationRequest;
import org.springframework.stereotype.Component;

/** Pure HTTP request to application command mapper. */
@Component
public class LineageRequestMapper {

  public LineageService.RegisterAssetCommand asset(RegisterAssetRequest request) {
    return new LineageService.RegisterAssetCommand(
        request.assetKey(),
        request.assetType(),
        request.name(),
        request.sourceType(),
        request.sourceId(),
        request.parentAssetId(),
        request.dataSourceId(),
        request.databaseName(),
        request.schemaName(),
        request.tableName(),
        request.columnName(),
        request.properties());
  }

  public LineageService.RegisterRelationCommand relation(RegisterRelationRequest request) {
    return new LineageService.RegisterRelationCommand(
        request.sourceAssetId(),
        request.targetAssetId(),
        request.relationType(),
        request.sourceType(),
        request.sourceId(),
        request.expression(),
        request.confidence(),
        request.version(),
        request.observedAt(),
        request.properties());
  }
}
