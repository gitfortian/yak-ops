package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.common.enums.modeling.NodeKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds SQL only from the closed level enum; all caller values remain bound parameters. */
public final class PlanningSqlProvider {
    private PlanningSqlProvider() {}

    private static String table(NodeKind kind) {
        return switch (kind) {
            case CATEGORY -> "yak_ops_modeling_category";
            case DOMAIN -> "yak_ops_modeling_domain";
            case PROCESS -> "yak_ops_modeling_process";
            case DATASET -> "yak_ops_modeling_datamart";
            case SUBJECT -> "yak_ops_modeling_subject";
        };
    }

    private static String parent(NodeKind kind) {
        return switch (kind) {
            case CATEGORY, DATASET -> null;
            case DOMAIN -> "category_id";
            case PROCESS -> "domain_id";
            case SUBJECT -> "datamart_id";
        };
    }

    private static String projection(NodeKind kind) {
        String parent = parent(kind);
        return "SELECT id,workspace_id,'" + kind.name() + "' AS kind,"
                + (parent == null ? "NULL" : parent) + " AS parent_id,"
                + "name,code,"
                + (kind.hasEnglishName() ? "en_name" : "NULL") + " AS en_name,"
                + (kind.hasOwner() ? "owner_id" : "NULL") + " AS owner_id,"
                + "description,revision,create_time,update_time,create_by,update_by FROM " + table(kind);
    }

    public static String tree() {
        return Arrays.stream(NodeKind.values())
                        .map(kind -> projection(kind) + " WHERE workspace_id=#{workspaceId}")
                        .collect(Collectors.joining(" UNION ALL "))
                + " ORDER BY code,id";
    }

    public static String find(Map<String, Object> params) {
        NodeKind kind = (NodeKind) params.get("kind");
        return projection(kind) + " WHERE workspace_id=#{workspaceId} AND id=#{id}"
                + (Boolean.TRUE.equals(params.get("lock")) ? " FOR UPDATE" : "");
    }

    public static String insert(Map<String, Object> params) {
        NodeKind kind = (NodeKind) params.get("kind");
        List<String> columns = new ArrayList<>(List.of("id", "workspace_id", "name", "code"));
        List<String> values =
                new ArrayList<>(List.of("#{node.id}", "#{node.workspaceId}", "#{node.name}", "#{node.code}"));
        if (kind.hasEnglishName()) {
            columns.add("en_name");
            values.add("#{node.enName}");
        }
        String parent = parent(kind);
        if (parent != null) {
            columns.add(parent);
            values.add("#{node.parentId}");
        }
        if (kind.hasOwner()) {
            columns.add("owner_id");
            values.add("#{node.ownerId}");
        }
        columns.addAll(List.of("description", "revision", "create_time", "update_time", "create_by", "update_by"));
        values.addAll(List.of(
                "#{node.description}",
                "#{node.revision}",
                "#{node.createTime}",
                "#{node.updateTime}",
                "#{node.createBy}",
                "#{node.updateBy}"));
        return "INSERT INTO " + table(kind) + "(" + String.join(",", columns) + ") VALUES (" + String.join(",", values)
                + ")";
    }

    public static String update(Map<String, Object> params) {
        NodeKind kind = (NodeKind) params.get("kind");
        StringBuilder assignments = new StringBuilder("SET name=#{node.name},code=#{node.code}");
        if (kind.hasEnglishName()) assignments.append(",en_name=#{node.enName}");
        if (kind.hasOwner()) assignments.append(",owner_id=#{node.ownerId}");
        assignments
                .append(",description=#{node.description},revision=revision+1,")
                .append("update_time=#{node.updateTime},update_by=#{node.updateBy}");
        return "UPDATE " + table(kind) + " " + assignments
                + " WHERE workspace_id=#{node.workspaceId} AND id=#{node.id} AND revision=#{revision}";
    }

    public static String delete(Map<String, Object> params) {
        return "DELETE FROM " + table((NodeKind) params.get("kind"))
                + " WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}";
    }

    public static String children(Map<String, Object> params) {
        NodeKind kind = (NodeKind) params.get("kind");
        return switch (kind) {
            case CATEGORY ->
                "SELECT id FROM yak_ops_modeling_domain WHERE workspace_id=#{workspaceId} AND category_id=#{id} LIMIT 1 FOR UPDATE";
            case DOMAIN ->
                "SELECT id FROM yak_ops_modeling_process WHERE workspace_id=#{workspaceId} AND domain_id=#{id} LIMIT 1 FOR UPDATE";
            case DATASET ->
                "SELECT id FROM yak_ops_modeling_subject WHERE workspace_id=#{workspaceId} AND datamart_id=#{id} LIMIT 1 FOR UPDATE";
            case PROCESS, SUBJECT -> "SELECT NULL";
        };
    }
}
