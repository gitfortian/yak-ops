package io.yak.ops.common.enums.modeling.standard;

/**
 * Closed taxonomy of measurement units.
 *
 * <p>Currency, quantifier and time are the categories the reference product names; ratio and rank
 * cover percentage shares and ranking fields, which a closed list has to name explicitly instead of
 * leaving to free text.
 */
public enum MeasureUnitCategory {
    /** Currency amounts, such as 元. */
    CURRENCY,
    /** Countable items, such as 件. */
    QUANTIFIER,
    /** Ratios and percentages, such as % (stored as a fraction). */
    RATIO,
    /** Ordinal ranks, such as 名. */
    RANK,
    /** Time measurements, such as 秒. */
    TIME
}
