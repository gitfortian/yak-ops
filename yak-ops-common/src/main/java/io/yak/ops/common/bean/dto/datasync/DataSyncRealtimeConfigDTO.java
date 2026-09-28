package io.yak.ops.common.bean.dto.datasync;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 实时同步任务的 YakFlow 运行参数。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Data
public class DataSyncRealtimeConfigDTO {

    /** 连续 Source 自动 Checkpoint 周期，单位秒。 */
    @NotNull(message = "Checkpoint 周期不能为空")
    @Min(value = 1, message = "Checkpoint 周期必须大于 0")
    @Max(value = 3600, message = "Checkpoint 周期不能超过 3600 秒")
    private Integer checkpointIntervalSeconds = 10;

    /** Debezium batch 到 SourceReader 的有界队列容量。 */
    @NotNull(message = "CDC 队列容量不能为空")
    @Min(value = 1, message = "CDC 队列容量必须大于 0")
    @Max(value = 100000, message = "CDC 队列容量不能超过 100000")
    private Integer queueCapacity = 64;

    /** 每次 SourceReader.poll 最多输出的变更事件数。 */
    @NotNull(message = "CDC 读取批次大小不能为空")
    @Min(value = 1, message = "CDC 读取批次大小必须大于 0")
    @Max(value = 100000, message = "CDC 读取批次大小不能超过 100000")
    private Integer pollBatchSize = 500;

    /** JDBC Changelog Sink 每次提交前最多累计的变更事件数。 */
    @NotNull(message = "CDC 写入批次大小不能为空")
    @Min(value = 1, message = "CDC 写入批次大小必须大于 0")
    @Max(value = 100000, message = "CDC 写入批次大小不能超过 100000")
    private Integer writeBatchSize = 500;

    /** CDC Source 与 JDBC Sink 的连接、语句超时秒数。 */
    @NotNull(message = "超时时间不能为空")
    @Min(value = 1, message = "超时时间必须大于 0")
    @Max(value = 600, message = "超时时间不能超过 600 秒")
    private Integer timeoutSeconds = 30;
}
