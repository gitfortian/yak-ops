package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * REALTIME Task 的期望运行状态，用于区分用户主动停止与进程异常中断。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncDesiredState {
    STOPPED(0, "已停止"),
    RUNNING(1, "运行中");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
