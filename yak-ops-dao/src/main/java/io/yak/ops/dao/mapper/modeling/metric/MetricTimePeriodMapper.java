package io.yak.ops.dao.mapper.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricTimePeriodEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for Workspace time periods. */
@Mapper
public interface MetricTimePeriodMapper {
    @Select("SELECT * FROM yak_ops_modeling_metric_time_period WHERE workspace_id=#{workspaceId} "
            + "AND (#{query.keyword} IS NULL OR cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!') "
            + "ORDER BY update_time DESC,id DESC LIMIT #{query.offset},#{query.pageSize}")
    List<MetricTimePeriodEntity> page(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_metric_time_period WHERE workspace_id=#{workspaceId} "
            + "AND (#{query.keyword} IS NULL OR cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!')")
    long count(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select("SELECT * FROM yak_ops_modeling_metric_time_period WHERE workspace_id=#{workspaceId} AND id=#{id}")
    MetricTimePeriodEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert(
            "INSERT INTO yak_ops_modeling_metric_time_period(id,workspace_id,en_code,cn_name,description,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{enCode},#{cnName},#{description},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insert(MetricTimePeriodEntity node);

    @Update(
            "UPDATE yak_ops_modeling_metric_time_period SET en_code=#{enCode},cn_name=#{cnName},description=#{description},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(MetricTimePeriodEntity node);

    @Delete(
            "DELETE FROM yak_ops_modeling_metric_time_period WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_modeling_metric_time_period WHERE workspace_id=#{workspaceId} AND en_code=#{enCode} "
                    + "AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(
            @Param("workspaceId") String workspaceId,
            @Param("enCode") String enCode,
            @Param("excludeId") String excludeId);
}
