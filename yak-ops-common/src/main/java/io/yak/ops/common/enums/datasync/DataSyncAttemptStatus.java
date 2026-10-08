package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据同步 Execution 内单次 Attempt 生命周期状态。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncAttemptStatus {
    PENDING(1, "等待"),
    RUNNING(2, "运行中"),
    SUCCEEDED(3, "成功"),
    FAILED(4, "失败"),
    CANCELED(5, "已取消"),
    LOST(6, "丢失");

    @EnumValue
    private final Integer value;

    private final String displayName;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == LOST;
    }
}
