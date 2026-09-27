package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据同步实例触发方式。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncTriggerType {
    MANUAL(1, "手动"),
    SCHEDULE(2, "调度"),
    RETRY(3, "重试");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
