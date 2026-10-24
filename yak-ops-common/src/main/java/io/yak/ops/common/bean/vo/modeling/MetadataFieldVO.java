package io.yak.ops.common.bean.vo.modeling;

/**
 * One observed physical column inside a metadata snapshot.
 *
 * @param ordinal one-based column order
 * @param name observed column name
 * @param typeName observed native type name
 * @param jdbcType observed {@code java.sql.Types} value
 * @param size observed column size, null when the driver reports none
 * @param scale observed decimal scale, null when the driver reports none
 * @param nullable whether the column accepts nulls
 * @param primaryKey whether the column participates in the primary key
 * @param primaryKeyPosition position inside the primary key, null when not a key column
 * @param remarks observed column comment
 */
public record MetadataFieldVO(
        int ordinal,
        String name,
        String typeName,
        int jdbcType,
        Integer size,
        Integer scale,
        boolean nullable,
        boolean primaryKey,
        Integer primaryKeyPosition,
        String remarks) {}
