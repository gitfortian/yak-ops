package io.yak.ops.common.enums.datasync;

/**
 * Data Sync Retry Policy 的系统策略模式。
 *
 * @author weifuwan
 * @since 2026-10-06
 */
public enum DataSyncRetryPolicyMode {

    /** 由系统判定错误是否值得重试，并应用写入安全边界。 */
    SMART,

    /** 按显式 maxAttempts / backoffSeconds 重试，保留历史兼容语义。 */
    FIXED
}
