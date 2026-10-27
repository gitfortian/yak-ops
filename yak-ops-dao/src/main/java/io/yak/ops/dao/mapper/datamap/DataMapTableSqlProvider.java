package io.yak.ops.dao.mapper.datamap;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the catalog search statements from bound parameters.
 *
 * <p>Every condition is a MyBatis placeholder; the only assembled fragments are the fixed
 * {@code AND} clauses below, so no caller value reaches the SQL text.
 */
public final class DataMapTableSqlProvider {

    /** Read projection that carries the datasource display name beside the asset. */
    public static final String PROJECTION = "SELECT t.id,t.workspace_id,t.datasource_id,ds.name AS datasource_name,"
            + "t.database_name,t.schema_name,t.name,t.table_type,t.remarks,t.status,t.first_seen_time,"
            + "t.last_seen_time,t.description,t.owner_id,t.domain_id,t.create_time,t.update_time,t.create_by,"
            + "t.update_by FROM yak_ops_data_map_table t "
            + "LEFT JOIN yak_ops_data_source ds ON ds.id=t.datasource_id";

    private DataMapTableSqlProvider() {}

    private static String conditions(DataMapSearchCriteria criteria) {
        List<String> conditions = new ArrayList<>();
        conditions.add("t.workspace_id=#{workspaceId}");
        if (!criteria.isIncludeMissing()) conditions.add("t.status='ACTIVE'");
        if (criteria.getDatasourceId() != null) conditions.add("t.datasource_id=#{datasourceId}");
        if (criteria.getDatabaseName() != null) conditions.add("t.database_name=#{databaseName}");
        if (criteria.getSchemaName() != null) conditions.add("t.schema_name=#{schemaName}");
        if (criteria.getDomainId() != null) conditions.add("t.domain_id=#{domainId}");
        if (criteria.isUnclassified()) conditions.add("t.domain_id IS NULL");
        if (criteria.getTagId() != null) {
            conditions.add("EXISTS (SELECT 1 FROM yak_ops_data_map_table_tag tt "
                    + "WHERE tt.table_id=t.id AND tt.tag_id=#{tagId})");
        }
        if (!criteria.getIds().isEmpty()) {
            conditions.add("t.id IN <foreach collection=\"ids\" item=\"identity\" open=\"(\" separator=\",\" "
                    + "close=\")\">#{identity}</foreach>");
        }
        if (criteria.getKeywordPattern() != null) {
            conditions.add("(t.name LIKE #{keywordPattern} ESCAPE '!' "
                    + "OR t.remarks LIKE #{keywordPattern} ESCAPE '!' "
                    + "OR t.description LIKE #{keywordPattern} ESCAPE '!' "
                    + "OR EXISTS (SELECT 1 FROM yak_ops_data_map_column c WHERE c.table_id=t.id "
                    + "AND c.workspace_id=t.workspace_id "
                    + "AND (c.name LIKE #{keywordPattern} ESCAPE '!' "
                    + "OR c.remarks LIKE #{keywordPattern} ESCAPE '!')))");
        }
        return String.join(" AND ", conditions);
    }

    /** Returns one page of matching assets in browse order. */
    public static String select(DataMapSearchCriteria criteria) {
        return "<script>" + PROJECTION + " WHERE " + conditions(criteria)
                + " ORDER BY t.name, t.id LIMIT #{limit} OFFSET #{offset}</script>";
    }

    /**
     * Counts matching assets, capped at one thousand rows.
     *
     * <p>The cap is applied to the derived table so a broad keyword cannot be turned into a long
     * count query; the UI renders {@code 1000+} past it.
     */
    public static String count(DataMapSearchCriteria criteria) {
        return "<script>SELECT COUNT(*) FROM (SELECT t.id FROM yak_ops_data_map_table t WHERE " + conditions(criteria)
                + " LIMIT 1000) capped</script>";
    }
}
