package io.yak.ops.dao.mapper.governance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Chunked retention deletes of the governance tables.
 *
 * <p>Every statement deletes at most {@code limit} rows so the nightly job never runs one unbounded
 * {@code DELETE}. Rule results and issue events are removed by their foreign-key cascades when the
 * owning scan run or issue goes; open, handling and ignored issues are unreachable from these
 * statements by definition.
 */
@Mapper
public interface GovernanceRetentionMapper {

    /**
     * Deletes one chunk of scan runs created before the cutoff.
     *
     * <p>The per-rule results of a deleted run follow through the foreign-key cascade.
     *
     * @param cutoff boundary created time; older runs are eligible
     * @param limit maximum rows this chunk may delete
     * @return the number of scan runs deleted by this chunk
     */
    @Delete("DELETE FROM yak_ops_governance_scan_run "
            + "WHERE create_time < #{cutoff} AND active_scope IS NULL LIMIT #{limit}")
    int deleteScanRunsBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    /**
     * Deletes one chunk of health snapshots dated before the cutoff.
     *
     * @param cutoffDate boundary snapshot date; strictly older days are eligible
     * @param limit maximum rows this chunk may delete
     * @return the number of health snapshots deleted by this chunk
     */
    @Delete("DELETE FROM yak_ops_governance_health_snapshot " + "WHERE snapshot_date < #{cutoffDate} LIMIT #{limit}")
    int deleteHealthSnapshotsBefore(@Param("cutoffDate") LocalDate cutoffDate, @Param("limit") int limit);

    /**
     * Deletes one chunk of issues resolved before the cutoff.
     *
     * <p>Only terminal {@code RESOLVED} rows are eligible; open, handling and ignored issues are
     * never deleted. The append-only events of a deleted issue follow the foreign-key cascade.
     *
     * @param cutoff boundary resolution time; issues resolved earlier are eligible
     * @param limit maximum rows this chunk may delete
     * @return the number of issues deleted by this chunk
     */
    @Delete("DELETE FROM yak_ops_governance_issue "
            + "WHERE status = 'RESOLVED' AND resolved_at < #{cutoff} LIMIT #{limit}")
    int deleteResolvedIssuesBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
