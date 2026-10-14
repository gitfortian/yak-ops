package io.yak.ops.dao.repository.task;

/** Immutable per-table Instance window persisted before any physical Job submission. */
public record TaskCursorBatchRow(
        String tableKey,
        String binding,
        String mode,
        String column,
        String initialValue,
        String lower,
        String upper,
        boolean allowInitialLoad,
        boolean initialLoad,
        long expectedVersion) {}
