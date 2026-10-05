package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 数据同步实例启动时固化的脱敏任务定义快照。
 *
 * <p>该对象禁止包含密码、connectionParams、originalJson、SSH 私钥或任何 Token。</p>
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncDefinitionSnapshotVO {

    /** 产生该实例的任务 ID。 */
    private String taskId;

    /** 实例启动时固化的任务名称。 */
    private String taskName;

    /** 实例启动时采用的任务定义版本。 */
    private Integer taskVersion;

    /** 实例启动时固化的同步类型：OFFLINE 或 REALTIME。 */
    private String syncType;

    /** 实例启动时固化的写入方式；REALTIME 当前固定为 APPEND。 */
    private String writeMode;

    /** 实例启动时固化的来源数据源与表路径，不包含连接凭证。 */
    private DataSyncEndpointSnapshotVO source;

    /** 实例启动时固化的目标数据源与表路径，不包含连接凭证。 */
    private DataSyncEndpointSnapshotVO target;

    /** Execution 创建时固化的目标表自动创建策略。 */
    private Boolean autoCreateTable;

    /** Execution 创建时固化的任务级字段映射；为空表示沿用系统默认同名映射。 */
    private DataSyncMappingVO mapping;

    /** Execution 创建时固化的 Retry Policy；所有 Attempt 共用。 */
    private DataSyncRetryPolicyVO retryPolicy;

    /** OFFLINE 实例启动时固化的 YakFlow 运行参数；REALTIME 实例为空。 */
    private DataSyncRuntimeConfigVO runtimeConfig;

    /** REALTIME 实例启动时固化的 CDC / Checkpoint 参数；OFFLINE 实例为空。 */
    private DataSyncRealtimeConfigVO realtimeConfig;
}
