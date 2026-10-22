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

/** Parameter-bound statements for the standard catalog, directories and fields. */
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

    @Select("SELECT * FROM yak_ops_standard_category WHERE workspace_id=#{workspaceId} ORDER BY create_time,id")
    List<CategoryEntity> categories(String workspaceId);

    @Select("SELECT * FROM yak_ops_standard_category WHERE workspace_id=#{workspaceId} AND id=#{id}")
    CategoryEntity category(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert(
            "INSERT INTO yak_ops_standard_category(id,workspace_id,parent_id,name,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{parentId},#{name},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insertCategory(CategoryEntity node);

    @Update(
            "UPDATE yak_ops_standard_category SET name=#{name},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int updateCategory(CategoryEntity node);

    @Delete(
            "DELETE FROM yak_ops_standard_category WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int deleteCategory(
            @Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_standard_field WHERE workspace_id=#{workspaceId} AND category_id=#{categoryId}")
    long fieldsInCategory(@Param("workspaceId") String workspaceId, @Param("categoryId") String categoryId);

    @Select("SELECT * FROM yak_ops_standard_field WHERE workspace_id=#{workspaceId} AND id=#{id}")
    StandardFieldEntity field(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert(
            "INSERT INTO yak_ops_standard_field(id,workspace_id,category_id,cn_name,en_code,data_type,length_precision,is_not_null,security_level,owner_id,business_caliber,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{categoryId},#{cnName},#{enCode},#{dataType},#{lengthPrecision},#{isNotNull},#{securityLevel},#{ownerId},#{businessCaliber},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insertField(StandardFieldEntity node);

    @Update(
            "UPDATE yak_ops_standard_field SET cn_name=#{cnName},en_code=#{enCode},data_type=#{dataType},length_precision=#{lengthPrecision},is_not_null=#{isNotNull},security_level=#{securityLevel},owner_id=#{ownerId},business_caliber=#{businessCaliber},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int updateField(StandardFieldEntity node);

    @Delete(
            "DELETE FROM yak_ops_standard_field WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int deleteField(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_standard_field WHERE en_code=#{enCode} AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(@Param("enCode") String enCode, @Param("excludeId") String excludeId);

    // en_code is ascii_bin while bound parameters arrive as utf8mb4; CONVERT keeps the LIKE collation-compatible.
    @Select(
            "SELECT * FROM yak_ops_standard_field WHERE workspace_id=#{workspaceId} AND (#{query.categoryId} IS NULL OR category_id=#{query.categoryId}) AND (#{query.keyword} IS NULL OR cn_name LIKE #{query.keyword} ESCAPE '!' OR CONVERT(en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!') ORDER BY update_time DESC,id DESC LIMIT #{query.offset},#{query.pageSize}")
    List<StandardFieldEntity> fields(@Param("workspaceId") String workspaceId, @Param("query") FieldQuery query);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_standard_field WHERE workspace_id=#{workspaceId} AND (#{query.categoryId} IS NULL OR category_id=#{query.categoryId}) AND (#{query.keyword} IS NULL OR cn_name LIKE #{query.keyword} ESCAPE '!' OR CONVERT(en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!')")
    long countFields(@Param("workspaceId") String workspaceId, @Param("query") FieldQuery query);
}
