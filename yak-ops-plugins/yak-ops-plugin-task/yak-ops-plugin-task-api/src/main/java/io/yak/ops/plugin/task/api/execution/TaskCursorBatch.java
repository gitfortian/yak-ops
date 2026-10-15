package io.yak.ops.plugin.task.api.execution;

/** A non-secret table cursor proposal or an Instance's frozen source range. */
public record TaskCursorBatch(
        String tableKey,
        String binding,
        String mode,
        String column,
        String initialValue,
        String lower,
        String upper,
        boolean allowInitialLoad,
        boolean initialLoad,
        long expectedVersion) {
    public TaskCursorBatch {
        if (tableKey == null
                || !tableKey.matches("[0-9a-f]{64}")
                || binding == null
                || !binding.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid table cursor identity");
        }
        if (mode == null || column == null || upper == null || upper.isBlank()) {
            throw new IllegalArgumentException("Cursor mode, column and upper bound are required");
        }
    }

    /** Creates an uncommitted upper-bound proposal, resolved against durable state by the platform. */
    public static TaskCursorBatch proposal(
            String key, String binding, String mode, String column, String initialValue, String upper, boolean full) {
        return new TaskCursorBatch(key, binding, mode, column, initialValue, null, upper, full, false, -1);
    }
}
