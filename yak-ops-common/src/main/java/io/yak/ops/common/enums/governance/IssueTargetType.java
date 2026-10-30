package io.yak.ops.common.enums.governance;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Kind of object a rule accuses. The target is polymorphic across two owning modules. */
@Getter
@RequiredArgsConstructor
public enum IssueTargetType {
    DATA_MAP_TABLE("DATA_MAP_TABLE"),
    TASK_DEFINITION("TASK_DEFINITION");

    @EnumValue
    private final String value;
}
