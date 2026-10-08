package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * Execution 创建时冻结的一条 Data Sync Table Route。
 *
 * <p>只保存非敏感产品定义，不包含数据源连接凭证、Token、SSH 私钥或 Runtime lease。</p>
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Data
public class DataSyncTableRouteSnapshotVO {

    /** 稳定 Route ID。 */
    private String routeId;

    /** Task 内冻结的 Route 顺序，从 0 开始。 */
    private Integer sortOrder;

    /** 冻结的来源数据源与物理表路径。 */
    private DataSyncEndpointSnapshotVO source;

    /** 冻结的目标数据源与物理表路径。 */
    private DataSyncEndpointSnapshotVO target;

    /** 目标表缺失时是否允许当前 Route 自动建表。 */
    private Boolean autoCreateTable;

    /** 当前 Route 冻结的字段映射；为空表示沿用大小写不敏感同名映射。 */
    private DataSyncMappingVO mapping;

    /** OFFLINE Route 冻结的 Effective Runtime Config；REALTIME Route 为空。 */
    private DataSyncRuntimeConfigVO runtimeConfig;

    /** OFFLINE AUTO Route 冻结的运行规划摘要；固定策略或 REALTIME Route 为空。 */
    private DataSyncOfflineRuntimePlanVO offlineRuntimePlan;
}
