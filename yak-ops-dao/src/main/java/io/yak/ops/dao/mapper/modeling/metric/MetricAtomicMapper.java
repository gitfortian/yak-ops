package io.yak.ops.dao.mapper.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricAtomicEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for Workspace atomic metrics. */
@Mapper
public interface MetricAtomicMapper {
    String PROJECTION = "SELECT a.*,p.name AS process_name,u.en_code AS measure_unit_en_code "
            + "FROM yak_ops_modeling_metric_atomic a "
            + "LEFT JOIN yak_ops_modeling_process p ON p.workspace_id=a.workspace_id AND p.id=a.process_id "
            + "LEFT JOIN yak_ops_standard_measure_unit u ON u.workspace_id=a.workspace_id AND u.id=a.measure_unit_id ";

    @Select(PROJECTION + "WHERE a.workspace_id=#{workspaceId} "
            + "AND (#{query.processId} IS NULL OR a.process_id=#{query.processId}) "
            + "AND (#{query.keyword} IS NULL OR a.cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(a.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!') "
            + "ORDER BY a.update_time DESC,a.id DESC LIMIT #{query.offset},#{query.pageSize}")
    List<MetricAtomicEntity> page(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_metric_atomic a WHERE a.workspace_id=#{workspaceId} "
            + "AND (#{query.processId} IS NULL OR a.process_id=#{query.processId}) "
            + "AND (#{query.keyword} IS NULL OR a.cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(a.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!')")
    long count(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select(PROJECTION + "WHERE a.workspace_id=#{workspaceId} AND a.id=#{id}")
    MetricAtomicEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert(
            "INSERT INTO yak_ops_modeling_metric_atomic(id,workspace_id,process_id,en_code,cn_name,data_type,aggregate_type,measure_unit_id,owner_id,description,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{processId},#{enCode},#{cnName},#{dataType},#{aggregateType},#{measureUnitId},#{ownerId},#{description},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insert(MetricAtomicEntity node);

    @Update(
            "UPDATE yak_ops_modeling_metric_atomic SET process_id=#{processId},en_code=#{enCode},cn_name=#{cnName},data_type=#{dataType},aggregate_type=#{aggregateType},measure_unit_id=#{measureUnitId},owner_id=#{ownerId},description=#{description},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(MetricAtomicEntity node);

    @Delete(
            "DELETE FROM yak_ops_modeling_metric_atomic WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_modeling_metric_atomic WHERE workspace_id=#{workspaceId} AND en_code=#{enCode} "
                    + "AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(
            @Param("workspaceId") String workspaceId,
            @Param("enCode") String enCode,
            @Param("excludeId") String excludeId);
}
