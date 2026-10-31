package io.yak.ops.common.bean.vo.datamap;

/** Coverage counts of one browse facet value, such as a data domain or a datasource. */
public record DataMapFacetSummaryVO(String facetKey, long assetCount, long describedCount, long ownedCount) {}
