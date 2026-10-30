package io.yak.ops.common.bean.vo.modeling;

import io.yak.ops.common.enums.modeling.PublicationEnvironment;

/**
 * One per-environment physical target bound to a layer.
 *
 * @param id binding identity
 * @param layerId owning layer
 * @param environment DEVELOP or PROD
 * @param dataSourceId datasource used to reach the target
 * @param dataSourceName datasource display name resolved for the UI
 * @param database default target database resolved for publications
 * @param schema default target schema, null where the engine has none
 */
public record LayerTargetVO(
        String id,
        String layerId,
        PublicationEnvironment environment,
        String dataSourceId,
        String dataSourceName,
        String database,
        String schema) {}
