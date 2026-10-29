package io.yak.ops.common.bean.vo.datamap;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One metadata collection task with its latest run summary. */
@Getter
@Setter
public class DataMapCrawlTaskVO {

    /** Task identity. */
    private String id;

    /** Operator-visible task name. */
    private String name;

    /** Datasource the task collects metadata from. */
    private String datasourceId;

    /** Catalog slot collected by the task. */
    private String database;

    /** Schema slot collected by the task. */
    private String schema;

    /** Workspace member accountable for the task. */
    private String ownerId;

    /** DRAFT, RUNNING or OFFLINE. */
    private String status;

    /** MANUAL or CRON. */
    private String scheduleType;

    /** Spring six-field Cron expression; null for MANUAL tasks. */
    private String cronExpression;

    /** Next scheduled trigger; null while not scheduled. */
    private LocalDateTime nextFireTime;

    /** Java regex of table names kept alive but not re-crawled. */
    private String excludePattern;

    /** Exact table names kept alive but not re-crawled. */
    private List<String> excludeTables;

    /** Status of the newest run, or null when the task never ran. */
    private String lastRunStatus;

    /** Submission time of the newest run, or null when the task never ran. */
    private LocalDateTime lastRunTime;

    /** Creation timestamp. */
    private LocalDateTime createdTime;

    /** Most recent configuration write timestamp. */
    private LocalDateTime updatedTime;

    /** Authenticated creator identity. */
    private String createBy;

    /** Authenticated identity of the most recent configuration write. */
    private String updateBy;
}
