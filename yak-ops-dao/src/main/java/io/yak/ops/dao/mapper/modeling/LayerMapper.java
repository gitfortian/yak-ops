package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for Workspace data warehouse layers. */
@Mapper
public interface LayerMapper {
    @Select("SELECT * FROM yak_ops_modeling_layer WHERE workspace_id=#{workspaceId} ORDER BY builtin DESC,sort_no,id")
    List<LayerEntity> layers(String workspaceId);

    @Select("SELECT * FROM yak_ops_modeling_layer WHERE workspace_id=#{workspaceId} AND id=#{id}")
    LayerEntity layer(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_layer WHERE workspace_id=#{workspaceId} AND builtin=1")
    long builtinCount(String workspaceId);

    /**
     * Counts the logical models still attached to one layer.
     *
     * <p>Models cite their layer with {@code ON DELETE RESTRICT}, so a delete that skips this
     * check surfaces as a raw foreign-key violation instead of a business answer.
     */
    @Select("SELECT COUNT(*) FROM yak_ops_modeling_model WHERE workspace_id=#{workspaceId} AND layer_id=#{layerId}")
    long modelsInLayer(@Param("workspaceId") String workspaceId, @Param("layerId") String layerId);

    @Select("SELECT id FROM yak_ops_workspace WHERE id=#{workspaceId} FOR UPDATE")
    String lockWorkspace(@Param("workspaceId") String workspaceId);

    @Insert({
        "<script>",
        "INSERT INTO",
        "yak_ops_modeling_layer(id,workspace_id,en_code,cn_name,data_source_id,description,enabled,builtin,sort_no,revision,create_time,update_time,create_by,update_by)",
        "VALUES",
        "<foreach collection='rows' item='row' separator=','>",
        "(#{row.id},#{row.workspaceId},#{row.enCode},#{row.cnName},#{row.dataSourceId},#{row.description},#{row.enabled},#{row.builtin},#{row.sortNo},#{row.revision},#{row.createTime},#{row.updateTime},#{row.createBy},#{row.updateBy})",
        "</foreach>",
        "ON DUPLICATE KEY UPDATE id=id",
        "</script>"
    })
    int seed(@Param("rows") List<LayerEntity> rows);

    @Insert(
            "INSERT INTO yak_ops_modeling_layer(id,workspace_id,en_code,cn_name,data_source_id,description,enabled,builtin,sort_no,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{enCode},#{cnName},#{dataSourceId},#{description},#{enabled},#{builtin},#{sortNo},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insert(LayerEntity row);

    @Update(
            "UPDATE yak_ops_modeling_layer SET en_code=#{enCode},cn_name=#{cnName},data_source_id=#{dataSourceId},description=#{description},enabled=#{enabled},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(LayerEntity row);

    @Update(
            "UPDATE yak_ops_modeling_layer SET naming_rule_enabled=#{enabled},revision=revision+1,update_time=#{now},update_by=#{actor} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int updateNamingRule(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("enabled") boolean enabled,
            @Param("revision") long revision,
            @Param("actor") String actor,
            @Param("now") java.time.LocalDateTime now);

    @Delete(
            "DELETE FROM yak_ops_modeling_layer WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_modeling_layer WHERE workspace_id=#{workspaceId} AND en_code=#{enCode} AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(
            @Param("workspaceId") String workspaceId,
            @Param("enCode") String enCode,
            @Param("excludeId") String excludeId);

    @Select(
            "SELECT id,workspace_id,name,db_type,environment,conn_status FROM yak_ops_data_source WHERE workspace_id=#{workspaceId} ORDER BY name,id")
    List<DataSourceEntity> datasources(String workspaceId);

    @Select("SELECT COUNT(*) FROM yak_ops_data_source WHERE workspace_id=#{workspaceId} AND id=#{id}")
    long datasource(@Param("workspaceId") String workspaceId, @Param("id") String id);
}
