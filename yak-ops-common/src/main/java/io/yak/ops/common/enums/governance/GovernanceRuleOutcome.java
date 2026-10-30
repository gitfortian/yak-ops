package io.yak.ops.common.enums.governance;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Outcome of one rule inside a scan run. */
@Getter
@RequiredArgsConstructor
public enum GovernanceRuleOutcome {
    RUNNING("RUNNING"),
    /** The rule enumerated every eligible target and may therefore auto-resolve missing ones. */
    SUCCEEDED("SUCCEEDED"),
    /** The rule threw, timed out or exceeded the scan budget; it resolves nothing. */
    FAILED("FAILED");

    @EnumValue
    private final String value;
}
