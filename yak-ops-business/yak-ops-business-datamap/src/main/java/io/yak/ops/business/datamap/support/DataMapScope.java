package io.yak.ops.business.datamap.support;

import io.yak.ops.common.util.StringUtils;
import io.yak.ops.core.data.TableId;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;

/**
 * Namespace selected for one crawl or one stored asset identity.
 *
 * <p>The two slots are exactly the selectors the JDBC Catalog accepts: for MySQL the database is
 * the catalog slot and the schema is empty, for PostgreSQL the schema carries the namespace. They
 * are stored as {@code NOT NULL DEFAULT ''} because InnoDB treats NULLs as distinct in a unique
 * index.
 *
 * @param databaseName catalog slot; empty when the engine has none
 * @param schemaName schema slot; empty when the engine has none
 */
public record DataMapScope(String databaseName, String schemaName) {

    /**
     * Resolves the requested namespace, falling back to the datasource binding.
     *
     * @param database requested catalog slot or null
     * @param schema requested schema slot or null
     * @param datasource authorized datasource information
     * @return normalized scope with empty strings for absent slots
     */
    public static DataMapScope resolve(String database, String schema, DataSourceInfo datasource) {
        return new DataMapScope(
                emptyIfBlank(StringUtils.trimToNull(database), datasource.database()),
                emptyIfBlank(StringUtils.trimToNull(schema), datasource.schema()));
    }

    private static String emptyIfBlank(String requested, String bound) {
        String value = requested == null ? bound : requested;
        return value == null ? "" : value;
    }

    /**
     * Builds the physical identity of one table inside this scope.
     *
     * @param table unqualified table name
     * @return connector identity with empty slots normalized to null
     */
    public TableId tableId(String table) {
        return new TableId(nullIfEmpty(databaseName), nullIfEmpty(schemaName), table);
    }

    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
