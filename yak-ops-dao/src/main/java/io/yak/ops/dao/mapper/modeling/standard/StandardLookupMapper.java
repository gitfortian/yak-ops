package io.yak.ops.dao.mapper.modeling.standard;

import io.yak.ops.dao.entity.modeling.standard.StandardLookupEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardLookupValueEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardQuery;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for lookup tables and their ordered values. */
@Mapper
public interface StandardLookupMapper {
    @Select("SELECT l.*,(SELECT COUNT(*) FROM yak_ops_standard_lookup_value v WHERE v.lookup_id=l.id) AS value_count "
            + "FROM yak_ops_standard_lookup l WHERE l.workspace_id=#{workspaceId} "
            + "AND (#{query.categoryId} IS NULL OR l.category_id=#{query.categoryId}) "
            + "AND (#{query.keyword} IS NULL OR l.cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(l.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!' "
            + "OR l.en_name LIKE #{query.keyword} ESCAPE '!') "
            + "ORDER BY l.update_time DESC,l.id DESC LIMIT #{query.offset},#{query.pageSize}")
    List<StandardLookupEntity> page(@Param("workspaceId") String workspaceId, @Param("query") StandardQuery query);

    @Select("SELECT COUNT(*) FROM yak_ops_standard_lookup l WHERE l.workspace_id=#{workspaceId} "
            + "AND (#{query.categoryId} IS NULL OR l.category_id=#{query.categoryId}) "
            + "AND (#{query.keyword} IS NULL OR l.cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(l.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!' "
            + "OR l.en_name LIKE #{query.keyword} ESCAPE '!')")
    long count(@Param("workspaceId") String workspaceId, @Param("query") StandardQuery query);

    @Select("SELECT * FROM yak_ops_standard_lookup WHERE workspace_id=#{workspaceId} AND id=#{id}")
    StandardLookupEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT * FROM yak_ops_standard_lookup_value WHERE lookup_id=#{lookupId} ORDER BY ordinal")
    List<StandardLookupValueEntity> values(@Param("lookupId") String lookupId);

    @Insert(
            "INSERT INTO yak_ops_standard_lookup(id,workspace_id,category_id,en_code,cn_name,en_name,description,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{categoryId},#{enCode},#{cnName},#{enName},#{description},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insert(StandardLookupEntity node);

    @Update(
            "UPDATE yak_ops_standard_lookup SET en_code=#{enCode},cn_name=#{cnName},en_name=#{enName},description=#{description},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(StandardLookupEntity node);

    @Delete(
            "DELETE FROM yak_ops_standard_lookup WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Delete("DELETE FROM yak_ops_standard_lookup_value WHERE lookup_id=#{lookupId}")
    int deleteValues(@Param("lookupId") String lookupId);

    @Insert({
        "<script>",
        "INSERT INTO yak_ops_standard_lookup_value",
        "(id,workspace_id,lookup_id,ordinal,value_code,cn_name,en_name,meaning)",
        "VALUES",
        "<foreach collection='rows' item='row' separator=','>",
        "(#{row.id},#{row.workspaceId},#{row.lookupId},#{row.ordinal},#{row.valueCode},#{row.cnName},#{row.enName},#{row.meaning})",
        "</foreach>",
        "</script>"
    })
    int insertValues(@Param("rows") List<StandardLookupValueEntity> rows);

    @Select("SELECT COUNT(*) FROM yak_ops_standard_lookup WHERE workspace_id=#{workspaceId} AND en_code=#{enCode} "
            + "AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(
            @Param("workspaceId") String workspaceId,
            @Param("enCode") String enCode,
            @Param("excludeId") String excludeId);
}
