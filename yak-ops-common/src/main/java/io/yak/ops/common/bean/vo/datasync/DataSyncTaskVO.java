package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Exposes the current data synchronization task definition to clients.
 */
@Data
public class DataSyncTaskVO {

    /** Stable data synchronization task identifier. */
    private String id;

    /** Task name unique within its Workspace. */
    private String name;

    /** Synchronization type: OFFLINE or REALTIME. */
    private String syncType;

    /** Publication status: UNPUBLISHED or PUBLISHED. */
    private String status;

    /** Desired runtime state for REALTIME: STOPPED or RUNNING; always STOPPED for OFFLINE. */
    private String desiredState;

    /** Offline write mode: APPEND, OVERWRITE or UPSERT; REALTIME currently reports APPEND. */
    private String writeMode;

    /** Source datasource identifier. */
    private String sourceDataSourceId;

    /** Source database resolved from the connection or definition; null if unsupported. */
    private String sourceDatabase;

    /** Effective source schema, or null if the provider has no schema level. */
    private String sourceSchema;

    /** Physical source table name. */
    private String sourceTable;

    /** Target datasource identifier. */
    private String targetDataSourceId;

    /** Target database resolved from the connection or definition; null if unsupported. */
    private String targetDatabase;

    /** Effective target schema, or null if the provider has no schema level. */
    private String targetSchema;

    /** Physical target table name. */
    private String targetTable;

    /** YakFlow read, write and timeout settings for OFFLINE; null for REALTIME. */
    private DataSyncRuntimeConfigVO runtimeConfig;

    /** CDC, checkpoint and writer settings for REALTIME; null for OFFLINE. */
    private DataSyncRealtimeConfigVO realtimeConfig;

    /** Fixed retry policy applied after execution failures. */
    private DataSyncRetryPolicyVO retryPolicy;

    /** Current definition version, starting at 1 and incrementing after each successful edit. */
    private Integer definitionVersion;

    /** User-maintained task description. */
    private String remark;

    /** Identifier of the operator who last updated the definition. */
    private String updateBy;

    /** Quartz Cron expression configured for OFFLINE; null when no schedule exists. */
    private String scheduleCronExpression;

    /** Explicit IANA timezone for an OFFLINE schedule; null when no schedule exists. */
    private String scheduleTimeZone;

    /** Whether an OFFLINE schedule is enabled; null when not configured. */
    private Boolean scheduleEnabled;

    /** Task creation timestamp. */
    private LocalDateTime createTime;

    /** Timestamp of the most recent definition update. */
    private LocalDateTime updateTime;
}
