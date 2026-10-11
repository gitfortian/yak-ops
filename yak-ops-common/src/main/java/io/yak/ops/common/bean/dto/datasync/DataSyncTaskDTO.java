package io.yak.ops.common.bean.dto.datasync;

import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Defines the create or update request for a data synchronization task.
 */
@Data
public class DataSyncTaskDTO {

    /** Task name, unique within its Workspace. */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称不能超过 128 个字符")
    private String name;

    /** Synchronization type: OFFLINE or REALTIME. */
    @NotNull(message = "同步类型不能为空")
    private DataSyncType syncType = DataSyncType.OFFLINE;

    /** Offline target write mode; only APPEND is currently exposed at this boundary. */
    @NotNull(message = "写入方式不能为空")
    private DataSyncWriteMode writeMode = DataSyncWriteMode.APPEND;

    /** Source datasource identifier. */
    @NotBlank(message = "来源数据源不能为空")
    private String sourceDataSourceId;

    /** Source database name, or null when this level does not exist. */
    @Size(max = 128, message = "来源数据库名称不能超过 128 个字符")
    private String sourceDatabase;

    /** Source schema name, or null when this level does not exist. */
    @Size(max = 128, message = "来源 Schema 名称不能超过 128 个字符")
    private String sourceSchema;

    /** Physical source table name. */
    @NotBlank(message = "来源表不能为空")
    @Size(max = 128, message = "来源表名称不能超过 128 个字符")
    private String sourceTable;

    /** Target datasource identifier. */
    @NotBlank(message = "目标数据源不能为空")
    private String targetDataSourceId;

    /** Target database name, or null when this level does not exist. */
    @Size(max = 128, message = "目标数据库名称不能超过 128 个字符")
    private String targetDatabase;

    /** Target schema name, or null when this level does not exist. */
    @Size(max = 128, message = "目标 Schema 名称不能超过 128 个字符")
    private String targetSchema;

    /** Physical target table name. */
    @NotBlank(message = "目标表不能为空")
    @Size(max = 128, message = "目标表名称不能超过 128 个字符")
    private String targetTable;

    /**
 * YakFlow runtime settings for OFFLINE tasks.
 *
 * <p>Optional on create (server defaults are materialized); omission on update retains the saved settings.
 */
    @Valid
    private DataSyncRuntimeConfigDTO runtimeConfig;

    /**
 * YakFlow CDC settings for REALTIME tasks.
 *
 * <p>Optional on create (server defaults are materialized); omission on update retains the saved settings.
 */
    @Valid
    private DataSyncRealtimeConfigDTO realtimeConfig;

    /**
 * Fixed retry policy after execution failure.
 *
 * <p>Optional on create (server defaults are materialized); omission on update retains the saved policy.
 */
    @Valid
    private DataSyncRetryPolicyDTO retryPolicy;

    /** Optional user-maintained task description. */
    @Size(max = 500, message = "任务备注不能超过 500 个字符")
    private String remark;
}
