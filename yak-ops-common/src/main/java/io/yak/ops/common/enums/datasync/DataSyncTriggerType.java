package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据同步 Execution 根触发方式；Retry Attempt 不应覆盖根 Execution 的触发来源。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncTriggerType {
    MANUAL(1, "手动"),
    SCHEDULE(2, "调度"),

    /**
     * 兼容保留值。v1.1 Retry / Attempt Contract 不再使用 RETRY 创建新的 Execution Root。
     */
    RETRY(3, "重试"),
    AUTO_RECOVERY(4, "自动恢复");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
