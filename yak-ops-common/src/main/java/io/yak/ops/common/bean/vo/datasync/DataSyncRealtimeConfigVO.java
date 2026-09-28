package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 实时同步任务对外展示的 YakFlow 运行参数。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Data
public class DataSyncRealtimeConfigVO {

    /** 连续 Source 自动 Checkpoint 周期，单位秒。 */
    private Integer checkpointIntervalSeconds;

    /** Debezium batch 到 SourceReader 的有界队列容量。 */
    private Integer queueCapacity;

    /** 每次 SourceReader.poll 最多输出的变更事件数。 */
    private Integer pollBatchSize;

    /** JDBC Changelog Sink 每次提交前最多累计的变更事件数。 */
    private Integer writeBatchSize;

    /** CDC Source 与 JDBC Sink 的连接、语句超时秒数。 */
    private Integer timeoutSeconds;
}
