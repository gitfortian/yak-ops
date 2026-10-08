package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据同步实例生命周期状态。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncInstanceStatus {
    PENDING(1, "等待"),
    RUNNING(2, "运行中"),
    SUCCEEDED(3, "成功"),
    FAILED(4, "失败"),
    CANCELED(5, "已取消"),
    LOST(6, "丢失"),
    RETRY_WAITING(7, "等待重试");

    @EnumValue
    private final Integer value;

    private final String displayName;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == LOST;
    }
}
