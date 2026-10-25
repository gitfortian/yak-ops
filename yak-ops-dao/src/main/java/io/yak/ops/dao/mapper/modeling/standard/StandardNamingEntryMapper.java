package io.yak.ops.dao.mapper.modeling.standard;

import io.yak.ops.dao.entity.modeling.standard.StandardNamingEntryEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardQuery;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for the flat naming dictionary. */
@Mapper
public interface StandardNamingEntryMapper {
    @Select("SELECT * FROM yak_ops_standard_naming_entry WHERE workspace_id=#{workspaceId} "
            + "AND (#{query.keyword} IS NULL OR cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!' "
            + "OR en_name LIKE #{query.keyword} ESCAPE '!') "
            + "ORDER BY update_time DESC,id DESC LIMIT #{query.offset},#{query.pageSize}")
    List<StandardNamingEntryEntity> page(@Param("workspaceId") String workspaceId, @Param("query") StandardQuery query);

    @Select("SELECT COUNT(*) FROM yak_ops_standard_naming_entry WHERE workspace_id=#{workspaceId} "
            + "AND (#{query.keyword} IS NULL OR cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!' "
            + "OR en_name LIKE #{query.keyword} ESCAPE '!')")
    long count(@Param("workspaceId") String workspaceId, @Param("query") StandardQuery query);

    @Select("SELECT * FROM yak_ops_standard_naming_entry WHERE workspace_id=#{workspaceId} AND id=#{id}")
    StandardNamingEntryEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Reads every entry for name matching; the dictionary is small and read once per rule action. */
    @Select("SELECT * FROM yak_ops_standard_naming_entry WHERE workspace_id=#{workspaceId} ORDER BY en_code")
    List<StandardNamingEntryEntity> entries(String workspaceId);

    @Insert(
            "INSERT INTO yak_ops_standard_naming_entry(id,workspace_id,en_code,en_name,cn_name,description,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{enCode},#{enName},#{cnName},#{description},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insert(StandardNamingEntryEntity node);

    @Update(
            "UPDATE yak_ops_standard_naming_entry SET en_code=#{enCode},en_name=#{enName},cn_name=#{cnName},description=#{description},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(StandardNamingEntryEntity node);

    @Delete(
            "DELETE FROM yak_ops_standard_naming_entry WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_standard_naming_entry WHERE workspace_id=#{workspaceId} AND en_code=#{enCode} "
                    + "AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(
            @Param("workspaceId") String workspaceId,
            @Param("enCode") String enCode,
            @Param("excludeId") String excludeId);
}
