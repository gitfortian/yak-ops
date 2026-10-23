package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.dao.entity.modeling.DimensionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for Workspace concept dimensions. */
@Mapper
public interface DimensionMapper {
    String PROJECTION = "SELECT d.id,d.workspace_id,d.scope,d.domain_id,d.datamart_id,d.name,d.en_name,d.code,"
            + "d.description,d.revision,d.create_time,d.update_time,d.create_by,d.update_by,"
            + "dom.name AS domain_name, ds.name AS datamart_name "
            + "FROM yak_ops_modeling_dimension d "
            + "LEFT JOIN yak_ops_modeling_domain dom ON dom.workspace_id=d.workspace_id AND dom.id=d.domain_id "
            + "LEFT JOIN yak_ops_modeling_datamart ds ON ds.workspace_id=d.workspace_id AND ds.id=d.datamart_id ";

    @Select({
        "<script>",
        PROJECTION,
        "WHERE d.workspace_id=#{workspaceId}",
        "<if test='scope != null'> AND d.scope=#{scope}</if>",
        "<if test='domainId != null'> AND d.domain_id=#{domainId}</if>",
        "<if test='datamartId != null'> AND d.datamart_id=#{datamartId}</if>",
        "ORDER BY d.code,d.id",
        "</script>"
    })
    List<DimensionEntity> dimensions(
            @Param("workspaceId") String workspaceId,
            @Param("scope") DimensionScope scope,
            @Param("domainId") String domainId,
            @Param("datamartId") String datamartId);

    @Select(PROJECTION + "WHERE d.workspace_id=#{workspaceId} AND d.id=#{id}")
    DimensionEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT * FROM yak_ops_modeling_dimension WHERE workspace_id=#{workspaceId} AND id=#{id} FOR UPDATE")
    DimensionEntity lock(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_dimension WHERE workspace_id=#{workspaceId} "
            + "AND owner_scope_id=#{ownerScopeId} AND code=#{code} "
            + "AND (#{excludeId} IS NULL OR id<>#{excludeId})")
    long countCode(
            @Param("workspaceId") String workspaceId,
            @Param("ownerScopeId") String ownerScopeId,
            @Param("code") String code,
            @Param("excludeId") String excludeId);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_domain WHERE workspace_id=#{workspaceId} AND id=#{id}")
    long domain(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_datamart WHERE workspace_id=#{workspaceId} AND id=#{id}")
    long datamart(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert("INSERT INTO yak_ops_modeling_dimension(id,workspace_id,scope,domain_id,datamart_id,name,en_name,code,"
            + "description,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{scope},"
            + "#{domainId},#{datamartId},#{name},#{enName},#{code},#{description},#{revision},#{createTime},"
            + "#{updateTime},#{createBy},#{updateBy})")
    int insert(DimensionEntity row);

    @Update("UPDATE yak_ops_modeling_dimension SET name=#{name},en_name=#{enName},code=#{code},"
            + "description=#{description},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(DimensionEntity row);

    @Delete("DELETE FROM yak_ops_modeling_dimension WHERE workspace_id=#{workspaceId} AND id=#{id} "
            + "AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);
}
