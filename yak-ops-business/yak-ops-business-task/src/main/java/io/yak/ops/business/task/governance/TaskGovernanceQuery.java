package io.yak.ops.business.task.governance;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only execution contract owned by the Task module.
 *
 * <p>Governance reads through it instead of the Task tables, so the Task module keeps sole ownership
 * of its schema and never depends on governance.
 */
public interface TaskGovernanceQuery {

    /** Definitions whose latest scheduled terminal runs, at least {@code minimum}, are all FAILED. */
    List<TaskFailureCandidate> consecutiveFailureCandidates(String workspaceId, LocalDateTime since, int minimum);

    /** Definitions whose finished scheduled runs average above {@code thresholdSeconds}. */
    List<TaskSlowCandidate> slowRunCandidates(
            String workspaceId, LocalDateTime since, int minimumRuns, long thresholdSeconds);
}
