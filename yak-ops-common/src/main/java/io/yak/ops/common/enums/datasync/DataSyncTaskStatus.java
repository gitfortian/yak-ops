package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据同步任务发布状态。
 *
 * <p>产品层文案使用“已下线 / 已上线”，持久化枚举使用 UNPUBLISHED / PUBLISHED，避免与
 * {@link DataSyncType#OFFLINE} 的同步类型语义混淆。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncTaskStatus {

    /** 当前任务定义未上线，不允许创建新的执行实例。 */
    UNPUBLISHED(0, "已下线"),

    /** 当前任务定义已上线，允许创建新的执行实例。 */
    PUBLISHED(1, "已上线");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
