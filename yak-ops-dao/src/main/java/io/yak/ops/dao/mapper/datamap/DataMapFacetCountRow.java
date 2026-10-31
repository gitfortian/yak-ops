package io.yak.ops.dao.mapper.datamap;

import lombok.Getter;
import lombok.Setter;

/** Aggregated asset counts of one facet value, such as a data domain or a datasource. */
@Getter
@Setter
public class DataMapFacetCountRow {
    /** Facet value identity; a datasource id or a data domain id. */
    private String facetKey;
    /** Active assets carrying the facet value. */
    private Long assetCount;
    /** Active assets of the facet value whose curated description is filled. */
    private Long describedCount;
    /** Active assets of the facet value whose curated owner is filled. */
    private Long ownedCount;
}
