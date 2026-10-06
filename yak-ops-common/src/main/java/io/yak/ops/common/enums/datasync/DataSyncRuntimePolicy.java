package io.yak.ops.common.enums.datasync;

/**
 * 数据同步运行参数的规划策略。
 *
 * @author weifuwan
 * @since 2026-10-06
 */
public enum DataSyncRuntimePolicy {

    /** Execution 创建时根据当前 Source / Schema 计算有效运行参数。 */
    AUTO,

    /** 直接使用 Task 已保存的具体运行参数。 */
    FIXED
}
