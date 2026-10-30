package io.yak.ops.common.enums.governance;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Severity fixed by the rule that produced an Issue. */
@Getter
@RequiredArgsConstructor
public enum IssueSeverity {
    HIGH("HIGH", 10),
    MEDIUM("MEDIUM", 3),
    LOW("LOW", 1);

    @EnumValue
    private final String value;

    /** Weight this severity adds to the weighted issue count of its dimension. */
    private final int weight;
}
