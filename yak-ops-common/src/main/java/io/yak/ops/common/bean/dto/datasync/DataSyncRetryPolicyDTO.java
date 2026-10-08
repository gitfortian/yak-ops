package io.yak.ops.common.bean.dto.datasync;

import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据同步 Execution 的重试策略。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Data
public class DataSyncRetryPolicyDTO {

    /** Retry Policy 模式；历史配置缺失时按 FIXED 兼容。 */
    private DataSyncRetryPolicyMode mode;

    /** 最大 Attempt 总数，包含首次执行；1 表示关闭自动重试。 */
    @NotNull(message = "最大 Attempt 次数不能为空")
    @Min(value = 1, message = "最大 Attempt 次数不能小于 1")
    @Max(value = 10, message = "最大 Attempt 次数不能超过 10")
    private Integer maxAttempts = 1;

    /** Attempt 失败后的固定等待秒数。 */
    @NotNull(message = "Retry Backoff 不能为空")
    @Min(value = 0, message = "Retry Backoff 不能小于 0")
    @Max(value = 3600, message = "Retry Backoff 不能超过 3600 秒")
    private Integer backoffSeconds = 60;
}
