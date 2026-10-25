package io.yak.ops.common.bean.vo.modeling;

/**
 * Selectable layer contract consumed by dimension modeling; only enabled layers are projected.
 *
 * @param id layer identity
 * @param enCode uppercase abbreviation shown in the selector
 * @param cnName business display name
 * @param dataSourceId datasource that physical publishing resolves for this layer
 * @param dataSourceName current datasource name for display
 * @param namingRuleEnabled whether a model suffix of this layer must carry a dictionary root word
 */
public record LayerOptionVO(
        String id,
        String enCode,
        String cnName,
        String dataSourceId,
        String dataSourceName,
        boolean namingRuleEnabled) {}
