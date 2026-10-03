package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 离线同步 Attempt Runtime Trace 汇总。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
@Data
public class DataSyncTraceSummaryVO {

    /** Attempt 序号。 */
    private Integer attemptNo;

    /** 当前 Attempt 是否存在 Runtime Trace。 */
    private Boolean available;

    /** Trace 是否已经完成收口。 */
    private Boolean complete;

    /** Source 规划出的 Split 数量。 */
    private Long sourceSplitCount;

    /** Source 成功完成的 Split 数量。 */
    private Long sourceFinishedSplitCount;

    /** Source 失败的 Split 数量。 */
    private Long sourceFailedSplitCount;

    /** Source Trace 观察到的累计读取行数。 */
    private Long sourceRows;

    /** 所有终态 Split 的读取耗时之和，非 Execution 墙钟耗时。 */
    private Long sourceSplitDurationMillis;

    /** Sink SQL 模板。 */
    private String sinkSql;

    /** Sink 配置的 Batch Size。 */
    private Integer sinkBatchSize;

    /** Sink Save Mode。 */
    private String sinkSaveMode;

    /** Sink Write Mode。 */
    private String sinkWriteMode;

    /** Sink 成功提交的 Batch 数量。 */
    private Long sinkCommittedBatchCount;

    /** Sink 失败的 Batch 数量。 */
    private Long sinkFailedBatchCount;

    /** Sink Trace 观察到的累计批次行数。 */
    private Long sinkRows;

    /** Sink executeBatch 累计耗时。 */
    private Long sinkExecuteDurationMillis;

    /** Sink commit 累计耗时。 */
    private Long sinkCommitDurationMillis;

    /** Runtime Trace 中的失败事件数量。 */
    private Long errorCount;

    /** 因 Trace 队列拥塞或 Trace Store 故障丢弃的事件数量。 */
    private Long droppedEventCount;
}
