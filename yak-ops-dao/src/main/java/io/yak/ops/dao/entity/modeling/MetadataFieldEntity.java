package io.yak.ops.dao.entity.modeling;

import lombok.Getter;
import lombok.Setter;

/** Persists one observed physical column inside a metadata snapshot. */
@Getter
@Setter
public class MetadataFieldEntity {
    /** Application-generated identity. */
    private String id;
    /** Workspace owning the snapshot. */
    private String workspaceId;
    /** Collected table this column belongs to. */
    private String tableId;
    /** One-based column order reported by the driver. */
    private int ordinal;
    /** Observed physical column name. */
    private String name;
    /** Observed native type name. */
    private String typeName;
    /** Observed {@code java.sql.Types} value. */
    private int jdbcType;
    /** Observed column size, when the driver reports one. */
    private Integer size;
    /** Observed decimal scale, when the driver reports one. */
    private Integer scale;
    /** Whether the column accepts nulls. */
    private Boolean nullable;
    /** Whether the column participates in the primary key. */
    private Boolean primaryKey;
    /** Position inside the primary key; null when not a key column. */
    private Integer primaryKeyPosition;
    /** Observed column comment. */
    private String remarks;
}
