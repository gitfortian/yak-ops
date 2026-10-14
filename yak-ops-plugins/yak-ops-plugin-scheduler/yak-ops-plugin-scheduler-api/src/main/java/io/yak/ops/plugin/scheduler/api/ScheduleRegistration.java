package io.yak.ops.plugin.scheduler.api;

import java.time.Instant;

/**
 * Non-secret immutable input for one engine registration.
 * @param workspaceId owning Workspace
 * @param scheduleId stable schedule identity
 * @param revision configuration revision captured at registration
 * @param cronExpression scheduler-native time rule
 * @param timeZone explicit named time zone
 * @param activatedAt exclusive boundary for this registration's first occurrence
 */
public record ScheduleRegistration(
        String workspaceId,
        String scheduleId,
        long revision,
        String cronExpression,
        String timeZone,
        Instant activatedAt) {}
