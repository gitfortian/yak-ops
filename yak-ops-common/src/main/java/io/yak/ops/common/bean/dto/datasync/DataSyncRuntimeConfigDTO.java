package io.yak.ops.common.bean.dto.datasync;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 离线同步任务的 YakFlow 运行参数。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncRuntimeConfigDTO {

    /** JDBC 游标 Fetch Size。 */
    @NotNull(message = "Fetch Size 不能为空")
    @Min(value = 1, message = "Fetch Size 必须大于 0")
    @Max(value = 100000, message = "Fetch Size 不能超过 100000")
    private Integer fetchSize = 500;

    /** Source 每次向 Runtime 输出的最大行数。 */
    @NotNull(message = "读取批次大小不能为空")
    @Min(value = 1, message = "读取批次大小必须大于 0")
    @Max(value = 100000, message = "读取批次大小不能超过 100000")
    private Integer readBatchSize = 500;

    /** Sink 每次提交的最大行数。 */
    @NotNull(message = "写入批次大小不能为空")
    @Min(value = 1, message = "写入批次大小必须大于 0")
    @Max(value = 100000, message = "写入批次大小不能超过 100000")
    private Integer writeBatchSize = 500;

    /** JDBC 连接与语句超时秒数。 */
    @NotNull(message = "超时时间不能为空")
    @Min(value = 1, message = "超时时间必须大于 0")
    @Max(value = 600, message = "超时时间不能超过 600 秒")
    private Integer timeoutSeconds = 30;
}
