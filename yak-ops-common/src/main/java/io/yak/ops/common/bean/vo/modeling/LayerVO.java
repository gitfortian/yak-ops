package io.yak.ops.common.bean.vo.modeling;

/**
 * Authorized layer projection; presets expose their immutable abbreviation and preset flag.
 *
 * @param id layer identity
 * @param enCode Workspace-unique uppercase abbreviation
 * @param cnName business display name
 * @param dataSourceId bound physical datasource, or null before binding
 * @param dataSourceName current datasource name for display, or null when unbound
 * @param description optional business description
 * @param enabled whether dimension modeling may select the layer
 * @param builtin whether the layer is one of the five presets
 * @param sortNo display order
 * @param revision current optimistic version
 */
public record LayerVO(
        String id,
        String enCode,
        String cnName,
        String dataSourceId,
        String dataSourceName,
        String description,
        boolean enabled,
        boolean builtin,
        int sortNo,
        long revision) {}
