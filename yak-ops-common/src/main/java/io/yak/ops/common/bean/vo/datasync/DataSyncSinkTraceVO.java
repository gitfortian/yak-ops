package io.yak.ops.common.bean.vo.datasync;

import java.time.Instant;
import lombok.Data;

/**
 * 离线同步 Sink Batch 终态诊断记录。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
@Data
public class DataSyncSinkTraceVO {

    /** 事件时间。 */
    private Instant timestamp;

    /** Writer 内单调递增的 Batch 序号。 */
    private Long batchNo;

    /** 当前批次行数。 */
    private Long rows;

    /** executeBatch 耗时。 */
    private Long executeDurationMillis;

    /** commit 耗时。 */
    private Long commitDurationMillis;

    /** SUCCESS / FAILED。 */
    private String status;

    /** 失败阶段。 */
    private String failureStage;

    /** 异常类型。 */
    private String errorType;

    /** 已脱敏的异常消息。 */
    private String errorMessage;
}
