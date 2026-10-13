package io.yak.ops.common.bean.dto.schedule;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Optimistic revision required for explicit enablement and disablement.
 * @param revision last observed configuration revision
 */
public record ScheduleRevisionDTO(@NotNull @Min(1) Long revision) {}
