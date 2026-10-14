package io.yak.ops.common.bean.vo.schedule;

import java.time.Instant;

/**
 * Sanitized registration observation derived from the engine, not desired enablement.
 * @param status PENDING, APPLIED, or ERROR
 * @param reason safe registration error code, absent when healthy
 * @param observedTime time of the observation
 * @param nextFireTime observed engine occurrence, absent when unknown or exhausted
 * @param exhausted whether the enabled finite expression has no future occurrence
 */
public record ScheduleRegistrationVO(
        String status, String reason, Instant observedTime, Instant nextFireTime, boolean exhausted) {}
