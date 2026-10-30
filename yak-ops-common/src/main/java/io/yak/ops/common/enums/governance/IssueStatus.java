package io.yak.ops.common.enums.governance;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Current state of a governance Issue. Status is mutable state, never part of its identity. */
@Getter
@RequiredArgsConstructor
public enum IssueStatus {
    OPEN("OPEN"),
    HANDLING("HANDLING"),
    RESOLVED("RESOLVED"),
    IGNORED("IGNORED");

    @EnumValue
    private final String value;

    /** Whether the Issue counts towards the score of its dimension. */
    public boolean isScored() {
        return this == OPEN || this == HANDLING;
    }

    /** Whether a member may assign the Issue without a separate reopen step. */
    public boolean isAssignable() {
        return this == OPEN || this == HANDLING;
    }
}
