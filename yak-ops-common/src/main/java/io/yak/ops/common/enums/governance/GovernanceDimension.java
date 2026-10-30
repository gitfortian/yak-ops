package io.yak.ops.common.enums.governance;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Scored dimension of the Workspace health score. */
@Getter
@RequiredArgsConstructor
public enum GovernanceDimension {
    /** Ownership and description of catalogued tables. */
    DEV("DEV"),
    /** Scheduled execution health of published Task definitions. */
    COMPUTE("COMPUTE"),
    /** Equal-weight average of the dimensions that were evaluated on the same day. */
    OVERALL("OVERALL");

    @EnumValue
    private final String value;

    /** Whether a snapshot row for this dimension can carry a score. */
    public boolean isScored() {
        return this != OVERALL;
    }
}
