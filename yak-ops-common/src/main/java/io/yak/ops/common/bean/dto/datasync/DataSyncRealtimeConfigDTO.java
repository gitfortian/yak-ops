package io.yak.ops.common.bean.dto.datasync;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Carries YakFlow runtime parameters for a realtime synchronization definition.
 */
@Data
public class DataSyncRealtimeConfigDTO {

    /** Automatic checkpoint interval for the continuous source, in seconds. */
    @NotNull(message = "Checkpoint 周期不能为空")
    @Min(value = 1, message = "Checkpoint 周期必须大于 0")
    @Max(value = 3600, message = "Checkpoint 周期不能超过 3600 秒")
    private Integer checkpointIntervalSeconds = 10;

    /** Bounded queue capacity between Debezium batches and SourceReader. */
    @NotNull(message = "CDC 队列容量不能为空")
    @Min(value = 1, message = "CDC 队列容量必须大于 0")
    @Max(value = 100000, message = "CDC 队列容量不能超过 100000")
    private Integer queueCapacity = 64;

    /** Maximum change events emitted by each SourceReader poll. */
    @NotNull(message = "CDC 读取批次大小不能为空")
    @Min(value = 1, message = "CDC 读取批次大小必须大于 0")
    @Max(value = 100000, message = "CDC 读取批次大小不能超过 100000")
    private Integer pollBatchSize = 500;

    /** Maximum change events accumulated before each JDBC Changelog Sink commit. */
    @NotNull(message = "CDC 写入批次大小不能为空")
    @Min(value = 1, message = "CDC 写入批次大小必须大于 0")
    @Max(value = 100000, message = "CDC 写入批次大小不能超过 100000")
    private Integer writeBatchSize = 500;

    /** Connection and statement timeout for the CDC Source and JDBC Sink, in seconds. */
    @NotNull(message = "超时时间不能为空")
    @Min(value = 1, message = "超时时间必须大于 0")
    @Max(value = 600, message = "超时时间不能超过 600 秒")
    private Integer timeoutSeconds = 30;
}
