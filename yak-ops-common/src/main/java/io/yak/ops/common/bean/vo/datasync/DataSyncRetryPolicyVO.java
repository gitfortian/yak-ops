package io.yak.ops.common.bean.vo.datasync;

import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import lombok.Data;

/**
 * Exposes the retry policy of a data synchronization execution.
 */
@Data
public class DataSyncRetryPolicyVO {

    /** Retry policy mode; an absent value resolves to FIXED. */
    private DataSyncRetryPolicyMode mode;

    /** Maximum attempt count, including the initial execution. */
    private Integer maxAttempts;

    /** Fixed delay between attempts after failure, in seconds. */
    private Integer backoffSeconds;
}
