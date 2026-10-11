package io.yak.ops.common.bean.vo.datasource;

import lombok.Data;

/**
 * Exposes physical column metadata from a datasource Catalog.
 */
@Data
public class DataSourceCatalogColumnVO {

    /** Database-native column name; preserve its original case for mappings. */
    private String name;

    /** Native SQL type name returned by the database, such as VARCHAR or NUMBER. */
    private String typeName;

    /** Standard JDBC type code from {@link java.sql.Types} for cross-database compatibility checks. */
    private Integer jdbcType;

    /** Column length or numeric precision supplied by the JDBC driver; may be null. */
    private Integer size;

    /** Fractional precision of numeric columns; null for non-numeric or unspecified values. */
    private Integer scale;

    /** Whether the database column accepts NULL. */
    private Boolean nullable;

    /** Physical column ordinal reported by JDBC Catalog metadata. */
    private Integer ordinalPosition;

    /** Whether the column belongs to the table's primary key. */
    private Boolean primaryKey;

    /** Composite primary-key position from JDBC KEY_SEQ; null for non-key or unspecified columns. */
    private Integer primaryKeyPosition;

    /** Optional database column description or comment. */
    private String remarks;
}
