package io.yak.ops.dao.mapper.tag;

import io.yak.ops.dao.entity.tag.TableTagValueEntity;
import io.yak.ops.dao.entity.tag.TagKeyEntity;
import io.yak.ops.dao.entity.tag.TagValueEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Parameter-bound statements for the tag dictionary: keys, their values and the asset bindings.
 *
 * <p>Delete protection lives in the schema, not here: a bound value rejects the delete with a
 * foreign-key violation, and a key delete cascades into that same rejection. The service maps that
 * violation to {@code TAG_IN_USE}.
 */
@Mapper
public interface TagMapper {

    String KEY_COLUMNS = "id,workspace_id,name,create_time,update_time,create_by,update_by";

    String VALUE_COLUMNS = "id,workspace_id,key_id,name,create_time,update_time,create_by,update_by";

    String BINDING_COLUMNS = "workspace_id,table_id,tag_value_id,create_time,create_by";

    @Insert("INSERT INTO yak_ops_tag_key (" + KEY_COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{name},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insertKey(TagKeyEntity row);

    @Select("SELECT " + KEY_COLUMNS + " FROM yak_ops_tag_key WHERE workspace_id=#{workspaceId} AND id=#{id}")
    TagKeyEntity findKeyById(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT " + KEY_COLUMNS + " FROM yak_ops_tag_key WHERE workspace_id=#{workspaceId} AND name=#{name}")
    TagKeyEntity findKeyByName(@Param("workspaceId") String workspaceId, @Param("name") String name);

    @Select("SELECT " + KEY_COLUMNS + " FROM yak_ops_tag_key WHERE workspace_id=#{workspaceId} ORDER BY name, id")
    List<TagKeyEntity> listKeys(@Param("workspaceId") String workspaceId);

    @Update("UPDATE yak_ops_tag_key SET name=#{name},update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int renameKey(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("name") String name,
            @Param("updateTime") LocalDateTime updateTime,
            @Param("updateBy") String updateBy);

    /** Cascades to the key's values; a bound value turns the cascade into a foreign-key violation. */
    @Delete("DELETE FROM yak_ops_tag_key WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int deleteKey(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert("INSERT INTO yak_ops_tag_value (" + VALUE_COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{keyId},#{name},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insertValue(TagValueEntity row);

    @Select("SELECT " + VALUE_COLUMNS + " FROM yak_ops_tag_value WHERE workspace_id=#{workspaceId} AND id=#{id}")
    TagValueEntity findValueById(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Resolves the requested values of a filter or a binding command in one statement. */
    @Select("<script>SELECT " + VALUE_COLUMNS + " FROM yak_ops_tag_value WHERE workspace_id=#{workspaceId} "
            + "AND id IN <foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}"
            + "</foreach></script>")
    List<TagValueEntity> findValuesByIds(@Param("workspaceId") String workspaceId, @Param("ids") List<String> ids);

    @Select("SELECT " + VALUE_COLUMNS + " FROM yak_ops_tag_value WHERE key_id=#{keyId} AND name=#{name}")
    TagValueEntity findValueByName(@Param("keyId") String keyId, @Param("name") String name);

    @Select("SELECT " + VALUE_COLUMNS + " FROM yak_ops_tag_value WHERE workspace_id=#{workspaceId} "
            + "ORDER BY name, id")
    List<TagValueEntity> listValues(@Param("workspaceId") String workspaceId);

    @Update("UPDATE yak_ops_tag_value SET name=#{name},update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int renameValue(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("name") String name,
            @Param("updateTime") LocalDateTime updateTime,
            @Param("updateBy") String updateBy);

    /** A bound value rejects this delete through the binding foreign key. */
    @Delete("DELETE FROM yak_ops_tag_value WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int deleteValue(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM yak_ops_tag_value WHERE workspace_id=#{workspaceId} AND key_id=#{keyId}")
    long countValuesByKey(@Param("workspaceId") String workspaceId, @Param("keyId") String keyId);

    /**
     * Inserts many bindings at once, skipping pairs that are already bound.
     *
     * <p>A batch ADD is idempotent by intent, so a pair another transaction bound in the meantime is
     * a no-op instead of a duplicate-key failure.
     */
    @Insert({
        "<script>",
        "INSERT IGNORE INTO yak_ops_data_map_table_tag_value (" + BINDING_COLUMNS + ") VALUES",
        "<foreach collection='rows' item='row' separator=','>",
        "(#{row.workspaceId},#{row.tableId},#{row.tagValueId},#{row.createTime},#{row.createBy})",
        "</foreach>",
        "</script>"
    })
    int insertBindings(@Param("rows") List<TableTagValueEntity> rows);

    @Delete("DELETE FROM yak_ops_data_map_table_tag_value "
            + "WHERE workspace_id=#{workspaceId} AND table_id=#{tableId}")
    int deleteBindingsByTable(@Param("workspaceId") String workspaceId, @Param("tableId") String tableId);

    @Select("<script>SELECT " + BINDING_COLUMNS + " FROM yak_ops_data_map_table_tag_value "
            + "WHERE workspace_id=#{workspaceId} AND table_id IN "
            + "<foreach collection=\"tableIds\" item=\"tableId\" open=\"(\" separator=\",\" close=\")\">#{tableId}"
            + "</foreach></script>")
    List<TableTagValueEntity> listBindings(
            @Param("workspaceId") String workspaceId, @Param("tableIds") List<String> tableIds);

    @Select("<script>SELECT " + BINDING_COLUMNS + " FROM yak_ops_data_map_table_tag_value "
            + "WHERE workspace_id=#{workspaceId} AND table_id IN "
            + "<foreach collection=\"tableIds\" item=\"tableId\" open=\"(\" separator=\",\" close=\")\">#{tableId}"
            + "</foreach> AND tag_value_id IN "
            + "<foreach collection=\"tagValueIds\" item=\"tagValueId\" open=\"(\" separator=\",\" close=\")\">"
            + "#{tagValueId}</foreach></script>")
    List<TableTagValueEntity> listBindingsByTablesAndValues(
            @Param("workspaceId") String workspaceId,
            @Param("tableIds") List<String> tableIds,
            @Param("tagValueIds") List<String> tagValueIds);

    /** Removes the selected pairs from every listed asset in one statement. */
    @Delete("<script>DELETE FROM yak_ops_data_map_table_tag_value "
            + "WHERE workspace_id=#{workspaceId} AND table_id IN "
            + "<foreach collection=\"tableIds\" item=\"tableId\" open=\"(\" separator=\",\" close=\")\">#{tableId}"
            + "</foreach> AND tag_value_id IN "
            + "<foreach collection=\"tagValueIds\" item=\"tagValueId\" open=\"(\" separator=\",\" close=\")\">"
            + "#{tagValueId}</foreach></script>")
    int deleteBindingsByTablesAndValues(
            @Param("workspaceId") String workspaceId,
            @Param("tableIds") List<String> tableIds,
            @Param("tagValueIds") List<String> tagValueIds);

    @Select("SELECT tag_value_id,COUNT(*) AS usage_count FROM yak_ops_data_map_table_tag_value "
            + "WHERE workspace_id=#{workspaceId} GROUP BY tag_value_id")
    List<TagValueUsageRow> listUsageCounts(@Param("workspaceId") String workspaceId);

    @Select("SELECT COUNT(*) FROM yak_ops_data_map_table_tag_value "
            + "WHERE workspace_id=#{workspaceId} AND tag_value_id=#{tagValueId}")
    long countBindingsByValue(@Param("workspaceId") String workspaceId, @Param("tagValueId") String tagValueId);

    /**
     * Projects the tag values of the listed assets with their key names, in key then value order.
     *
     * <p>The catalog lists and the asset detail both render {@code key = value}; one join keeps
     * that a single statement instead of three reads that must be stitched back together.
     */
    @Select("<script>SELECT b.table_id AS tableId,v.key_id AS keyId,k.name AS keyName,"
            + "v.id AS valueId,v.name AS valueName FROM yak_ops_data_map_table_tag_value b "
            + "JOIN yak_ops_tag_value v ON v.id=b.tag_value_id AND v.workspace_id=b.workspace_id "
            + "JOIN yak_ops_tag_key k ON k.id=v.key_id AND k.workspace_id=v.workspace_id "
            + "WHERE b.workspace_id=#{workspaceId} AND b.table_id IN "
            + "<foreach collection=\"tableIds\" item=\"tableId\" open=\"(\" separator=\",\" close=\")\">#{tableId}"
            + "</foreach> ORDER BY k.name,v.name</script>")
    List<TableTagRefRow> listTagRefs(
            @Param("workspaceId") String workspaceId, @Param("tableIds") List<String> tableIds);
}
