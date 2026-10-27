package io.yak.ops.common.enums.datamap;

/** Presence of a catalog asset in its datasource namespace. */
public enum DataMapTableStatus {
    /** Observed by the most recent complete run. */
    ACTIVE,
    /** Absent from a complete run; the table was dropped or renamed. */
    MISSING
}
