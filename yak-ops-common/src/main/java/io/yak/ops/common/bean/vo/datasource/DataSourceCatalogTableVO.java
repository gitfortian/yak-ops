package io.yak.ops.common.bean.vo.datasource;

import lombok.Data;

/**
 * Exposes physical table and view metadata from a datasource Catalog.
 */
@Data
public class DataSourceCatalogTableVO {

    /** Database owning the table; null when the provider has no database level. */
    private String database;

    /** Schema owning the table; null when the provider has no schema level. */
    private String schema;

    /** Physical table or view name returned by the database. */
    private String name;

    /** Catalog object type, such as TABLE or VIEW. */
    private String type;

    /** Optional database table or view description. */
    private String remarks;
}
