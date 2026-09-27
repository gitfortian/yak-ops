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

    private String taskId;

    private String taskName;

    private Integer taskVersion;

    private DataSyncEndpointSnapshotVO source;

    private DataSyncEndpointSnapshotVO target;

    private DataSyncRuntimeConfigVO runtimeConfig;
}
