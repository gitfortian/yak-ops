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
