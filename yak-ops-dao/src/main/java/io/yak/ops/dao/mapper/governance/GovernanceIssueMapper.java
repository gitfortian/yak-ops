package io.yak.ops.dao.mapper.governance;

import io.yak.ops.dao.entity.governance.GovernanceIssueEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Parameter-bound statements for the current Issue of one target.
 *
 * <p>Identity is {@code (workspace_id, rule_code, target_type, target_id)}; status, owner, evidence
 * and timestamps are mutable columns of that row.
 */
@Mapper
public interface GovernanceIssueMapper {

    String COLUMNS = "id,workspace_id,rule_code,rule_version,dimension_code,severity,target_type,target_id,"
            + "target_name,status,owner_id,ignore_until,resolved_at,resolved_by,resolution,observed_value,"
            + "threshold_value,sample_count,window_days,first_detected_at,last_detected_at,last_scan_id,revision,"
            + "create_time,update_time,create_by,update_by";

    /** Filter shared by the page and count statements; blank values are not applied. */
    String FILTER = "<where>"
            + "<if test='q.status != null and q.status != \"\"'> AND status = #{q.status}</if>"
            + "<if test='q.severity != null and q.severity != \"\"'> AND severity = #{q.severity}</if>"
            + "<if test='q.dimension != null and q.dimension != \"\"'> AND dimension_code = #{q.dimension}</if>"
            + "<if test='q.ruleCode != null and q.ruleCode != \"\"'> AND rule_code = #{q.ruleCode}</if>"
            + "<if test='q.ownerId != null and q.ownerId != \"\"'> AND owner_id = #{q.ownerId}</if>"
            + "<if test='q.targetType != null and q.targetType != \"\"'> AND target_type = #{q.targetType}</if>"
            + "<if test='q.targetId != null and q.targetId != \"\"'> AND target_id = #{q.targetId}</if>"
            + "<if test='q.keyword != null and q.keyword != \"\"'>"
            + " AND target_name LIKE CONCAT('%', #{q.keyword}, '%')</if>"
            + "</where>";

    /** Board order: severity first, then the most recently touched Issue. */
    String BOARD_ORDER = " ORDER BY FIELD(severity, 'HIGH', 'MEDIUM', 'LOW'), update_time DESC, id DESC";

    @Insert("INSERT INTO yak_ops_governance_issue (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{ruleCode},#{ruleVersion},#{dimensionCode},#{severity},#{targetType},"
            + "#{targetId},#{targetName},#{status},#{ownerId},#{ignoreUntil},#{resolvedAt},#{resolvedBy},"
            + "#{resolution},#{observedValue},#{thresholdValue},#{sampleCount},#{windowDays},#{firstDetectedAt},"
            + "#{lastDetectedAt},#{lastScanId},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insert(GovernanceIssueEntity row);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_issue " + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    GovernanceIssueEntity findById(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Reads the target row and holds it until the surrounding transaction commits. */
    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_issue "
            + "WHERE workspace_id=#{workspaceId} AND rule_code=#{ruleCode} AND target_type=#{targetType} "
            + "AND target_id=#{targetId} FOR UPDATE")
    GovernanceIssueEntity lockByTarget(
            @Param("workspaceId") String workspaceId,
            @Param("ruleCode") String ruleCode,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);

    /**
     * Refreshes evidence and detection time without touching the lifecycle.
     *
     * <p>Used while the Issue stays OPEN, HANDLING, or IGNORED inside its window.
     */
    @Update("UPDATE yak_ops_governance_issue SET target_name=#{targetName}, rule_version=#{ruleVersion}, "
            + "observed_value=#{observedValue}, threshold_value=#{thresholdValue}, sample_count=#{sampleCount}, "
            + "window_days=#{windowDays}, last_detected_at=#{lastDetectedAt}, last_scan_id=#{scanId}, "
            + "revision=revision+1, update_time=#{now}, update_by=#{actor} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int touch(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("targetName") String targetName,
            @Param("ruleVersion") int ruleVersion,
            @Param("observedValue") BigDecimal observedValue,
            @Param("thresholdValue") BigDecimal thresholdValue,
            @Param("sampleCount") Integer sampleCount,
            @Param("windowDays") Integer windowDays,
            @Param("lastDetectedAt") LocalDateTime lastDetectedAt,
            @Param("scanId") String scanId,
            @Param("now") LocalDateTime now,
            @Param("actor") String actor);

    /**
     * Reopens a RESOLVED Issue, or an IGNORED one whose window expired, and refreshes its evidence.
     *
     * <p>{@code first_detected_at} and the owner survive; the resolution fields and the ignore window
     * are cleared.
     */
    @Update("UPDATE yak_ops_governance_issue SET status='OPEN', target_name=#{targetName}, "
            + "rule_version=#{ruleVersion}, observed_value=#{observedValue}, threshold_value=#{thresholdValue}, "
            + "sample_count=#{sampleCount}, window_days=#{windowDays}, last_detected_at=#{lastDetectedAt}, "
            + "last_scan_id=#{scanId}, resolved_at=NULL, resolved_by=NULL, resolution=NULL, ignore_until=NULL, "
            + "revision=revision+1, update_time=#{now}, update_by=#{actor} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int reopenAndDetect(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("targetName") String targetName,
            @Param("ruleVersion") int ruleVersion,
            @Param("observedValue") BigDecimal observedValue,
            @Param("thresholdValue") BigDecimal thresholdValue,
            @Param("sampleCount") Integer sampleCount,
            @Param("windowDays") Integer windowDays,
            @Param("lastDetectedAt") LocalDateTime lastDetectedAt,
            @Param("scanId") String scanId,
            @Param("now") LocalDateTime now,
            @Param("actor") String actor);

    /** Targets of one rule that the current run did not observe; the only auto-close input. */
    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_issue "
            + "WHERE workspace_id=#{workspaceId} AND rule_code=#{ruleCode} AND status <> 'RESOLVED' "
            + "AND (last_scan_id IS NULL OR last_scan_id <> #{scanId})")
    List<GovernanceIssueEntity> listUnobserved(
            @Param("workspaceId") String workspaceId,
            @Param("ruleCode") String ruleCode,
            @Param("scanId") String scanId);

    @Update("UPDATE yak_ops_governance_issue SET status='RESOLVED', resolution='AUTO', resolved_by=#{actor}, "
            + "resolved_at=#{now}, ignore_until=NULL, revision=revision+1, update_time=#{now}, update_by=#{actor} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status <> 'RESOLVED'")
    int autoResolve(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("actor") String actor,
            @Param("now") LocalDateTime now);

    /** Severity counts of the score-bearing Issues of one dimension. */
    @Select("SELECT severity, COUNT(*) AS total FROM yak_ops_governance_issue "
            + "WHERE workspace_id=#{workspaceId} AND dimension_code=#{dimensionCode} "
            + "AND status IN ('OPEN', 'HANDLING') GROUP BY severity")
    List<GovernanceSeverityCountRow> countScoredBySeverity(
            @Param("workspaceId") String workspaceId, @Param("dimensionCode") String dimensionCode);

    /** Locks one Issue of the Workspace; every manual command validates the row it locked. */
    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_issue "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} FOR UPDATE")
    GovernanceIssueEntity lockById(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("<script>SELECT " + COLUMNS + " FROM yak_ops_governance_issue" + FILTER + BOARD_ORDER
            + " LIMIT #{q.limit} OFFSET #{q.offset}</script>")
    List<GovernanceIssueEntity> page(
            @Param("workspaceId") String workspaceId, @Param("q") GovernanceIssuePageQuery query);

    @Select("<script>SELECT COUNT(1) FROM yak_ops_governance_issue" + FILTER + "</script>")
    long count(@Param("workspaceId") String workspaceId, @Param("q") GovernanceIssuePageQuery query);

    /** Ignores whose window has expired; the scan opens them before evaluating any rule. */
    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_issue "
            + "WHERE workspace_id=#{workspaceId} AND status='IGNORED' AND ignore_until <= #{now}")
    List<GovernanceIssueEntity> listExpiredIgnored(
            @Param("workspaceId") String workspaceId, @Param("now") LocalDateTime now);

    @Update("UPDATE yak_ops_governance_issue SET status='OPEN', ignore_until=NULL, revision=revision+1, "
            + "update_time=#{now}, update_by=#{actor} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status='IGNORED'")
    int reopenExpired(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("now") LocalDateTime now,
            @Param("actor") String actor);

    @Update("UPDATE yak_ops_governance_issue SET status='HANDLING', owner_id=#{ownerId}, "
            + "ignore_until=NULL, revision=revision+1, update_time=#{now}, update_by=#{actor} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status IN ('OPEN', 'HANDLING')")
    int assign(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("now") LocalDateTime now,
            @Param("actor") String actor);

    @Update("UPDATE yak_ops_governance_issue SET status='RESOLVED', resolution='USER', "
            + "resolved_by=#{actor}, resolved_at=#{now}, ignore_until=NULL, revision=revision+1, "
            + "update_time=#{now}, update_by=#{actor} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status <> 'RESOLVED'")
    int resolve(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("now") LocalDateTime now,
            @Param("actor") String actor);

    @Update("UPDATE yak_ops_governance_issue SET status='OPEN', resolved_at=NULL, resolved_by=NULL, "
            + "resolution=NULL, revision=revision+1, update_time=#{now}, update_by=#{actor} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status='RESOLVED'")
    int reopen(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("now") LocalDateTime now,
            @Param("actor") String actor);

    @Update("UPDATE yak_ops_governance_issue SET status='IGNORED', ignore_until=#{ignoreUntil}, "
            + "revision=revision+1, update_time=#{now}, update_by=#{actor} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} "
            + "AND status IN ('OPEN', 'HANDLING', 'IGNORED')")
    int ignore(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("ignoreUntil") LocalDateTime ignoreUntil,
            @Param("now") LocalDateTime now,
            @Param("actor") String actor);

    @Update("UPDATE yak_ops_governance_issue SET status='OPEN', ignore_until=NULL, revision=revision+1, "
            + "update_time=#{now}, update_by=#{actor} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status='IGNORED'")
    int unignore(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("now") LocalDateTime now,
            @Param("actor") String actor);

    @Select("SELECT status, COUNT(*) AS total FROM yak_ops_governance_issue "
            + "WHERE workspace_id=#{workspaceId} GROUP BY status")
    List<GovernanceStatusCountRow> countByStatus(@Param("workspaceId") String workspaceId);

    /** Score-bearing Issues per rule, so the overview distribution matches the score basis. */
    @Select("SELECT rule_code, COUNT(*) AS total FROM yak_ops_governance_issue "
            + "WHERE workspace_id=#{workspaceId} AND status IN ('OPEN', 'HANDLING') GROUP BY rule_code")
    List<GovernanceRuleCountRow> countScoredByRule(@Param("workspaceId") String workspaceId);
}
