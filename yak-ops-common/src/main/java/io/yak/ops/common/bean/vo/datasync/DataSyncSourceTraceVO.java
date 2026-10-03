package io.yak.ops.common.bean.vo.datasync;

import java.time.Instant;
import java.util.List;
import lombok.Data;

/**
 * 离线同步 Source Split 终态诊断记录。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
@Data
public class DataSyncSourceTraceVO {

    /** 事件时间。 */
    private Instant timestamp;

    /** Split 稳定标识。 */
    private String splitId;

    /** Reader Worker 名称。 */
    private String workerName;

    /** Connector 生成的 SELECT SQL 模板。 */
    private String sql;

    /** Connector 生成的数值 Split 边界参数。 */
    private List<Long> parameters;

    /** 数值 Split 字段。 */
    private String splitColumn;

    /** 范围最小包含值。 */
    private Long lowerBoundInclusive;

    /** 范围最大包含值。 */
    private Long upperBoundInclusive;

    /** 当前 Split 读取行数。 */
    private Long rows;

    /** 当前 Split 从 Reader open 到结束的总耗时。 */
    private Long durationMillis;

    /** SUCCESS / FAILED。 */
    private String status;

    /** 失败阶段。 */
    private String failureStage;

    /** 异常类型。 */
    private String errorType;

    /** 已脱敏的异常消息。 */
    private String errorMessage;
}
