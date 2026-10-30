package io.yak.ops.business.datamap.support;

/**
 * Product limits fixed by FYOIP-15 rather than configuration.
 *
 * <p>They are shared by execution, validation and preview so a value the editor accepts can never
 * be refused by the worker for being over a different ceiling.
 */
public final class DataMapLimits {

    /** A namespace reaching this many tables is refused before any column is read. */
    public static final int TABLE_CEILING = 2_000;

    /** Excluded tables a task may name explicitly. */
    public static final int EXCLUDE_TABLE_LIMIT = 500;

    /** Per-table failures returned for one run. */
    public static final int FAILURE_PREVIEW_LIMIT = 200;

    private DataMapLimits() {}
}
