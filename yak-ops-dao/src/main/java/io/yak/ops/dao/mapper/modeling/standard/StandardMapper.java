package io.yak.ops.dao.mapper.modeling.standard;

import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardFieldEntity;
import io.yak.ops.dao.repository.modeling.standard.FieldQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for the standard catalog, directories, sets and fields. */
@Mapper
public interface StandardMapper {
    @Insert(
            "INSERT INTO yak_ops_standard_catalog(workspace_id,create_time,create_by) VALUES(#{workspaceId},#{now},#{actor}) ON DUPLICATE KEY UPDATE workspace_id=workspace_id")
    void reserveCatalog(
            @Param("workspaceId") String workspaceId, @Param("actor") String actor, @Param("now") LocalDateTime now);

    /**
     * Locks the Workspace catalog row in the calling transaction.
     *
     * @return null when the catalog row has not been reserved yet, otherwise whether defaults were committed
     */
    @Select(
            "SELECT initialized_at IS NOT NULL FROM yak_ops_standard_catalog WHERE workspace_id=#{workspaceId} FOR UPDATE")
    Boolean lockCatalog(String workspaceId);

    @Update("UPDATE yak_ops_standard_catalog SET initialized_at=#{now} WHERE workspace_id=#{workspaceId}")
    void markInitialized(@Param("workspaceId") String workspaceId, @Param("now") LocalDateTime now);

    /**
     * Reads every node with the number of records it holds directly.
     *
     * <p>The count spans both kinds because it answers one question only: may this node be deleted,
     * and may a standard set still change the kind it declares. A set can only ever hold one kind,
     * so summing cannot hide a mixed state there.
     */
    @Select("SELECT c.*,"
            + "(SELECT COUNT(*) FROM yak_ops_standard_field f"
            + " WHERE f.workspace_id=c.workspace_id AND f.category_id=c.id)"
            + "+(SELECT COUNT(*) FROM yak_ops_standard_lookup l"
            + " WHERE l.workspace_id=c.workspace_id AND l.category_id=c.id) AS record_count "
            + "FROM yak_ops_standard_category c WHERE c.workspace_id=#{workspaceId} "
            + "ORDER BY c.create_time,c.id")
    List<CategoryEntity> categories(String workspaceId);

    @Select("SELECT * FROM yak_ops_standard_category WHERE workspace_id=#{workspaceId} AND id=#{id}")
    CategoryEntity category(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert(
            "INSERT INTO yak_ops_standard_category(id,workspace_id,parent_id,node_type,standard_kind,name,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{parentId},#{nodeType},#{standardKind},#{name},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insertCategory(CategoryEntity node);

    @Update(
            "UPDATE yak_ops_standard_category SET name=#{name},standard_kind=#{standardKind},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int updateCategory(CategoryEntity node);

    @Delete(
            "DELETE FROM yak_ops_standard_category WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int deleteCategory(
            @Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    /** Counts records of either kind that hang directly below one node. */
    @Select("SELECT (SELECT COUNT(*) FROM yak_ops_standard_field"
            + " WHERE workspace_id=#{workspaceId} AND category_id=#{categoryId})"
            + "+(SELECT COUNT(*) FROM yak_ops_standard_lookup"
            + " WHERE workspace_id=#{workspaceId} AND category_id=#{categoryId})")
    long recordsInCategory(@Param("workspaceId") String workspaceId, @Param("categoryId") String categoryId);

    @Select("SELECT s.*,"
            + "p.en_code AS parent_standard_en_code,"
            + "l.en_code AS lookup_en_code,l.cn_name AS lookup_cn_name,"
            + "u.en_code AS measure_unit_en_code,u.cn_name AS measure_unit_cn_name "
            + "FROM yak_ops_standard_field s "
            + "LEFT JOIN yak_ops_standard_field p ON p.workspace_id=s.workspace_id AND p.id=s.parent_standard_id "
            + "LEFT JOIN yak_ops_standard_lookup l ON l.workspace_id=s.workspace_id AND l.id=s.lookup_id "
            + "LEFT JOIN yak_ops_standard_measure_unit u ON u.workspace_id=s.workspace_id AND u.id=s.measure_unit_id "
            + "WHERE s.workspace_id=#{workspaceId} AND s.id=#{id}")
    StandardFieldEntity field(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert(
            "INSERT INTO yak_ops_standard_field(id,workspace_id,category_id,cn_name,en_code,data_type,length_precision,is_not_null,security_level,owner_id,business_caliber,default_value,parent_standard_id,lookup_id,measure_unit_id,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{categoryId},#{cnName},#{enCode},#{dataType},#{lengthPrecision},#{isNotNull},#{securityLevel},#{ownerId},#{businessCaliber},#{defaultValue},#{parentStandardId},#{lookupId},#{measureUnitId},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insertField(StandardFieldEntity node);

    @Update(
            "UPDATE yak_ops_standard_field SET cn_name=#{cnName},en_code=#{enCode},data_type=#{dataType},length_precision=#{lengthPrecision},is_not_null=#{isNotNull},security_level=#{securityLevel},owner_id=#{ownerId},business_caliber=#{businessCaliber},default_value=#{defaultValue},parent_standard_id=#{parentStandardId},lookup_id=#{lookupId},measure_unit_id=#{measureUnitId},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int updateField(StandardFieldEntity node);

    @Delete(
            "DELETE FROM yak_ops_standard_field WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int deleteField(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_standard_field WHERE workspace_id=#{workspaceId} AND en_code=#{enCode} AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(
            @Param("workspaceId") String workspaceId,
            @Param("enCode") String enCode,
            @Param("excludeId") String excludeId);

    // en_code is ascii_bin while bound parameters arrive as utf8mb4; CONVERT keeps the LIKE collation-compatible.
    @Select("SELECT s.*,"
            + "p.en_code AS parent_standard_en_code,"
            + "l.en_code AS lookup_en_code,l.cn_name AS lookup_cn_name,"
            + "u.en_code AS measure_unit_en_code,u.cn_name AS measure_unit_cn_name "
            + "FROM yak_ops_standard_field s "
            + "LEFT JOIN yak_ops_standard_field p ON p.workspace_id=s.workspace_id AND p.id=s.parent_standard_id "
            + "LEFT JOIN yak_ops_standard_lookup l ON l.workspace_id=s.workspace_id AND l.id=s.lookup_id "
            + "LEFT JOIN yak_ops_standard_measure_unit u ON u.workspace_id=s.workspace_id AND u.id=s.measure_unit_id "
            + "WHERE s.workspace_id=#{workspaceId} AND (#{query.categoryId} IS NULL OR s.category_id=#{query.categoryId}) "
            + "AND (#{query.keyword} IS NULL OR s.cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(s.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!') "
            + "ORDER BY s.update_time DESC,s.id DESC LIMIT #{query.offset},#{query.pageSize}")
    List<StandardFieldEntity> fields(@Param("workspaceId") String workspaceId, @Param("query") FieldQuery query);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_standard_field s WHERE s.workspace_id=#{workspaceId} AND (#{query.categoryId} IS NULL OR s.category_id=#{query.categoryId}) AND (#{query.keyword} IS NULL OR s.cn_name LIKE #{query.keyword} ESCAPE '!' OR CONVERT(s.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!')")
    long countFields(@Param("workspaceId") String workspaceId, @Param("query") FieldQuery query);

    /** Reads the parent link only, used to walk a chain without loading every attribute. */
    @Select("SELECT parent_standard_id FROM yak_ops_standard_field WHERE workspace_id=#{workspaceId} AND id=#{id}")
    String parentStandardId(@Param("workspaceId") String workspaceId, @Param("id") String id);
}
