package io.yak.ops.dao.repository.modeling;

import io.yak.ops.common.enums.modeling.ModelType;

/**
 * One DWD or DWS model of a layer as the naming rule sees it.
 *
 * @param id model identity
 * @param type model kind
 * @param name full table name including the layer prefix
 * @param cnName business display name
 * @param processCode code of the owning business process, or null for a malformed row
 * @param namingRootWordId root word recorded when the name was composed under a rule, or null
 */
public record ModelNamingRow(
        String id, ModelType type, String name, String cnName, String processCode, String namingRootWordId) {}
