package io.yak.ops.dao.mapper.datamap;

import io.yak.ops.dao.entity.datamap.DataMapCommentEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for asset comment threads. */
@Mapper
public interface DataMapCommentMapper {

    String COLUMNS = "id,workspace_id,target_type,target_id,parent_id,body,author_id,author_name,"
            + "reply_count,create_time,update_time,create_by,update_by";

    @Insert("INSERT INTO yak_ops_data_map_comment (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{targetType},#{targetId},#{parentId},#{body},#{authorId},"
            + "#{authorName},#{replyCount},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insert(DataMapCommentEntity row);

    /** Reads one top-level page of comments for the target, newest first. */
    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_comment "
            + "WHERE workspace_id=#{workspaceId} AND target_type=#{targetType} AND target_id=#{targetId} "
            + "AND parent_id IS NULL ORDER BY create_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<DataMapCommentEntity> pageTopLevel(
            @Param("workspaceId") String workspaceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /** Counts top-level comments for the target. */
    @Select("SELECT COUNT(*) FROM yak_ops_data_map_comment "
            + "WHERE workspace_id=#{workspaceId} AND target_type=#{targetType} AND target_id=#{targetId} "
            + "AND parent_id IS NULL")
    long countTopLevel(
            @Param("workspaceId") String workspaceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);

    /** Reads every reply of the target's threads, oldest first; the service groups them by parent. */
    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_comment "
            + "WHERE workspace_id=#{workspaceId} AND target_type=#{targetType} AND target_id=#{targetId} "
            + "AND parent_id IS NOT NULL ORDER BY create_time ASC, id ASC")
    List<DataMapCommentEntity> repliesOfTarget(
            @Param("workspaceId") String workspaceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_comment WHERE workspace_id=#{workspaceId} AND id=#{id}")
    DataMapCommentEntity findById(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Increments the reply counter of the parent comment. */
    @Update("UPDATE yak_ops_data_map_comment SET reply_count=reply_count+1,update_time=NOW(3) "
            + "WHERE id=#{parentId}")
    int incrementReplyCount(@Param("parentId") String parentId);

    /** Decrements the reply counter of the parent comment, never below zero. */
    @Update("UPDATE yak_ops_data_map_comment SET reply_count=GREATEST(reply_count-1,0),update_time=NOW(3) "
            + "WHERE id=#{parentId}")
    int decrementReplyCount(@Param("parentId") String parentId);

    /** Deletes one comment; deleting a top-level row cascades its replies via fk_dmc_parent. */
    @Delete("DELETE FROM yak_ops_data_map_comment WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Unread comment count for the caller: comments newer than the read marker, excluding the caller's own. */
    @Select("SELECT COUNT(*) FROM yak_ops_data_map_comment c "
            + "WHERE c.workspace_id=#{workspaceId} AND c.target_type=#{targetType} AND c.target_id=#{targetId} "
            + "AND c.author_id<>#{userId} "
            + "AND c.create_time > COALESCE((SELECT r.last_read_time FROM yak_ops_data_map_comment_read r "
            + "WHERE r.workspace_id=#{workspaceId} AND r.target_id=#{targetId} AND r.user_id=#{userId}),"
            + "'1970-01-01 00:00:00')")
    long unreadCount(
            @Param("workspaceId") String workspaceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("userId") String userId);

    /** Inserts the caller's read marker if absent, else moves it forward to {@code now}. */
    @Insert("INSERT INTO yak_ops_data_map_comment_read (id,workspace_id,target_id,user_id,last_read_time) "
            + "VALUES (#{id},#{workspaceId},#{targetId},#{userId},#{now}) "
            + "ON DUPLICATE KEY UPDATE last_read_time=VALUES(last_read_time)")
    int upsertReadMarker(
            @Param("id") String id,
            @Param("workspaceId") String workspaceId,
            @Param("targetId") String targetId,
            @Param("userId") String userId,
            @Param("now") LocalDateTime now);
}
