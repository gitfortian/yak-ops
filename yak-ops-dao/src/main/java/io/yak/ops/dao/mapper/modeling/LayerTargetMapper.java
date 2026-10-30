package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.dao.entity.modeling.LayerTargetEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Parameter-bound statements for per-environment layer targets.
 *
 * <p>Bindings are replaced wholesale inside the caller transaction: one delete plus explicit
 * inserts keeps "unbind one environment" expressible without a diff protocol.
 */
@Mapper
public interface LayerTargetMapper {
    String PROJECTION = "SELECT t.id,t.workspace_id,t.layer_id,t.environment,t.data_source_id,"
            + "t.physical_database,t.physical_schema,t.create_time,t.update_time,t.create_by,t.update_by "
            + "FROM yak_ops_modeling_layer_target t ";

    @Select(PROJECTION + "WHERE t.workspace_id=#{workspaceId} AND t.layer_id=#{layerId} "
            + "AND t.environment=#{environment}")
    LayerTargetEntity find(
            @Param("workspaceId") String workspaceId,
            @Param("layerId") String layerId,
            @Param("environment") String environment);

    @Select(PROJECTION + "WHERE t.workspace_id=#{workspaceId} AND t.layer_id=#{layerId} " + "ORDER BY t.environment")
    List<LayerTargetEntity> listByLayer(@Param("workspaceId") String workspaceId, @Param("layerId") String layerId);

    @Insert("INSERT INTO yak_ops_modeling_layer_target(id,workspace_id,layer_id,environment,data_source_id,"
            + "physical_database,physical_schema,create_time,update_time,create_by,update_by) "
            + "VALUES(#{id},#{workspaceId},#{layerId},#{environment},#{dataSourceId},#{physicalDatabase},"
            + "#{physicalSchema},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insert(LayerTargetEntity row);

    @Delete("DELETE FROM yak_ops_modeling_layer_target " + "WHERE workspace_id=#{workspaceId} AND layer_id=#{layerId}")
    int deleteByLayer(@Param("workspaceId") String workspaceId, @Param("layerId") String layerId);
}
