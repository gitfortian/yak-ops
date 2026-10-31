package io.yak.ops.common.bean.vo.datamap;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Browse facet counts of the Workspace, powering the explore home and the catalog browse panel. */
@Getter
@Setter
public class DataMapOverviewFacetsVO {

    /** Per data domain asset and coverage counts, ordered by domain id. */
    private List<DataMapFacetSummaryVO> domains = new ArrayList<>();

    /** Per datasource asset and coverage counts, ordered by datasource id. */
    private List<DataMapFacetSummaryVO> datasources = new ArrayList<>();
}
