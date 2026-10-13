package io.yak.ops.common.bean.dto.schedule;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import io.yak.ops.common.enums.schedule.ScheduleFireOutcome;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Bounded final-fire history filters in the authorized Workspace. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleFireQueryDTO extends PageQueryDTO {
    /** Optional final submission outcome. */
    private ScheduleFireOutcome outcome;
    /** Inclusive scheduled-time lower bound. */
    private Instant from;
    /** Exclusive scheduled-time upper bound. */
    private Instant until;
}
