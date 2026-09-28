package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据同步任务类型。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncType {

    /** 有界离线同步任务。 */
    OFFLINE(1, "离线同步"),

    /** MySQL CDC 持续实时同步任务。 */
    REALTIME(2, "实时同步");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
