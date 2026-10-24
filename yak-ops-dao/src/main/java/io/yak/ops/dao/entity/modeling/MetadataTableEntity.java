package io.yak.ops.dao.entity.modeling;

import lombok.Getter;
import lombok.Setter;

/** Persists one observed physical table inside a metadata snapshot. */
@Getter
@Setter
public class MetadataTableEntity {
    /** Application-generated identity. */
    private String id;
    /** Workspace owning the snapshot. */
    private String workspaceId;
    /** Collection run this row belongs to. */
    private String collectionId;
    /** Datasource the table was read through. */
    private String datasourceId;
    /** Observed physical database. */
    private String databaseName;
    /** Observed physical schema; null where the engine has none. */
    private String schemaName;
    /** Observed physical table name. */
    private String name;
    /** Observed table type reported by the driver. */
    private String type;
    /** Observed table comment. */
    private String remarks;
}
