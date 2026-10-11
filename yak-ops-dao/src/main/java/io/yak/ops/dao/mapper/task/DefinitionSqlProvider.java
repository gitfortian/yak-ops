package io.yak.ops.dao.mapper.task;

import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import java.util.Map;

/** Prepared-parameter SQL for plugin-neutral indexed Task Definition list queries. */
public final class DefinitionSqlProvider {
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
            // Business validates identifiers; quoted parameter paths cannot inject SQL.
            if (!key.matches("[A-Za-z][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid filter key");
            sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.");
            sql.append(key)
                    .append("')) = #{filter.parameterFilters.")
                    .append(key)
                    .append("}");
        }
        return sql.append(" ORDER BY d.update_time DESC, d.id DESC").toString();
    }
}
