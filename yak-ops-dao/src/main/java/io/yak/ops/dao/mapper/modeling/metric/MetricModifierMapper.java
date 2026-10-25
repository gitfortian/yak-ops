package io.yak.ops.dao.mapper.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricModifierEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for Workspace modifiers anchored to data domains. */
@Mapper
public interface MetricModifierMapper {
    String PROJECTION = "SELECT m.*,dom.name AS domain_name "
            + "FROM yak_ops_modeling_metric_modifier m "
            + "LEFT JOIN yak_ops_modeling_domain dom ON dom.workspace_id=m.workspace_id AND dom.id=m.domain_id ";

    @Select(PROJECTION + "WHERE m.workspace_id=#{workspaceId} "
            + "AND (#{query.domainId} IS NULL OR m.domain_id=#{query.domainId}) "
            + "AND (#{query.keyword} IS NULL OR m.cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(m.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!') "
            + "ORDER BY m.update_time DESC,m.id DESC LIMIT #{query.offset},#{query.pageSize}")
    List<MetricModifierEntity> page(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_metric_modifier m WHERE m.workspace_id=#{workspaceId} "
            + "AND (#{query.domainId} IS NULL OR m.domain_id=#{query.domainId}) "
            + "AND (#{query.keyword} IS NULL OR m.cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(m.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!')")
    long count(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select(PROJECTION + "WHERE m.workspace_id=#{workspaceId} AND m.id=#{id}")
    MetricModifierEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert(
            "INSERT INTO yak_ops_modeling_metric_modifier(id,workspace_id,domain_id,en_code,cn_name,description,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{domainId},#{enCode},#{cnName},#{description},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insert(MetricModifierEntity node);

    @Update(
            "UPDATE yak_ops_modeling_metric_modifier SET domain_id=#{domainId},en_code=#{enCode},cn_name=#{cnName},description=#{description},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(MetricModifierEntity node);

    @Delete(
            "DELETE FROM yak_ops_modeling_metric_modifier WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_modeling_metric_modifier WHERE workspace_id=#{workspaceId} AND en_code=#{enCode} "
                    + "AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(
            @Param("workspaceId") String workspaceId,
            @Param("enCode") String enCode,
            @Param("excludeId") String excludeId);
}
