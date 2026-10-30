package io.yak.ops.dao.mapper.governance;

import io.yak.ops.dao.entity.governance.GovernanceIssueEventEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Append-only statements for the transition history of an Issue. No update path exists. */
@Mapper
public interface GovernanceIssueEventMapper {

    String COLUMNS = "id,workspace_id,issue_id,event_type,from_status,to_status,owner_id,ignore_until,note,"
            + "actor_id,create_time";

    @Insert("INSERT INTO yak_ops_governance_issue_event (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{issueId},#{eventType},#{fromStatus},#{toStatus},#{ownerId},#{ignoreUntil},"
            + "#{note},#{actorId},#{createTime})")
    int insert(GovernanceIssueEventEntity row);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_issue_event "
            + "WHERE workspace_id=#{workspaceId} AND issue_id=#{issueId} ORDER BY create_time DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<GovernanceIssueEventEntity> pageByIssue(
            @Param("workspaceId") String workspaceId,
            @Param("issueId") String issueId,
            @Param("offset") int offset,
            @Param("limit") int limit);
}
