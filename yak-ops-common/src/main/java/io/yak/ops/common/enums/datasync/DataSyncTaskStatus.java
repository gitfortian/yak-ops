package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the publication state of a synchronization definition.
 *
 * <p>Persistence uses UNPUBLISHED and PUBLISHED independently of the OFFLINE synchronization type.
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncTaskStatus {

    /** An unpublished definition cannot create new execution instances. */
    UNPUBLISHED(0, "已下线"),

    /** A published definition may create new execution instances. */
    PUBLISHED(1, "已上线");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
