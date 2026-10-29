package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import java.util.List;
import java.util.Objects;

/**
 * Immutable collection of authorized table routes and their atomic admission claim.
 *
 * <p>Single-table and multi-table jobs use the same collection-based execution path.
 */
public record JobPlan(List<TablePlan> tables, TaskAdmissionPlan admissionPlan) {
    public JobPlan {
        tables = List.copyOf(tables);
        if (tables.isEmpty()) throw new IllegalArgumentException("Job needs table mappings");
        Objects.requireNonNull(admissionPlan, "admissionPlan");
    }
}
