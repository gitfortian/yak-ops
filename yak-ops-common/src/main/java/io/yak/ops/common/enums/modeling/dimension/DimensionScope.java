package io.yak.ops.common.enums.modeling.dimension;

/**
 * Where a concept dimension belongs.
 *
 * <p>A dimension is owned by exactly one scope: {@link #PUBLIC} binds a data domain of the business
 * tree, {@link #APPLICATION} binds a data mart of the application tree. The abbreviation is unique
 * inside that scope only, so the same abbreviation may exist once per domain or data mart.
 */
public enum DimensionScope {
    PUBLIC,
    APPLICATION
}
