package io.yak.ops.dao.mapper.datamap;

import io.yak.ops.dao.entity.datamap.DataMapTableTagEntity;
import io.yak.ops.dao.entity.datamap.DataMapTagEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for Workspace tags and their asset associations. */
@Mapper
public interface DataMapTagMapper {

    String COLUMNS = "id,workspace_id,name,create_time,update_time,create_by,update_by";

    @Insert("INSERT INTO yak_ops_data_map_tag (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{name},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insert(DataMapTagEntity row);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_tag WHERE workspace_id=#{workspaceId} AND id=#{id}")
    DataMapTagEntity findById(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_tag WHERE workspace_id=#{workspaceId} AND name=#{name}")
    DataMapTagEntity findByName(@Param("workspaceId") String workspaceId, @Param("name") String name);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_tag WHERE workspace_id=#{workspaceId} ORDER BY name, id")
    List<DataMapTagEntity> listAll(@Param("workspaceId") String workspaceId);

    @Update("UPDATE yak_ops_data_map_tag SET name=#{name},update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int rename(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("name") String name,
            @Param("updateTime") LocalDateTime updateTime,
            @Param("updateBy") String updateBy);

    /** Detaches the tag from every asset through the foreign key. */
    @Delete("DELETE FROM yak_ops_data_map_tag WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert("INSERT INTO yak_ops_data_map_table_tag (workspace_id,table_id,tag_id,create_time,create_by) "
            + "VALUES (#{workspaceId},#{tableId},#{tagId},#{createTime},#{createBy})")
    int insertAssignment(DataMapTableTagEntity row);

    @Delete("DELETE FROM yak_ops_data_map_table_tag WHERE workspace_id=#{workspaceId} AND table_id=#{tableId}")
    int deleteAssignments(@Param("workspaceId") String workspaceId, @Param("tableId") String tableId);

    @Select("SELECT tag_id FROM yak_ops_data_map_table_tag WHERE workspace_id=#{workspaceId} AND table_id=#{tableId}")
    List<String> listTagIds(@Param("workspaceId") String workspaceId, @Param("tableId") String tableId);

    @Select("<script>SELECT workspace_id,table_id,tag_id,create_time,create_by FROM yak_ops_data_map_table_tag "
            + "WHERE workspace_id=#{workspaceId} AND table_id IN "
            + "<foreach collection=\"tableIds\" item=\"tableId\" open=\"(\" separator=\",\" close=\")\">#{tableId}"
            + "</foreach></script>")
    List<DataMapTableTagEntity> listAssignments(
            @Param("workspaceId") String workspaceId, @Param("tableIds") List<String> tableIds);
}
