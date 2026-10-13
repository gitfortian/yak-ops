package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Distinguishes bounded offline synchronization from continuous realtime synchronization.
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncType {

    /** Bounded offline data synchronization. */
    OFFLINE(1, "离线同步"),

    /** Continuous realtime synchronization based on MySQL CDC. */
    REALTIME(2, "实时同步");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
