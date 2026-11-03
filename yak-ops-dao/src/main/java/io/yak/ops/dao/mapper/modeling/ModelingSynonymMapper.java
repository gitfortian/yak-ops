package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.dao.entity.modeling.ModelingSynonymEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Parameter-bound statements for business synonyms of standards, model fields and metrics. Rows
 * are replaced as a whole set per target inside the owning service transaction.
 */
@Mapper
public interface ModelingSynonymMapper {
    @Select("SELECT id,workspace_id,target_type,target_id,term,revision,create_time,update_time,create_by,update_by "
            + "FROM yak_ops_modeling_synonym "
            + "WHERE workspace_id=#{workspaceId} AND target_type=#{targetType} AND target_id=#{targetId} "
            + "ORDER BY id")
    List<ModelingSynonymEntity> listByTarget(
            @Param("workspaceId") String workspaceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);

    @Select("<script>SELECT id,workspace_id,target_type,target_id,term,revision,create_time,update_time,"
            + "create_by,update_by FROM yak_ops_modeling_synonym "
            + "WHERE workspace_id=#{workspaceId} AND target_type=#{targetType} "
            + "AND target_id IN <foreach collection='targetIds' item='tid' open='(' separator=',' close=')'>#{tid}"
            + "</foreach></script>")
    List<ModelingSynonymEntity> listByTargets(
            @Param("workspaceId") String workspaceId,
            @Param("targetType") String targetType,
            @Param("targetIds") List<String> targetIds);

    @Insert("INSERT INTO yak_ops_modeling_synonym(id,workspace_id,target_type,target_id,term,revision,"
            + "create_time,update_time,create_by,update_by) "
            + "VALUES(#{id},#{workspaceId},#{targetType},#{targetId},#{term},#{revision},"
            + "#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insert(ModelingSynonymEntity row);

    @Delete("DELETE FROM yak_ops_modeling_synonym "
            + "WHERE workspace_id=#{workspaceId} AND target_type=#{targetType} AND target_id=#{targetId}")
    int deleteByTarget(
            @Param("workspaceId") String workspaceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);
}
