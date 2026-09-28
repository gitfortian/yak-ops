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

    /** 实例启动时固化的来源数据源与表路径，不包含连接凭证。 */
    private DataSyncEndpointSnapshotVO source;

    /** 实例启动时固化的目标数据源与表路径，不包含连接凭证。 */
    private DataSyncEndpointSnapshotVO target;

    /** 实例启动时固化的 YakFlow 运行参数。 */
    private DataSyncRuntimeConfigVO runtimeConfig;
}
