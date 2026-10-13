package io.yak.ops.business.task.submission;

import io.yak.ops.common.enums.schedule.ScheduleFireOutcome;
import io.yak.ops.common.enums.schedule.ScheduleFireReason;

/**
 * Durable resolution of a logical fire; a replay is not a new occurrence.
 * @param outcome final submission outcome
 * @param reason safe non-admission reason or null
 * @param instanceId admitted Instance ID or null
 * @param replayed whether terminal evidence already existed
 */
public record ScheduleFireResult(
        ScheduleFireOutcome outcome, ScheduleFireReason reason, String instanceId, boolean replayed) {}
