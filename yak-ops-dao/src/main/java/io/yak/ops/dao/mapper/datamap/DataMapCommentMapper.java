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
            + "mention_ids,reply_count,create_time,update_time";

    @Insert("INSERT INTO yak_ops_data_map_comment (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{targetType},#{targetId},#{parentId},#{body},#{authorId},"
            + "#{authorName},#{mentionIds},#{replyCount},#{createTime},#{updateTime})")
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

    /** Reads replies of one parent, oldest first. */
    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_comment "
            + "WHERE parent_id=#{parentId} ORDER BY create_time ASC, id ASC")
    List<DataMapCommentEntity> repliesOf(@Param("parentId") String parentId);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_comment " + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    DataMapCommentEntity findById(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Increments the reply counter of the parent comment. */
    @Update("UPDATE yak_ops_data_map_comment SET reply_count=reply_count+1,update_time=NOW(3) "
            + "WHERE id=#{parentId}")
    int incrementReplyCount(@Param("parentId") String parentId);

    /** Decrements the reply counter of the parent comment. */
    @Update("UPDATE yak_ops_data_map_comment SET reply_count=GREATEST(reply_count-1,0),update_time=NOW(3) "
            + "WHERE id=#{parentId}")
    int decrementReplyCount(@Param("parentId") String parentId);

    /** Deletes one comment; reply and event cascades are declared by the migration. */
    @Delete("DELETE FROM yak_ops_data_map_comment WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Deletes all replies of one parent; used to compute the cascade count before deleting. */
    @Select("SELECT COUNT(*) FROM yak_ops_data_map_comment WHERE parent_id=#{parentId}")
    int countReplies(@Param("parentId") String parentId);

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

    /** Finds the read marker row for the caller. */
    @Select("SELECT id,workspace_id,target_id,user_id,last_read_time "
            + "FROM yak_ops_data_map_comment_read "
            + "WHERE workspace_id=#{workspaceId} AND target_id=#{targetId} AND user_id=#{userId}")
    DataMapCommentEntity findReadMarker(
            @Param("workspaceId") String workspaceId,
            @Param("targetId") String targetId,
            @Param("userId") String userId);

    /** Inserts the read marker if absent. */
    @Insert("INSERT INTO yak_ops_data_map_comment_read (id,workspace_id,target_id,user_id,last_read_time) "
            + "VALUES (#{id},#{workspaceId},#{targetId},#{userId},#{now}) "
            + "ON DUPLICATE KEY UPDATE last_read_time=VALUES(last_read_time)")
    int upsertReadMarker(
            @Param("id") String id,
            @Param("workspaceId") String workspaceId,
            @Param("targetId") String targetId,
            @Param("userId") String userId,
            @Param("now") LocalDateTime now);

    /** Deletes comments anchored to one asset; the data map cascade calls this. */
    @Delete("DELETE FROM yak_ops_data_map_comment WHERE workspace_id=#{workspaceId} AND target_id=#{targetId}")
    int deleteByTarget(@Param("workspaceId") String workspaceId, @Param("targetId") String targetId);
}
