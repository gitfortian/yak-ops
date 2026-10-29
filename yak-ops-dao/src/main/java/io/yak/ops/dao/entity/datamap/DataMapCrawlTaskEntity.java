package io.yak.ops.dao.entity.datamap;

import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persists one metadata collection task: configuration and lifecycle, separate from its runs.
 *
 * <p>The schedule is evaluated by the application scheduler, so {@code nextFireTime} is durable
 * desired state rather than an engine-owned trigger. Excluded tables are stored as a JSON array to
 * keep the whole configuration on one row, because it is always replaced as a set.
 */
@Getter
@Setter
public class DataMapCrawlTaskEntity extends BaseEntity {
    /** Workspace owning the task. */
    private String workspaceId;
    /** Operator-visible task name. */
    private String name;
    /** Datasource the task collects metadata from. */
    private String datasourceId;
    /** Catalog slot collected by the task; empty when the engine has none. */
    private String databaseName;
    /** Schema slot collected by the task; empty when the engine has none. */
    private String schemaName;
    /** Workspace member accountable for the task. */
    private String ownerId;
    /** Lifecycle status: DRAFT, RUNNING or OFFLINE. */
    private String status;
    /** Trigger mode: MANUAL or CRON. */
    private String scheduleType;
    /** Spring six-field Cron expression; null for MANUAL tasks. */
    private String cronExpression;
    /** Next scheduled trigger; null while not scheduled. */
    private LocalDateTime nextFireTime;
    /** Java regex of table names kept alive but not re-crawled. */
    private String excludePattern;
    /** JSON array of table names kept alive but not re-crawled. */
    private String excludeTables;
}
