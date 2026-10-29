package io.yak.ops.dao.mapper.governance;

import io.yak.ops.dao.entity.governance.GovernanceScanRunEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for governance scan runs. */
@Mapper
public interface GovernanceScanRunMapper {

    String COLUMNS = "id,workspace_id,trigger_type,status,active_scope,rule_count,succeeded_rule_count,"
            + "failed_rule_count,issue_created_count,issue_reopened_count,issue_resolved_count,reason_code,"
            + "start_time,finish_time,create_time,create_by";

    @Insert("INSERT INTO yak_ops_governance_scan_run (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{triggerType},#{status},#{activeScope},#{ruleCount},#{succeededRuleCount},"
            + "#{failedRuleCount},#{issueCreatedCount},#{issueReopenedCount},#{issueResolvedCount},#{reasonCode},"
            + "#{startTime},#{finishTime},#{createTime},#{createBy})")
    int insert(GovernanceScanRunEntity row);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_scan_run "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    GovernanceScanRunEntity findById(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /**
     * Returns the live run of a Workspace.
     *
     * <p>The unique active key allows at most one row, so this never needs an ordering clause.
     */
    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_scan_run "
            + "WHERE workspace_id=#{workspaceId} AND active_scope=1")
    GovernanceScanRunEntity findActive(@Param("workspaceId") String workspaceId);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_scan_run "
            + "WHERE workspace_id=#{workspaceId} ORDER BY create_time DESC, id DESC LIMIT #{limit}")
    List<GovernanceScanRunEntity> listRecent(@Param("workspaceId") String workspaceId, @Param("limit") int limit);

    /** Claims a pending run; the conditional predicate makes two workers race on one row at most once. */
    @Update("UPDATE yak_ops_governance_scan_run SET status='RUNNING', start_time=#{startTime} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status='PENDING' AND active_scope=1")
    int claim(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("startTime") LocalDateTime startTime);

    @Update("UPDATE yak_ops_governance_scan_run SET status=#{status}, active_scope=NULL, "
            + "succeeded_rule_count=#{succeededRuleCount}, failed_rule_count=#{failedRuleCount}, "
            + "issue_created_count=#{issueCreatedCount}, issue_reopened_count=#{issueReopenedCount}, "
            + "issue_resolved_count=#{issueResolvedCount}, reason_code=#{reasonCode}, finish_time=#{finishTime} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status='RUNNING'")
    int finish(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("status") String status,
            @Param("succeededRuleCount") int succeededRuleCount,
            @Param("failedRuleCount") int failedRuleCount,
            @Param("issueCreatedCount") int issueCreatedCount,
            @Param("issueReopenedCount") int issueReopenedCount,
            @Param("issueResolvedCount") int issueResolvedCount,
            @Param("reasonCode") String reasonCode,
            @Param("finishTime") LocalDateTime finishTime);

    /** Marks every live run of the workspace FAILED; the single node cannot still be executing them. */
    @Update("UPDATE yak_ops_governance_scan_run SET status='FAILED', active_scope=NULL, "
            + "reason_code=#{reasonCode}, finish_time=#{finishTime} "
            + "WHERE workspace_id=#{workspaceId} AND active_scope=1")
    int markLiveFailed(
            @Param("workspaceId") String workspaceId,
            @Param("reasonCode") String reasonCode,
            @Param("finishTime") LocalDateTime finishTime);
}
