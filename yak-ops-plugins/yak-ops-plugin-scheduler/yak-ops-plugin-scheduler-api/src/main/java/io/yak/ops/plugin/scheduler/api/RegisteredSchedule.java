package io.yak.ops.plugin.scheduler.api;

import java.time.Instant;

/**
 * Observed engine state, independent of Task execution or desired enablement.
 * @param registration stored registration metadata
 * @param triggerPresent whether a physical trigger exists
 * @param firingEnabled whether the present trigger is eligible for engine firing
 * @param nextFireTime engine-observed next UTC occurrence, null when none exists
 */
public record RegisteredSchedule(
        ScheduleRegistration registration, boolean triggerPresent, boolean firingEnabled, Instant nextFireTime) {}
