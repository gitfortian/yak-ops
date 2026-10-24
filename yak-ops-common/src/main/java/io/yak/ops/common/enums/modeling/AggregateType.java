package io.yak.ops.common.enums.modeling;

import io.yak.ops.common.enums.modeling.standard.DataType;

/**
 * Aggregate function carried by a measure field.
 *
 * <p>Only summary ({@code DWS}) and application ({@code ADS}) models may declare one; a detail or
 * dimension table rejects it. The compatibility matrix lives here so the editor, the request
 * validation and the tests share one definition instead of repeating it on both sides.
 */
public enum AggregateType {
    SUM,
    COUNT,
    AVG,
    MAX,
    MIN;

    /**
     * Resolves a submitted value without guessing.
     *
     * @param value request text, case sensitive
     * @return matching function, or null when absent or unknown
     */
    public static AggregateType from(String value) {
        if (value == null) return null;
        for (var type : values()) {
            if (type.name().equals(value)) return type;
        }
        return null;
    }

    /**
     * Whether this function may be applied to the given logical type.
     *
     * <p>{@code SUM} and {@code AVG} need arithmetic, so they are limited to the two numeric types.
     * {@code COUNT} ignores the value, and {@code MAX} / {@code MIN} only need an ordering that all
     * five logical types provide.
     *
     * @param dataType resolved logical type of the field
     * @return true when the pair is allowed
     */
    public boolean supports(DataType dataType) {
        if (dataType == null) return false;
        return switch (this) {
            case SUM, AVG -> dataType == DataType.BIGINT || dataType == DataType.DECIMAL;
            case COUNT, MAX, MIN -> true;
        };
    }
}
