package io.yak.ops.dao.entity.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one data map crawl run and its terminal counters. */
@Getter
@Setter
public class DataMapCrawlEntity {
    /** Application-generated run identity. */
    private String id;
    /** Workspace owning the run. */
    private String workspaceId;
    /** Datasource whose namespace is enumerated. */
    private String datasourceId;
    /** Datasource binding revision pinned at submission. */
    private Long datasourceRevision;
    /** Catalog slot of the crawled namespace; empty when the engine has none. */
    private String databaseName;
    /** Schema slot of the crawled namespace; empty when the engine has none. */
    private String schemaName;
    /** PENDING, RUNNING, SUCCEEDED or FAILED. */
    private String status;
    /** 1 while the run is active, null once terminal. */
    private Integer activeScope;
    /** Tables observed by the run. */
    private Integer tableCount;
    /** Columns observed by the run. */
    private Integer columnCount;
    /** Assets first inserted by the run. */
    private Integer addedCount;
    /** Existing assets refreshed by the run. */
    private Integer updatedCount;
    /** Assets marked MISSING by the run. */
    private Integer removedCount;
    /** Tables whose columns could not be read. */
    private Integer failedCount;
    /** Run-level failure text; null on success. */
    private String error;
    /** Submission timestamp. */
    private LocalDateTime createdTime;
    /** Claim timestamp; null while pending. */
    private LocalDateTime startedTime;
    /** Terminal timestamp; null while active. */
    private LocalDateTime finishedTime;
    /** Authenticated submitter identity. */
    private String createBy;
}
