package io.yak.ops.common.bean.vo.schedule;

import io.yak.ops.common.enums.schedule.ScheduleFireOutcome;
import io.yak.ops.common.enums.schedule.ScheduleFireReason;
import java.time.Instant;

/**
 * Authorized trigger history, not a second Task execution lifecycle.
 * @param id fire result identity
 * @param scheduleId originating schedule
 * @param scheduleRevision originating registration revision
 * @param scheduledTime logical UTC time
 * @param fireTime actual callback time
 * @param resolvedTime durable resolution time
 * @param outcome final submission result
 * @param reason safe reason, absent for admission
 * @param instanceId admitted Instance or null
 */
public record ScheduleFireVO(
        String id,
        String scheduleId,
        long scheduleRevision,
        Instant scheduledTime,
        Instant fireTime,
        Instant resolvedTime,
        ScheduleFireOutcome outcome,
        ScheduleFireReason reason,
        String instanceId) {}
