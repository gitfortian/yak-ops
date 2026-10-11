package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Controls how an offline synchronization writes to its destination.
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncWriteMode {

    /** Appends rows without removing existing destination data. */
    APPEND(1, "追加写入"),

    /** Truncates destination data before writing the full synchronized dataset. */
    OVERWRITE(2, "覆盖写入"),

    /** Updates existing destination rows by primary key and inserts missing rows. */
    UPSERT(3, "更新写入");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
