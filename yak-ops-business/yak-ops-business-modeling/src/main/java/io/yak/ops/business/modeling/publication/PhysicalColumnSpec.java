package io.yak.ops.business.modeling.publication;

import io.yak.ops.common.enums.modeling.standard.DataType;

/**
 * One logical field reduced to what physical generation and verification need.
 *
 * @param name physical column name, which is the field code
 * @param dataType logical type
 * @param lengthPrecision declared length or {@code precision,scale}
 * @param notNull whether the column must reject nulls
 * @param keyPosition one-based position inside the primary key, null when not a key column
 * @param comment column comment, which is the field Chinese name
 */
public record PhysicalColumnSpec(
        String name, DataType dataType, String lengthPrecision, boolean notNull, Integer keyPosition, String comment) {}
