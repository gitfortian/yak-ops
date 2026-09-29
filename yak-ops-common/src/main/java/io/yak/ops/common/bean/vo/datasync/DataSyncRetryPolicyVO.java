package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 数据同步 Execution 对外展示的固定重试策略。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Data
public class DataSyncRetryPolicyVO {

    /** 最大 Attempt 总数，包含首次执行。 */
    private Integer maxAttempts;

    /** Attempt 失败后的固定等待秒数。 */
    private Integer backoffSeconds;
}
