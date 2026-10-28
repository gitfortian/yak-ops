package io.yak.ops.common.bean.vo.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** One crawl run with its counters. */
@Getter
@Setter
public class DataMapCrawlVO {

    /** Run identity. */
    private String id;

    /** Datasource whose namespace was enumerated. */
    private String datasourceId;

    /** Catalog slot of the crawled namespace. */
    private String database;

    /** Schema slot of the crawled namespace. */
    private String schema;

    /** PENDING, RUNNING, SUCCEEDED or FAILED. */
    private String status;

    /** Tables observed by the run. */
    private int tableCount;

    /** Columns observed by the run. */
    private int columnCount;

    /** Assets first inserted by the run. */
    private int addedCount;

    /** Existing assets refreshed by the run. */
    private int updatedCount;

    /** Assets marked MISSING by the run. */
    private int removedCount;

    /** Tables whose columns could not be read. */
    private int failedCount;

    /** Tables kept alive but skipped by the exclude pattern. */
    private int filteredCount;

    /** How the run was requested: MANUAL or SCHEDULED. */
    private String triggerType;

    /** Java regex; matching tables were kept alive but not re-crawled. */
    private String excludePattern;

    /** Wall-clock seconds between claim and the terminal write; null while active. */
    private Long durationSeconds;

    /** Crawl whose failed tables this run re-attempts; null for a fresh run. */
    private String retryOf;

    /** Run-level failure text; null on success. */
    private String error;

    /** Submission timestamp. */
    private LocalDateTime createdTime;

    /** Claim timestamp. */
    private LocalDateTime startedTime;

    /** Terminal timestamp. */
    private LocalDateTime finishedTime;

    /** Authenticated submitter identity. */
    private String createBy;
}
