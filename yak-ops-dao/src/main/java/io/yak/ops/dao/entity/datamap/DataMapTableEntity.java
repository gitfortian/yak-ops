package io.yak.ops.dao.entity.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one catalogued physical table or view as current state. */
@Getter
@Setter
public class DataMapTableEntity {
    /** Application-generated asset identity, stable across crawls. */
    private String id;
    /** Workspace owning the asset. */
    private String workspaceId;
    /** Datasource the asset was observed through. */
    private String datasourceId;
    /** Datasource display name, read through the datasource join; not a column of this table. */
    private String datasourceName;
    /** Catalog slot of the physical identity. */
    private String databaseName;
    /** Schema slot of the physical identity. */
    private String schemaName;
    /** Physical table or view name, unqualified. */
    private String name;
    /** Engine-reported TABLE_TYPE; crawler-owned. */
    private String tableType;
    /** Engine-reported table comment; crawler-owned. */
    private String remarks;
    /** ACTIVE or MISSING; crawler-owned. */
    private String status;
    /** First complete run that observed this name. */
    private LocalDateTime firstSeenTime;
    /** Most recent run that observed this name. */
    private LocalDateTime lastSeenTime;
    /** User-maintained business description. */
    private String description;
    /** Accountable Workspace member. */
    private String ownerId;
    /** Optional data domain of 数仓规划. */
    private String domainId;
    /** Creation timestamp. */
    private LocalDateTime createTime;
    /** Most recent write timestamp. */
    private LocalDateTime updateTime;
    /** Authenticated creator identity. */
    private String createBy;
    /** Authenticated updater identity. */
    private String updateBy;
}
