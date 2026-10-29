package io.yak.ops.common.enums.governance;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How a governance scan run was requested. */
@Getter
@RequiredArgsConstructor
public enum GovernanceScanTrigger {
    MANUAL("MANUAL"),
    SCHEDULED("SCHEDULED");

    @EnumValue
    private final String value;
}
