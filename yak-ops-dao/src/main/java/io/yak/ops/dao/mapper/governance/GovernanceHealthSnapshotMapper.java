package io.yak.ops.dao.mapper.governance;

import io.yak.ops.dao.entity.governance.GovernanceHealthSnapshotEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Statements for the daily score rows of one Workspace. */
@Mapper
public interface GovernanceHealthSnapshotMapper {

    String COLUMNS = "id,workspace_id,dimension_code,snapshot_date,score,base_count,weighted_issue_count,"
            + "issue_count,high_count,medium_count,low_count,evaluated_dimension_count,formula_version,scan_id,"
            + "create_time,update_time";

    @Insert("INSERT INTO yak_ops_governance_health_snapshot (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{dimensionCode},#{snapshotDate},#{score},#{baseCount},#{weightedIssueCount},"
            + "#{issueCount},#{highCount},#{mediumCount},#{lowCount},#{evaluatedDimensionCount},#{formulaVersion},"
            + "#{scanId},#{createTime},#{updateTime})")
    int insert(GovernanceHealthSnapshotEntity row);

    /** Removes the rows of one day so a later run replaces the set instead of stacking a second one. */
    @Delete("DELETE FROM yak_ops_governance_health_snapshot "
            + "WHERE workspace_id=#{workspaceId} AND snapshot_date=#{snapshotDate}")
    int deleteByDay(@Param("workspaceId") String workspaceId, @Param("snapshotDate") LocalDate snapshotDate);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_health_snapshot "
            + "WHERE workspace_id=#{workspaceId} AND snapshot_date BETWEEN #{from} AND #{to} "
            + "ORDER BY snapshot_date, dimension_code")
    List<GovernanceHealthSnapshotEntity> listByRange(
            @Param("workspaceId") String workspaceId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_governance_health_snapshot "
            + "WHERE workspace_id=#{workspaceId} AND snapshot_date=#{snapshotDate}")
    List<GovernanceHealthSnapshotEntity> listByDay(
            @Param("workspaceId") String workspaceId, @Param("snapshotDate") LocalDate snapshotDate);
}
