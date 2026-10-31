package io.yak.ops.business.governance.retention;

import io.yak.ops.dao.mapper.governance.GovernanceRetentionMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly retention cleanup of the governance history.
 *
 * <p>Scan runs, rule results and health snapshots are kept for 90 days; resolved issues and their
 * events for 180 days after the resolution. Every table is deleted in bounded chunks, and the job
 * stops after a fixed number of chunks per table so one oversized backlog cannot hold the metadata
 * database for a whole night — the remainder waits for the next night. Open, handling and ignored
 * issues are unreachable from the delete statements.
 */
@Component
public class GovernanceRetentionJob {

    /** Rows deleted per statement execution. */
    private static final int CHUNK_SIZE = 500;

    /** Safety bound of chunk executions per table and night. */
    private static final int MAX_CHUNKS_PER_TABLE = 200;

    /** Retention window of scan runs, rule results and health snapshots. */
    private static final int HISTORY_DAYS = 90;

    /** Retention window of resolved issues after their resolution. */
    private static final int RESOLVED_DAYS = 180;

    private static final Logger LOG = LoggerFactory.getLogger(GovernanceRetentionJob.class);

    private final GovernanceRetentionMapper retentionMapper;

    public GovernanceRetentionJob(GovernanceRetentionMapper retentionMapper) {
        this.retentionMapper = retentionMapper;
    }

    /** Runs after the nightly scan window, deleting one bounded chunk chain per table. */
    @Scheduled(cron = "${yak.governance.retention.cron:0 40 1 * * *}", zone = "UTC")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        int scans = deleteScanRunChunks(now.minusDays(HISTORY_DAYS));
        int snapshots = deleteSnapshotChunks(now.minusDays(HISTORY_DAYS).toLocalDate());
        int resolved = deleteResolvedChunks(now.minusDays(RESOLVED_DAYS));

        if (scans > 0 || snapshots > 0 || resolved > 0) {
            LOG.info(
                    "governance retention deleted {} scan run(s), {} health snapshot(s) and {} resolved issue(s)",
                    scans,
                    snapshots,
                    resolved);
        }
    }

    private int deleteScanRunChunks(LocalDateTime cutoff) {
        int total = 0;
        for (int chunk = 0; chunk < MAX_CHUNKS_PER_TABLE; chunk++) {
            int deleted = retentionMapper.deleteScanRunsBefore(cutoff, CHUNK_SIZE);
            total += deleted;
            if (deleted < CHUNK_SIZE) return total;
        }
        return total;
    }

    private int deleteSnapshotChunks(LocalDate cutoff) {
        int total = 0;
        for (int chunk = 0; chunk < MAX_CHUNKS_PER_TABLE; chunk++) {
            int deleted = retentionMapper.deleteHealthSnapshotsBefore(cutoff, CHUNK_SIZE);
            total += deleted;
            if (deleted < CHUNK_SIZE) return total;
        }
        return total;
    }

    private int deleteResolvedChunks(LocalDateTime cutoff) {
        int total = 0;
        for (int chunk = 0; chunk < MAX_CHUNKS_PER_TABLE; chunk++) {
            int deleted = retentionMapper.deleteResolvedIssuesBefore(cutoff, CHUNK_SIZE);
            total += deleted;
            if (deleted < CHUNK_SIZE) return total;
        }
        return total;
    }
}
