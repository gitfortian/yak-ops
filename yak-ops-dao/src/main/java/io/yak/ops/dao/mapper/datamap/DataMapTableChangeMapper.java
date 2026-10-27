package io.yak.ops.dao.mapper.datamap;

import io.yak.ops.dao.entity.datamap.DataMapTableChangeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Append-only statements for crawl-detected asset changes. */
@Mapper
public interface DataMapTableChangeMapper {

    @Insert("INSERT INTO yak_ops_data_map_table_change (id,workspace_id,table_id,change_type,detail,created_time) "
            + "VALUES (#{id},#{workspaceId},#{tableId},#{changeType},#{detail},#{createdTime})")
    int insert(DataMapTableChangeEntity row);

    @Select("SELECT id,workspace_id,table_id,change_type,detail,created_time FROM yak_ops_data_map_table_change "
            + "WHERE workspace_id=#{workspaceId} AND table_id=#{tableId} "
            + "ORDER BY created_time DESC, id DESC LIMIT #{limit}")
    List<DataMapTableChangeEntity> listRecent(
            @Param("workspaceId") String workspaceId, @Param("tableId") String tableId, @Param("limit") int limit);
}
