package io.yak.ops.dao.mapper.governance;

import io.yak.ops.dao.entity.governance.GovernanceRuleResultEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for per-rule outcomes of a scan run. */
@Mapper
public interface GovernanceRuleResultMapper {

    String COLUMNS = "id,workspace_id,scan_id,rule_code,rule_version,status,finding_count,"
            + "issue_created_count,issue_reopened_count,issue_resolved_count,reason_code,start_time,finish_time";

    @Insert("INSERT INTO yak_ops_governance_rule_result (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{scanId},#{ruleCode},#{ruleVersion},#{status},#{findingCount},"
            + "#{issueCreatedCount},#{issueReopenedCount},#{issueResolvedCount},#{reasonCode},#{startTime},"
            + "#{finishTime})")
    int insert(GovernanceRuleResultEntity row);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_rule_result "
            + "WHERE workspace_id=#{workspaceId} AND scan_id=#{scanId} ORDER BY start_time, rule_code")
    List<GovernanceRuleResultEntity> listByScan(
            @Param("workspaceId") String workspaceId, @Param("scanId") String scanId);

    @Update("UPDATE yak_ops_governance_rule_result SET status=#{status}, finding_count=#{findingCount}, "
            + "issue_created_count=#{issueCreatedCount}, issue_reopened_count=#{issueReopenedCount}, "
            + "issue_resolved_count=#{issueResolvedCount}, reason_code=#{reasonCode}, finish_time=#{finishTime} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status='RUNNING'")
    int complete(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("status") String status,
            @Param("findingCount") int findingCount,
            @Param("issueCreatedCount") int issueCreatedCount,
            @Param("issueReopenedCount") int issueReopenedCount,
            @Param("issueResolvedCount") int issueResolvedCount,
            @Param("reasonCode") String reasonCode,
            @Param("finishTime") LocalDateTime finishTime);

    /** Sweeps rule results left RUNNING by a previous process; only the startup recovery calls this. */
    @Update("UPDATE yak_ops_governance_rule_result SET status='FAILED', reason_code=#{reasonCode}, "
            + "finish_time=#{finishTime} WHERE workspace_id=#{workspaceId} AND status='RUNNING'")
    int markRunningFailed(
            @Param("workspaceId") String workspaceId,
            @Param("reasonCode") String reasonCode,
            @Param("finishTime") LocalDateTime finishTime);

    /** Fails every rule result left RUNNING by a previous process; startup recovery only. */
    @Update("UPDATE yak_ops_governance_rule_result SET status='FAILED', reason_code=#{reasonCode}, "
            + "finish_time=#{finishTime} WHERE status='RUNNING'")
    int markAllRunningFailed(@Param("reasonCode") String reasonCode, @Param("finishTime") LocalDateTime finishTime);
}
