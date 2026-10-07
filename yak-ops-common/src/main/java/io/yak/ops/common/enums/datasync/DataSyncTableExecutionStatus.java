package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Data Sync Table Execution 生命周期状态。
 *
 * <p>PR2 只创建 PLANNED；PENDING 及后续 Runtime / Retry 状态由 PR3 接管。</p>
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncTableExecutionStatus {
    PLANNED(0, "已规划"),
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
