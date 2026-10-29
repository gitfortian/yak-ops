package io.yak.ops.business.task.governance.impl;

import io.yak.ops.business.task.governance.TaskFailureCandidate;
import io.yak.ops.business.task.governance.TaskGovernanceQuery;
import io.yak.ops.business.task.governance.TaskSlowCandidate;
import io.yak.ops.dao.mapper.task.TaskGovernanceMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** Reads the Task projections governance needs without exposing the execution write model. */
@Service
public class TaskGovernanceQueryImpl implements TaskGovernanceQuery {

    private final TaskGovernanceMapper mapper;

    public TaskGovernanceQueryImpl(TaskGovernanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TaskFailureCandidate> consecutiveFailureCandidates(
            String workspaceId, LocalDateTime since, int minimum) {
        return mapper.listConsecutiveFailureCandidates(workspaceId, since, minimum).stream()
                .map(row -> new TaskFailureCandidate(
                        row.getDefinitionId(),
                        row.getDisplayName(),
                        row.getConsecutiveFailures() == null ? 0 : row.getConsecutiveFailures(),
                        row.getSampleCount() == null ? 0 : row.getSampleCount()))
                .toList();
    }

    @Override
    public List<TaskSlowCandidate> slowRunCandidates(
            String workspaceId, LocalDateTime since, int minimumRuns, long thresholdSeconds) {
        return mapper.listSlowRunCandidates(workspaceId, since, minimumRuns, thresholdSeconds).stream()
                .map(row -> new TaskSlowCandidate(
                        row.getDefinitionId(),
                        row.getDisplayName(),
                        row.getAverageSeconds() == null ? 0L : row.getAverageSeconds(),
                        row.getSampleCount() == null ? 0 : row.getSampleCount()))
                .toList();
    }
}
