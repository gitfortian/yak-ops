package io.yak.ops.dao.mapper.task;

import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import java.util.Map;

/** Typed product filters target nested canonical JSON paths; bound filter values remain parameters. */
public final class DefinitionSqlProvider {
    private static final Map<String, String> JSON_FILTER_PATHS = Map.of(
            "sourceType", "$.source.type",
            "sourceDataSourceId", "$.source.datasourceId",
            "targetDataSourceId", "$.sink.datasourceId");

    private DefinitionSqlProvider() {}

    public static String selectDefinitionPage(Map<String, Object> args) {
        DefinitionPageQuery filter = (DefinitionPageQuery) args.get("filter");
        StringBuilder sql = new StringBuilder("""
                SELECT d.* FROM yak_ops_task_definition d
                JOIN yak_ops_task_definition_version v
                  ON v.workspace_id = d.workspace_id
                 AND v.definition_id = d.id AND v.version = d.current_version
                WHERE d.workspace_id = #{filter.workspaceId} AND d.deleted_at IS NULL
                """);
        if (filter.taskType() != null) sql.append(" AND d.task_type = #{filter.taskType}");
        if (filter.statusValue() != null) sql.append(" AND d.status = #{filter.statusValue}");
        if (filter.keyword() != null) {
            sql.append(" AND (d.name LIKE CONCAT('%', #{filter.keyword}, '%')");
            sql.append(" OR v.parameters LIKE CONCAT('%', #{filter.keyword}, '%'))");
        }
        for (String key : filter.parameterFilters().keySet()) {
            String path = JSON_FILTER_PATHS.get(key);
            if (path == null) throw new IllegalArgumentException("Unsupported definition parameter filter");
            sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '")
                    .append(path)
                    .append("')) = #{filter.parameterFilters.")
                    .append(key)
                    .append("}");
        }
        return sql.append(" ORDER BY d.update_time DESC, d.id DESC").toString();
    }
}
