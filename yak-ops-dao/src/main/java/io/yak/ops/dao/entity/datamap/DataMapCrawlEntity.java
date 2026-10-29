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
    /** Task configuration that produced the run; null for legacy standalone runs. */
    private String taskId;
    /** Task name resolved at read time; never stored on the run. */
    private String taskName;
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
    /** Tables kept alive but skipped by the exclude pattern. */
    private Integer filteredCount;
    /** How the run was requested: MANUAL or SCHEDULED. */
    private String triggerType;
    /** Java regex snapshot; matching tables are kept alive but not re-crawled. */
    private String excludePattern;
    /** JSON array snapshot of excluded table names; matching tables are kept alive. */
    private String excludeTables;
    /** Crawl whose failed tables this run re-attempts; null for a fresh run. */
    private String retryOf;
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
