package io.yak.ops.common.enums.datamap;

/** Closed set of changes a crawl can detect between two observations of one asset. */
public enum DataMapTableChangeType {
    /** A column appeared. */
    COLUMN_ADDED,
    /** A column disappeared. */
    COLUMN_REMOVED,
    /** A column's native type changed. */
    COLUMN_TYPE_CHANGED,
    /** A column's nullability changed. */
    COLUMN_NULLABILITY_CHANGED,
    /** The primary-key sequence changed. */
    PRIMARY_KEY_CHANGED,
    /** The engine-reported table comment changed. */
    TABLE_COMMENT_CHANGED
}
