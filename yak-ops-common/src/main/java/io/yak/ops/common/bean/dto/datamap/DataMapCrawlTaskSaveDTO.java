package io.yak.ops.common.bean.dto.datamap;

import io.yak.ops.common.enums.datamap.DataMapCrawlScheduleType;
import io.yak.ops.common.enums.datamap.DataMapCrawlTaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Creates or replaces one metadata collection task configuration. */
@Getter
@Setter
public class DataMapCrawlTaskSaveDTO {

    /** Operator-visible task name. */
    @NotBlank
    @Size(max = 128)
    private String name;

    /** Datasource the task collects metadata from. */
    @NotBlank
    @Size(max = 64)
    private String datasourceId;

    /** Catalog slot to collect. */
    @NotBlank
    @Size(max = 128)
    private String database;

    /** Schema slot to collect; null or blank uses the datasource binding. */
    @Size(max = 128)
    private String schema;

    /** Workspace member accountable for the task. */
    @NotBlank
    @Size(max = 64)
    private String ownerId;

    /** Trigger mode; CRON requires a cron expression. */
    @NotNull
    private DataMapCrawlScheduleType scheduleType;

    /** Spring six-field Cron expression, required for CRON tasks. */
    @Size(max = 128)
    private String cronExpression;

    /** Java regex of table names kept alive but not re-crawled. */
    @Size(max = 256)
    private String excludePattern;

    /** Exact table names kept alive but not re-crawled. */
    @Size(max = 500)
    private List<@Size(max = 128) String> excludeTables;

    /**
     * Status to save as: DRAFT or RUNNING. Saving a running or offline task as DRAFT takes it
     * offline, because a draft never holds a schedule.
     */
    @NotNull
    private DataMapCrawlTaskStatus status;
}
