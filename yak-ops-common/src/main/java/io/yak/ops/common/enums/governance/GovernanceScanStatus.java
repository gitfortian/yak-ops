package io.yak.ops.common.enums.governance;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Terminal and in-flight states of one governance scan run. */
@Getter
@RequiredArgsConstructor
public enum GovernanceScanStatus {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    SUCCEEDED("SUCCEEDED"),
    /** At least one rule failed; counts observed by other rules stay valid. */
    PARTIAL("PARTIAL"),
    /** No rule completed a full enumeration. */
    FAILED("FAILED"),
    /** The run deliberately did not evaluate rules, for example while a data map crawl is active. */
    SKIPPED("SKIPPED");

    @EnumValue
    private final String value;

    public boolean isTerminal() {
        return this != PENDING && this != RUNNING;
    }
}
