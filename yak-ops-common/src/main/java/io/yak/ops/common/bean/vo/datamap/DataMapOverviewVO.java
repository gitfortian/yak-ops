package io.yak.ops.common.bean.vo.datamap;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Workspace-level counts and the ten most viewed assets. */
@Getter
@Setter
public class DataMapOverviewVO {

    /** Active assets catalogued in the Workspace. */
    private long assetCount;

    /** Distinct datasources that own at least one catalogued asset. */
    private long managedDatasourceCount;

    /** Assets first observed during the last seven days. */
    private long newTablesLastSevenDays;

    /** Active assets without a data domain. */
    private long unclassifiedCount;

    /** Active assets whose curated description is filled. */
    private long describedCount;

    /** Active assets whose curated owner is filled. */
    private long ownedCount;

    /** The ten assets with the highest summed visit count. */
    private List<DataMapTableSummaryVO> hotTables = new ArrayList<>();
}
