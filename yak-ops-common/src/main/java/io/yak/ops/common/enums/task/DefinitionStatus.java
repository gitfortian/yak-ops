package io.yak.ops.common.enums.task;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents Task Definition publication independently of execution state and sync type.
 */
@Getter
@RequiredArgsConstructor
public enum DefinitionStatus {

    /** An unpublished definition cannot create new execution instances. */
    UNPUBLISHED(0),

    /** A published definition can execute according to its plugin rules. */
    PUBLISHED(1);

    @EnumValue
    private final Integer value;
}
