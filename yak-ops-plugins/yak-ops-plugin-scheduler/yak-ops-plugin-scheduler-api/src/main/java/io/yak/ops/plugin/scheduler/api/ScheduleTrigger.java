package io.yak.ops.plugin.scheduler.api;

import java.time.Instant;

/**
 * Notification copied from the fired trigger, never reconstructed from a mutable JobDetail.
 * @param workspaceId registered Workspace
 * @param scheduleId registered schedule identity
 * @param revision revision of the fired registration
 * @param scheduledTime expected UTC occurrence
 * @param fireTime actual Quartz callback time
 * @param activatedAt exclusive activation boundary captured in the fired registration
 */
public record ScheduleTrigger(
        String workspaceId,
        String scheduleId,
        long revision,
        Instant scheduledTime,
        Instant fireTime,
        Instant activatedAt) {}
