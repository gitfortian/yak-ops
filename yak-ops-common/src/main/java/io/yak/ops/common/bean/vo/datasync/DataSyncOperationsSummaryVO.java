package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 运维中心 Data Sync 聚合指标摘要。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsSummaryVO {

    /** 查询范围内创建的 Execution 数量。 */
    private Long executionCount;

    /** 查询范围内当前状态为 SUCCEEDED 的 Execution 数量。 */
    private Long succeededCount;

    /** 查询范围内当前状态为 FAILED 的 Execution 数量。 */
    private Long failedCount;

    /** 查询范围内当前状态为 LOST 的 Execution 数量。 */
    private Long lostCount;

    /** 查询范围内出现 FAILED / LOST Execution 的去重 Task 数量。 */
    private Long abnormalTaskCount;

    /** 当前仍有 PENDING / RUNNING / RETRY_WAITING Execution 的去重 Task 数量，不受查询范围限制。 */
    private Long currentActiveTaskCount;

    /** 查询范围内 AUTO_RECOVERY 创建的 Execution 数量。 */
    private Long autoRecoveryCount;

    /** 查询范围内 Execution 当前或最终 Attempt 镜像的读取计数之和。 */
    private Long readRows;

    /** 查询范围内 Execution 当前或最终 Attempt 镜像的写入计数之和。 */
    private Long writeRows;

    /** 查询范围内存在 startTime / finishTime 的 Execution 平均耗时，单位毫秒。 */
    private Long averageDurationMillis;
}
