package io.yak.ops.common.bean.vo.datamap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One asset as it appears in catalog, favourite and recent lists. */
@Getter
@Setter
public class DataMapTableSummaryVO {

    /** Asset identity. */
    private String id;

    /** Datasource the asset was observed through. */
    private String datasourceId;

    /** Datasource display name. */
    private String datasourceName;

    /** Catalog slot of the physical identity. */
    private String database;

    /** Schema slot of the physical identity. */
    private String schema;

    /** Physical table or view name. */
    private String name;

    /** Engine-reported TABLE_TYPE. */
    private String tableType;

    /** Engine-reported table comment. */
    private String remarks;

    /** ACTIVE or MISSING. */
    private String status;

    /** User-maintained business description. */
    private String description;

    /** Accountable Workspace member. */
    private String ownerId;

    /** Optional data domain of 数仓规划. */
    private String domainId;

    /** First complete run that observed the asset. */
    private LocalDateTime firstSeenTime;

    /** Most recent run that observed the asset. */
    private LocalDateTime lastSeenTime;

    /** Tag values applied to the asset, grouped by their key. */
    private List<DataMapTagRefVO> tags = new ArrayList<>();

    /** Column names that matched the keyword; empty when no keyword was given. */
    private List<String> matchedColumns = new ArrayList<>();

    /** Summed visit count; populated only on the overview hot list, null elsewhere. */
    private Long viewCount;
}
