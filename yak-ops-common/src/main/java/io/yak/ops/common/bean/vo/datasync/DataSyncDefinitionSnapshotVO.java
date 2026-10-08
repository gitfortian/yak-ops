package io.yak.ops.common.bean.vo.datasync;

import java.util.List;
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

    /**
     * 兼容旧 Runtime 的首 Route 自动建表投影；新的表级定义以 tableRoutes 为准。
     */
    private Boolean autoCreateTable;

    /**
     * 兼容旧 Runtime 的首 Route Mapping 投影；新的表级定义以 tableRoutes 为准。
     */
    private DataSyncMappingVO mapping;

    /** Root Execution 创建时按 Route 顺序冻结的全部表级定义。 */
    private List<DataSyncTableRouteSnapshotVO> tableRoutes;

    /** Execution 创建时固化的 Retry Policy；所有 Attempt 共用。 */
    private DataSyncRetryPolicyVO retryPolicy;

    /**
     * 兼容旧 Runtime 的首 Route Effective Runtime Config；新的 OFFLINE 表级配置以 tableRoutes 为准。
     */
    private DataSyncRuntimeConfigVO runtimeConfig;

    /**
     * 兼容旧 Runtime 的首 Route AUTO 规划摘要；新的 OFFLINE 表级规划以 tableRoutes 为准。
     */
    private DataSyncOfflineRuntimePlanVO offlineRuntimePlan;

    /** REALTIME 实例启动时固化的 CDC / Checkpoint 参数；OFFLINE 实例为空。 */
    private DataSyncRealtimeConfigVO realtimeConfig;
}
