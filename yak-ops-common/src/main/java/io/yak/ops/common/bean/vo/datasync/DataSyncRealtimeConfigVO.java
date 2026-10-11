package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * Exposes YakFlow runtime settings of a realtime synchronization definition.
 */
@Data
public class DataSyncRealtimeConfigVO {

    /** Automatic checkpoint interval for the continuous source, in seconds. */
    private Integer checkpointIntervalSeconds;

    /** Bounded queue capacity between Debezium batches and SourceReader. */
    private Integer queueCapacity;

    /** Maximum change events emitted per SourceReader poll. */
    private Integer pollBatchSize;

    /** Maximum JDBC Changelog Sink events per commit batch. */
    private Integer writeBatchSize;

    /** CDC Source and JDBC Sink connection and statement timeout, in seconds. */
    private Integer timeoutSeconds;
}
