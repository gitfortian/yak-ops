package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * Operations Center 使用的数据同步任务运行态读模型。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncTaskOperationVO {

    /** Task ID。 */
    private String id;

    /** Task 名称。 */
    private String name;

    /** OFFLINE / REALTIME。 */
    private String syncType;

    /** REALTIME 期望运行状态；OFFLINE 固定 STOPPED。 */
    private String desiredState;

    /** 当前任务定义版本。 */
    private Integer definitionVersion;

    /** Execution Retry Policy。 */
    private DataSyncRetryPolicyVO retryPolicy;

    /** 当前最新 Execution；无运行历史时为空。 */
    private DataSyncInstanceVO latestInstance;

    /** OFFLINE Schedule Runtime；REALTIME 或未配置 Schedule 时为空。 */
    private DataSyncScheduleVO schedule;
}
