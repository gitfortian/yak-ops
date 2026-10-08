package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 运维中心 Data Sync 时间趋势单个时间桶。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsTrendPointVO {

    /** 时间桶开始时间；TODAY 按小时，其余范围按天。 */
    private LocalDateTime bucketStart;

    /** 时间桶内创建的 Execution 数量。 */
    private Long executionCount;

    /** 时间桶内当前状态为 SUCCEEDED 的 Execution 数量。 */
    private Long succeededCount;

    /** 时间桶内当前状态为 FAILED 的 Execution 数量。 */
    private Long failedCount;

    /** 时间桶内当前状态为 LOST 的 Execution 数量。 */
    private Long lostCount;

    /** 时间桶内 AUTO_RECOVERY 创建的 Execution 数量。 */
    private Long autoRecoveryCount;

    /** 时间桶内 Execution 当前或最终 Attempt 镜像的读取计数之和。 */
    private Long readRows;

    /** 时间桶内 Execution 当前或最终 Attempt 镜像的写入计数之和。 */
    private Long writeRows;

    /** 时间桶内完成 Execution 的平均耗时，单位毫秒。 */
    private Long averageDurationMillis;
}
