package io.yak.ops.dao.mapper.datamap;

import io.yak.ops.dao.entity.datamap.DataMapViewEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Parameter-bound statements for per-member favourites and visit counters. */
@Mapper
public interface DataMapPersonalMapper {

    @Insert("INSERT IGNORE INTO yak_ops_data_map_favorite (workspace_id,user_id,table_id,created_time) "
            + "VALUES (#{workspaceId},#{userId},#{tableId},#{createdTime})")
    int insertFavorite(
            @Param("workspaceId") String workspaceId,
            @Param("userId") String userId,
            @Param("tableId") String tableId,
            @Param("createdTime") LocalDateTime createdTime);

    @Delete("DELETE FROM yak_ops_data_map_favorite "
            + "WHERE workspace_id=#{workspaceId} AND user_id=#{userId} AND table_id=#{tableId}")
    int deleteFavorite(
            @Param("workspaceId") String workspaceId, @Param("userId") String userId, @Param("tableId") String tableId);

    @Select("SELECT COUNT(*) FROM yak_ops_data_map_favorite "
            + "WHERE workspace_id=#{workspaceId} AND user_id=#{userId} AND table_id=#{tableId}")
    long favoriteExists(
            @Param("workspaceId") String workspaceId, @Param("userId") String userId, @Param("tableId") String tableId);

    @Select("SELECT table_id FROM yak_ops_data_map_favorite "
            + "WHERE workspace_id=#{workspaceId} AND user_id=#{userId} "
            + "ORDER BY created_time DESC, table_id LIMIT #{limit} OFFSET #{offset}")
    List<String> listFavoriteIds(
            @Param("workspaceId") String workspaceId,
            @Param("userId") String userId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM yak_ops_data_map_favorite WHERE workspace_id=#{workspaceId} AND user_id=#{userId}")
    long countFavorites(@Param("workspaceId") String workspaceId, @Param("userId") String userId);

    /**
     * Sums the visit counter of one asset across every viewer of the Workspace.
     *
     * @param workspaceId current Workspace
     * @param tableId asset identity
     * @return total visit count; zero when the asset has never been opened
     */
    @Select("SELECT COALESCE(SUM(view_count),0) FROM yak_ops_data_map_view "
            + "WHERE workspace_id=#{workspaceId} AND table_id=#{tableId}")
    long countTableViews(@Param("workspaceId") String workspaceId, @Param("tableId") String tableId);

    @Insert("INSERT INTO yak_ops_data_map_view "
            + "(workspace_id,user_id,table_id,view_count,first_viewed_time,last_viewed_time) "
            + "VALUES (#{workspaceId},#{userId},#{tableId},1,#{viewedTime},#{viewedTime}) "
            + "ON DUPLICATE KEY UPDATE view_count=view_count+1,last_viewed_time=VALUES(last_viewed_time)")
    int upsertView(
            @Param("workspaceId") String workspaceId,
            @Param("userId") String userId,
            @Param("tableId") String tableId,
            @Param("viewedTime") LocalDateTime viewedTime);

    @Select("SELECT workspace_id,user_id,table_id,view_count,first_viewed_time,last_viewed_time "
            + "FROM yak_ops_data_map_view WHERE workspace_id=#{workspaceId} AND user_id=#{userId} "
            + "ORDER BY last_viewed_time DESC, table_id LIMIT #{limit} OFFSET #{offset}")
    List<DataMapViewEntity> listRecentViews(
            @Param("workspaceId") String workspaceId,
            @Param("userId") String userId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM yak_ops_data_map_view WHERE workspace_id=#{workspaceId} AND user_id=#{userId}")
    long countRecent(@Param("workspaceId") String workspaceId, @Param("userId") String userId);

    @Select("SELECT v.table_id,SUM(v.view_count) AS view_count FROM yak_ops_data_map_view v "
            + "JOIN yak_ops_data_map_table t ON t.id=v.table_id AND t.workspace_id=v.workspace_id "
            + "WHERE v.workspace_id=#{workspaceId} AND t.status='ACTIVE' "
            + "GROUP BY v.table_id ORDER BY SUM(v.view_count) DESC, v.table_id LIMIT #{limit}")
    List<DataMapViewTotalRow> hotTables(@Param("workspaceId") String workspaceId, @Param("limit") int limit);
}
