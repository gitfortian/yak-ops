package io.yak.ops.dao.entity.datamap;

import lombok.Getter;
import lombok.Setter;

/** Persists one current-state column of a catalog asset. */
@Getter
@Setter
public class DataMapColumnEntity {
    /** Application-generated column identity. */
    private String id;
    /** Workspace owning the asset. */
    private String workspaceId;
    /** Asset this column belongs to. */
    private String tableId;
    /** Engine-reported ordinal position, from one. */
    private Integer ordinal;
    /** Column name. */
    private String name;
    /** Engine-reported native type name. */
    private String typeName;
    /** java.sql.Types code. */
    private Integer jdbcType;
    /** Declared or reported column size. */
    private Integer size;
    /** Declared or reported numeric scale. */
    private Integer scale;
    /** Whether the column admits nulls. */
    private Boolean nullable;
    /** Whether the column participates in the primary key. */
    private Boolean primaryKey;
    /** Ordinal inside the declared primary key. */
    private Integer primaryKeyPosition;
    /** Engine-reported column comment. */
    private String remarks;
}
