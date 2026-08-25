
package io.yak.ops.business.lineage.controller.v1.converter;

import io.yak.ops.business.lineage.controller.v1.dto.LineageRequests.RegisterAssetRequest;
import io.yak.ops.business.lineage.controller.v1.dto.LineageRequests.RegisterRelationRequest;
import io.yak.ops.business.lineage.service.LineageWriteService;
import org.springframework.stereotype.Component;

/** Pure HTTP request to application command converter. */
@Component
public class LineageRequestConverter {

  public LineageWriteService.RegisterAssetCommand asset(RegisterAssetRequest request) {
    return new LineageWriteService.RegisterAssetCommand(
        request.assetKey(), request.assetType(), request.name(), request.sourceType(), request.sourceId(),
        request.parentAssetId(), request.dataSourceId(), request.databaseName(), request.schemaName(),
        request.tableName(), request.columnName(), request.properties());
  }

  public LineageWriteService.RegisterRelationCommand relation(RegisterRelationRequest request) {
    return new LineageWriteService.RegisterRelationCommand(
        request.sourceAssetId(), request.targetAssetId(), request.relationType(), request.sourceType(),
        request.sourceId(), request.expression(), request.confidence(), request.version(),
        request.observedAt(), request.properties());
  }
}
