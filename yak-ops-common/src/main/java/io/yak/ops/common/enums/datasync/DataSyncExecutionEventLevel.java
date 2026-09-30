package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据同步 Execution 产品事件级别。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncExecutionEventLevel {
    INFO(1, "信息"),
    WARN(2, "警告"),
    ERROR(3, "错误");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
