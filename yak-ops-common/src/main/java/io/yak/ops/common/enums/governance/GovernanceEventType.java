package io.yak.ops.common.enums.governance;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Append-only transition recorded on a governance Issue. */
@Getter
@RequiredArgsConstructor
public enum GovernanceEventType {
    CREATED("CREATED"),
    REOPENED("REOPENED"),
    ASSIGNED("ASSIGNED"),
    IGNORED("IGNORED"),
    UNIGNORED("UNIGNORED"),
    RESOLVED("RESOLVED"),
    AUTO_RESOLVED("AUTO_RESOLVED");

    @EnumValue
    private final String value;
}
