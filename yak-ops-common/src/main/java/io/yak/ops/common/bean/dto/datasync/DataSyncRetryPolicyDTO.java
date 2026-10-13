package io.yak.ops.common.bean.dto.datasync;

import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Defines retry settings for a data synchronization execution.
 */
@Data
public class DataSyncRetryPolicyDTO {

    /** Retry policy mode; an absent value resolves to FIXED. */
    private DataSyncRetryPolicyMode mode;

    /** Maximum attempt count including the first execution; 1 disables automatic retries. */
    @NotNull(message = "最大 Attempt 次数不能为空")
    @Min(value = 1, message = "最大 Attempt 次数不能小于 1")
    @Max(value = 10, message = "最大 Attempt 次数不能超过 10")
    private Integer maxAttempts = 1;

    /** Fixed delay after a failed attempt, in seconds. */
    @NotNull(message = "Retry Backoff 不能为空")
    @Min(value = 0, message = "Retry Backoff 不能小于 0")
    @Max(value = 3600, message = "Retry Backoff 不能超过 3600 秒")
    private Integer backoffSeconds = 60;
}
