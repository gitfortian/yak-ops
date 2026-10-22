package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.dao.entity.modeling.PlanningNodeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;

/** Bound SQL for the planning aggregate; callers use the scoped repository contract. */
@Mapper
public interface PlanningMapper {
    @SelectProvider(type = PlanningSqlProvider.class, method = "tree")
    List<PlanningNodeEntity> tree(@Param("workspaceId") String workspaceId);

    @SelectProvider(type = PlanningSqlProvider.class, method = "find")
    PlanningNodeEntity find(
            @Param("workspaceId") String workspaceId,
            @Param("kind") NodeKind kind,
            @Param("id") String id,
            @Param("lock") boolean lock);

    @InsertProvider(type = PlanningSqlProvider.class, method = "insert")
    int insert(@Param("kind") NodeKind kind, @Param("node") PlanningNodeEntity node);

    @UpdateProvider(type = PlanningSqlProvider.class, method = "update")
    int update(@Param("kind") NodeKind kind, @Param("node") PlanningNodeEntity node, @Param("revision") long revision);

    @DeleteProvider(type = PlanningSqlProvider.class, method = "delete")
    int delete(
            @Param("workspaceId") String workspaceId,
            @Param("kind") NodeKind kind,
            @Param("id") String id,
            @Param("revision") long revision);

    @SelectProvider(type = PlanningSqlProvider.class, method = "children")
    String child(@Param("workspaceId") String workspaceId, @Param("kind") NodeKind kind, @Param("id") String id);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_modeling_code WHERE code=#{code} AND NOT (node_kind=#{kind} AND node_id=#{id})")
    long occupied(@Param("code") String code, @Param("kind") String kind, @Param("id") String id);

    @Insert(
            "INSERT INTO yak_ops_modeling_code(code,workspace_id,node_kind,node_id,create_time,create_by) VALUES(#{code},#{workspaceId},#{kind},#{id},#{createTime},#{createBy})")
    int reserve(PlanningNodeEntity node);

    @Update(
            "UPDATE yak_ops_modeling_code SET code=#{code} WHERE workspace_id=#{workspaceId} AND node_kind=#{kind} AND node_id=#{id}")
    int changeCode(PlanningNodeEntity node);

    @Delete(
            "DELETE FROM yak_ops_modeling_code WHERE workspace_id=#{workspaceId} AND node_kind=#{kind} AND node_id=#{id}")
    int release(@Param("workspaceId") String workspaceId, @Param("kind") NodeKind kind, @Param("id") String id);
}
