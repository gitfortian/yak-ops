package io.yak.ops.common.bean.vo.modeling;

import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.enums.modeling.PublicationStatus;
import io.yak.ops.common.enums.modeling.StoragePolicy;
import java.util.List;

/**
 * Authorized logical model projection.
 *
 * <p>{@code type} is null when the stored row carries a kind this build does not know; such a row is
 * still readable but refuses edits, so a hand-written {@code DWS} row cannot be silently rewritten
 * as {@code DWD}. {@code fields} is null in list projections and populated on detail reads.
 *
 * @param id model identity
 * @param type model kind, null for an unknown stored value
 * @param name full table name including the layer prefix
 * @param cnName business display name
 * @param layerId selected warehouse layer
 * @param layerName layer display name
 * @param categoryId owning planning category
 * @param categoryName category display name
 * @param processId owning business process, null outside DWD
 * @param processName business process display name
 * @param domainId owning data domain, null for application-layer models
 * @param domainName data domain display name
 * @param datamartId owning data mart, null for public-layer models
 * @param datamartName data mart display name
 * @param dimensionId described concept dimension, null when none
 * @param dimensionName concept dimension display name
 * @param storagePolicy declared storage strategy
 * @param lifecycleDays data retention in days
 * @param ownerId assigned owner identity
 * @param description optional business description
 * @param namingRootWordId naming-dictionary word root chosen when the layer rule was applied, null
 *     when the model was created without a naming rule or before the rule existed
 * @param fieldCount number of fields, zero on write paths
 * @param revision current optimistic version
 * @param fields ordered fields on detail reads, null in list projections
 * @param latestPublicationStatus status of the newest publication, null when the model was never
 *     published or the stored value is unknown to this build
 */
public record ModelVO(
        String id,
        ModelType type,
        String name,
        String cnName,
        String layerId,
        String layerName,
        String categoryId,
        String categoryName,
        String processId,
        String processName,
        String domainId,
        String domainName,
        String datamartId,
        String datamartName,
        String dimensionId,
        String dimensionName,
        StoragePolicy storagePolicy,
        int lifecycleDays,
        String ownerId,
        String description,
        String namingRootWordId,
        long fieldCount,
        long revision,
        List<ModelFieldVO> fields,
        PublicationStatus latestPublicationStatus) {}
