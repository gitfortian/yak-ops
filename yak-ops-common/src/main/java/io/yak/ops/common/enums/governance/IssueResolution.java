package io.yak.ops.common.enums.governance;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Who closed a resolved Issue. */
@Getter
@RequiredArgsConstructor
public enum IssueResolution {
    /** A member resolved the Issue for the current review. */
    USER("USER"),
    /** A complete rule scan no longer saw the target. */
    AUTO("AUTO");

    @EnumValue
    private final String value;
}
