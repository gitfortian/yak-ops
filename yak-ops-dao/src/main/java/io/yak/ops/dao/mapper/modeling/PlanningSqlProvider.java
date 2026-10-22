package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.common.enums.modeling.NodeKind;
import java.util.Map;

/** Builds SQL only from the closed level enum; all caller values remain bound parameters. */
public final class PlanningSqlProvider {
    private PlanningSqlProvider() {}

    private static String table(NodeKind kind) {
        return switch (kind) {
            case CATEGORY -> "yak_ops_modeling_category";
            case DOMAIN -> "yak_ops_modeling_domain";
            case PROCESS -> "yak_ops_modeling_process";
        };
    }

    private static String parent(NodeKind kind) {
        return switch (kind) {
            case CATEGORY -> null;
            case DOMAIN -> "category_id";
            case PROCESS -> "domain_id";
        };
    }

    private static String projection(NodeKind kind) {
        String parent = parent(kind);
        return "SELECT id,workspace_id,'" + kind.name() + "' AS kind,"
                + (parent == null ? "NULL" : parent) + " AS parent_id,"
                + "name,code,owner_id,description,revision,create_time,update_time,create_by,update_by FROM "
                + table(kind);
    }

    public static String tree() {
        return java.util.Arrays.stream(NodeKind.values())
                        .map(kind -> projection(kind) + " WHERE workspace_id=#{workspaceId}")
                        .collect(java.util.stream.Collectors.joining(" UNION ALL "))
                + " ORDER BY code,id";
    }

    public static String find(Map<String, Object> params) {
        NodeKind kind = (NodeKind) params.get("kind");
        return projection(kind) + " WHERE workspace_id=#{workspaceId} AND id=#{id}"
                + (Boolean.TRUE.equals(params.get("lock")) ? " FOR UPDATE" : "");
    }

    public static String insert(Map<String, Object> params) {
        NodeKind kind = (NodeKind) params.get("kind");
        String parent = parent(kind);
        return "INSERT INTO " + table(kind)
                + "(id,workspace_id,name,code,owner_id,description,revision,create_time,update_time,create_by,update_by"
                + (parent == null ? "" : "," + parent) + ") VALUES "
                + "(#{node.id},#{node.workspaceId},#{node.name},#{node.code},#{node.ownerId},#{node.description},"
                + "#{node.revision},#{node.createTime},#{node.updateTime},#{node.createBy},#{node.updateBy}"
                + (parent == null ? "" : ",#{node.parentId}") + ")";
    }

    public static String update(Map<String, Object> params) {
        return "UPDATE " + table((NodeKind) params.get("kind"))
                + " SET name=#{node.name},code=#{node.code},owner_id=#{node.ownerId},description=#{node.description},"
                + "revision=revision+1,update_time=#{node.updateTime},update_by=#{node.updateBy}"
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
            case PROCESS -> "SELECT NULL";
        };
    }
}
