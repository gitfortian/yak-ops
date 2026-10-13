package io.yak.ops.common.bean.vo.schedule;

import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import java.time.Instant;
import lombok.Data;

/** Non-secret desired schedule state; registration observations remain separate. */
@Data
public class ScheduleSummaryVO {
    /** Stable schedule identity. */
    private String id;
    /** Root target type, independent of the Task plugin. */
    private ScheduleTargetType targetType;
    /** Target Definition identifier. */
    private String targetId;
    /** Stored Quartz expression. */
    private String cronExpression;
    /** Named evaluation time zone. */
    private String timeZone;
    /** Desired enablement, not confirmation of engine registration. */
    private boolean enabled;
    /** Optimistic configuration and trigger-eligibility revision. */
    private long revision;
    /** PENDING until an engine observation can prove applied state. */
    private String registrationStatus;
    /** Safe reason for a failed registration observation, absent when healthy. */
    private String registrationReason;
    /** UTC time of the current engine observation. */
    private Instant registrationObservedTime;
    /** Whether the enabled finite time rule has no future occurrence. */
    private boolean exhausted;
    /** Engine-observed next fire instant; null when registration is unobserved. */
    private Instant nextFireTime;
}
