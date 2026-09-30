package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据同步 Execution 面向产品展示的生命周期事件类型。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncExecutionEventType {
    EXECUTION_STARTED(1, "Execution 开始执行"),
    ATTEMPT_STARTED(2, "Attempt 开始执行"),
    SOURCE_READY(3, "来源准备完成"),
    TARGET_READY(4, "目标准备完成"),
    ATTEMPT_SUCCEEDED(5, "Attempt 执行成功"),
    ATTEMPT_FAILED(6, "Attempt 执行失败"),
    RETRY_WAITING(7, "等待重试"),
    EXECUTION_SUCCEEDED(8, "Execution 执行成功"),
    EXECUTION_FAILED(9, "Execution 执行失败"),
    EXECUTION_CANCELED(10, "Execution 已取消"),
    EXECUTION_LOST(11, "Execution 已丢失"),
    AUTO_RECOVERY_STARTED(12, "自动恢复已创建 Execution");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
