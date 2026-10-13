package io.yak.ops.common.bean.dto.datasync;

import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Carries YakFlow runtime settings for an offline synchronization definition.
 */
@Data
public class DataSyncRuntimeConfigDTO {

    /** Runtime planning policy; an absent value resolves to FIXED. */
    private DataSyncRuntimePolicy policy;

    /** JDBC cursor fetch size. */
    @NotNull(message = "Fetch Size 不能为空")
    @Min(value = 1, message = "Fetch Size 必须大于 0")
    @Max(value = 100000, message = "Fetch Size 不能超过 100000")
    private Integer fetchSize = 500;

    /** Maximum rows emitted by the Source in one runtime batch. */
    @NotNull(message = "读取批次大小不能为空")
    @Min(value = 1, message = "读取批次大小必须大于 0")
    @Max(value = 100000, message = "读取批次大小不能超过 100000")
    private Integer readBatchSize = 500;

    /** Maximum rows written by a Sink commit batch. */
    @NotNull(message = "写入批次大小不能为空")
    @Min(value = 1, message = "写入批次大小必须大于 0")
    @Max(value = 100000, message = "写入批次大小不能超过 100000")
    private Integer writeBatchSize = 500;

    /** Target row count for dynamic range splitting; null disables automatic splitting. */
    @Min(value = 1, message = "Split Size 必须大于 0")
    @Max(value = 10000000, message = "Split Size 不能超过 10000000")
    private Long splitSize;

    /** Parallelism of the bounded Source Reader. */
    @NotNull(message = "Source 并行度不能为空")
    @Min(value = 1, message = "Source 并行度必须大于 0")
    @Max(value = 16, message = "Source 并行度不能超过 16")
    private Integer sourceParallelism = 1;

    /** JDBC connection and statement timeout, in seconds. */
    @NotNull(message = "超时时间不能为空")
    @Min(value = 1, message = "超时时间必须大于 0")
    @Max(value = 600, message = "超时时间不能超过 600 秒")
    private Integer timeoutSeconds = 30;
}
