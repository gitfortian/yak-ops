package io.yak.ops.common.bean.vo.datasync;

import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import lombok.Data;

/**
 * 数据同步 Execution 对外展示的重试策略。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Data
public class DataSyncRetryPolicyVO {

    /** Retry Policy 模式；历史配置缺失时按 FIXED 兼容。 */
    private DataSyncRetryPolicyMode mode;

    /** 最大 Attempt 总数，包含首次执行。 */
    private Integer maxAttempts;

    /** Attempt 失败后的固定等待秒数。 */
    private Integer backoffSeconds;
}
