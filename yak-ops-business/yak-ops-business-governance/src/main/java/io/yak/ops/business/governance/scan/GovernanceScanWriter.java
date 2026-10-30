package io.yak.ops.business.governance.scan;

import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.business.governance.rule.IssueCandidate;
import io.yak.ops.business.governance.score.GovernanceDimensionFacts;
import io.yak.ops.business.governance.score.GovernanceScore;
import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.GovernanceEventType;
import io.yak.ops.common.enums.governance.GovernanceScanStatus;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.common.enums.governance.IssueStatus;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.governance.GovernanceHealthSnapshotEntity;
import io.yak.ops.dao.entity.governance.GovernanceIssueEntity;
import io.yak.ops.dao.entity.governance.GovernanceIssueEventEntity;
import io.yak.ops.dao.entity.governance.GovernanceRuleResultEntity;
import io.yak.ops.dao.entity.governance.GovernanceScanRunEntity;
import io.yak.ops.dao.mapper.governance.GovernanceHealthSnapshotMapper;
import io.yak.ops.dao.mapper.governance.GovernanceIssueEventMapper;
import io.yak.ops.dao.mapper.governance.GovernanceIssueMapper;
import io.yak.ops.dao.mapper.governance.GovernanceRuleResultMapper;
import io.yak.ops.dao.mapper.governance.GovernanceScanRunMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional steps of one scan run.
 *
 * <p>The executor orchestrates and holds no transaction of its own, so a rule's writes commit as
 * they complete instead of waiting for the whole scan. The 30-second statement budget lives here
 * because these are the only statements a rule evaluation reaches.
 */
@Service
public class GovernanceScanWriter {

    /** Actor recorded on a transition the product made on its own. */
    public static final String SYSTEM_ACTOR = "system";

    /** Upper bound of one rule batch; a larger candidate set is written as several batches. */
    public static final int BATCH_SIZE = 200;

    private final GovernanceScanRunMapper scanRunMapper;

    private final GovernanceRuleResultMapper ruleResultMapper;

    private final GovernanceIssueMapper issueMapper;

    private final GovernanceIssueEventMapper eventMapper;

    private final GovernanceHealthSnapshotMapper snapshotMapper;

    public GovernanceScanWriter(
            GovernanceScanRunMapper scanRunMapper,
            GovernanceRuleResultMapper ruleResultMapper,
            GovernanceIssueMapper issueMapper,
            GovernanceIssueEventMapper eventMapper,
            GovernanceHealthSnapshotMapper snapshotMapper) {
        this.scanRunMapper = scanRunMapper;
        this.ruleResultMapper = ruleResultMapper;
        this.issueMapper = issueMapper;
        this.eventMapper = eventMapper;
        this.snapshotMapper = snapshotMapper;
    }

    /** Counts one batch moved into the Issue table. */
    public record BatchCounts(int created, int reopened) {

        public static final BatchCounts EMPTY = new BatchCounts(0, 0);

        public BatchCounts plus(BatchCounts other) {
            return new BatchCounts(created + other.created, reopened + other.reopened);
        }
    }

    /** Inserts a pending run; the caller dispatches only after this transaction commits. */
    @Transactional
    public void createRun(GovernanceScanRunEntity row) {
        scanRunMapper.insert(row);
    }

    /** Claims a pending run; false means another worker already claimed it. */
    @Transactional
    public boolean claim(String workspaceId, String scanId, LocalDateTime now) {
        return scanRunMapper.claim(workspaceId, scanId, now) == 1;
    }

    /** Moves a claimed run to its terminal state and releases the active key. */
    @Transactional
    public void finish(
            String workspaceId,
            String scanId,
            GovernanceScanStatus status,
            int succeededRules,
            int failedRules,
            int created,
            int reopened,
            int resolved,
            String reasonCode,
            LocalDateTime now) {
        scanRunMapper.finish(
                workspaceId,
                scanId,
                status.getValue(),
                succeededRules,
                failedRules,
                created,
                reopened,
                resolved,
                reasonCode,
                now);
    }

    /**
     * Opens every expired ignore of the Workspace before any rule runs.
     *
     * <p>If the target is still present the rule refreshes the same row, so an expired ignore and a
     * disappeared finding legitimately produce two events in one scan.
     */
    @Transactional
    public void openExpiredIgnores(String workspaceId, LocalDateTime now) {
        for (GovernanceIssueEntity issue : issueMapper.listExpiredIgnored(workspaceId, now)) {
            if (issueMapper.reopenExpired(workspaceId, issue.getId(), now, SYSTEM_ACTOR) == 1) {
                appendEvent(
                        workspaceId,
                        issue.getId(),
                        GovernanceEventType.REOPENED,
                        IssueStatus.IGNORED.getValue(),
                        IssueStatus.OPEN.getValue(),
                        issue.getOwnerId(),
                        null,
                        "Ignore window expired",
                        SYSTEM_ACTOR,
                        now);
            }
        }
    }

    /** Inserts the RUNNING rule result; the unique key keeps one result per rule per scan. */
    @Transactional
    public String startRule(String workspaceId, String scanId, GovernanceRule rule, LocalDateTime now) {
        GovernanceRuleResultEntity row = new GovernanceRuleResultEntity();
        row.setId(IdUtils.nextId());
        row.setWorkspaceId(workspaceId);
        row.setScanId(scanId);
        row.setRuleCode(rule.code());
        row.setRuleVersion(rule.version());
        row.setStatus("RUNNING");
        row.setFindingCount(0);
        row.setIssueCreatedCount(0);
        row.setIssueReopenedCount(0);
        row.setIssueResolvedCount(0);
        row.setStartTime(now);
        ruleResultMapper.insert(row);
        return row.getId();
    }

    /** Reconciles one batch of candidates with the stored Issues, in a transaction of its own. */
    @Transactional(timeout = 30)
    public BatchCounts applyBatch(
            String workspaceId,
            String scanId,
            GovernanceRule rule,
            List<IssueCandidate> candidates,
            LocalDateTime now) {
        int created = 0;
        int reopened = 0;
        for (IssueCandidate candidate : candidates) {
            switch (reconcile(workspaceId, scanId, rule, candidate, now)) {
                case CREATED -> created++;
                case REOPENED -> reopened++;
                default -> {
                    // Evidence was refreshed on an Issue that keeps its lifecycle state.
                }
            }
        }
        return new BatchCounts(created, reopened);
    }

    /**
     * Resolves every Issue of the rule the run did not observe.
     *
     * <p>Only a rule that enumerated its whole target set reaches this call, so a failed rule closes
     * nothing by construction.
     */
    @Transactional(timeout = 30)
    public int autoResolveUnobserved(String workspaceId, String scanId, String ruleCode, LocalDateTime now) {
        int resolved = 0;
        for (GovernanceIssueEntity issue : issueMapper.listUnobserved(workspaceId, ruleCode, scanId)) {
            if (issueMapper.autoResolve(workspaceId, issue.getId(), SYSTEM_ACTOR, now) == 1) {
                appendEvent(
                        workspaceId,
                        issue.getId(),
                        GovernanceEventType.AUTO_RESOLVED,
                        issue.getStatus(),
                        IssueStatus.RESOLVED.getValue(),
                        issue.getOwnerId(),
                        null,
                        null,
                        SYSTEM_ACTOR,
                        now);
                resolved++;
            }
        }
        return resolved;
    }

    /** Records the terminal outcome of one rule. */
    @Transactional
    public void completeRule(
            String workspaceId,
            String resultId,
            String status,
            int findings,
            int created,
            int reopened,
            int resolved,
            String reasonCode,
            LocalDateTime now) {
        ruleResultMapper.complete(
                workspaceId, resultId, status, findings, created, reopened, resolved, reasonCode, now);
    }

    /**
     * Replaces the snapshot of one day with the facts of a complete run.
     *
     * <p>A dimension without an eligible base writes no row, and {@code OVERALL} exists only when
     * both dimensions are evaluated. The day is cleared first so a second run on the same day
     * replaces the set instead of stacking a second one.
     */
    @Transactional
    public void writeSnapshot(
            String workspaceId,
            String scanId,
            LocalDate day,
            List<GovernanceDimensionFacts> facts,
            GovernanceDimensionFacts overall,
            LocalDateTime now) {
        List<GovernanceDimensionFacts> evaluated =
                facts.stream().filter(GovernanceDimensionFacts::evaluated).toList();
        if (evaluated.isEmpty()) {
            return;
        }
        snapshotMapper.deleteByDay(workspaceId, day);
        int dimensionCount = evaluated.size();
        for (GovernanceDimensionFacts fact : evaluated) {
            snapshotMapper.insert(dimensionRow(workspaceId, scanId, day, fact, dimensionCount, now));
        }
        if (overall != null) {
            snapshotMapper.insert(overallRow(workspaceId, scanId, day, evaluated, overall, now));
        }
    }

    private GovernanceHealthSnapshotEntity dimensionRow(
            String workspaceId,
            String scanId,
            LocalDate day,
            GovernanceDimensionFacts fact,
            int dimensionCount,
            LocalDateTime now) {
        GovernanceHealthSnapshotEntity row = baseRow(workspaceId, scanId, day, now);
        row.setDimensionCode(fact.dimension().getValue());
        row.setScore(fact.score());
        row.setBaseCount(fact.baseCount());
        row.setWeightedIssueCount(BigDecimal.valueOf(fact.weightedIssues()));
        row.setIssueCount(fact.issueCount());
        row.setHighCount(fact.count(IssueSeverity.HIGH));
        row.setMediumCount(fact.count(IssueSeverity.MEDIUM));
        row.setLowCount(fact.count(IssueSeverity.LOW));
        row.setEvaluatedDimensionCount(dimensionCount);
        return row;
    }

    private GovernanceHealthSnapshotEntity overallRow(
            String workspaceId,
            String scanId,
            LocalDate day,
            List<GovernanceDimensionFacts> evaluated,
            GovernanceDimensionFacts overall,
            LocalDateTime now) {
        GovernanceHealthSnapshotEntity row = baseRow(workspaceId, scanId, day, now);
        row.setDimensionCode(GovernanceDimension.OVERALL.getValue());
        row.setScore(overall.score());
        row.setBaseCount(
                evaluated.stream().mapToInt(GovernanceDimensionFacts::baseCount).sum());
        row.setWeightedIssueCount(BigDecimal.valueOf(overall.weightedIssues()));
        row.setIssueCount(overall.issueCount());
        row.setHighCount(overall.count(IssueSeverity.HIGH));
        row.setMediumCount(overall.count(IssueSeverity.MEDIUM));
        row.setLowCount(overall.count(IssueSeverity.LOW));
        row.setEvaluatedDimensionCount(evaluated.size());
        return row;
    }

    private GovernanceHealthSnapshotEntity baseRow(
            String workspaceId, String scanId, LocalDate day, LocalDateTime now) {
        GovernanceHealthSnapshotEntity row = new GovernanceHealthSnapshotEntity();
        row.setId(IdUtils.nextId());
        row.setWorkspaceId(workspaceId);
        row.setSnapshotDate(day);
        row.setFormulaVersion(GovernanceScore.FORMULA_VERSION);
        row.setScanId(scanId);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private enum ReconcileOutcome {
        CREATED,
        REOPENED,
        REFRESHED
    }

    private ReconcileOutcome reconcile(
            String workspaceId, String scanId, GovernanceRule rule, IssueCandidate candidate, LocalDateTime now) {
        String targetType = rule.targetType().getValue();
        GovernanceIssueEntity existing =
                issueMapper.lockByTarget(workspaceId, rule.code(), targetType, candidate.targetId());
        if (existing == null) {
            insertIssue(workspaceId, scanId, rule, candidate, now);
            return ReconcileOutcome.CREATED;
        }
        boolean expiredIgnore = IssueStatus.IGNORED.getValue().equals(existing.getStatus())
                && existing.getIgnoreUntil() != null
                && !existing.getIgnoreUntil().isAfter(now);
        if (IssueStatus.RESOLVED.getValue().equals(existing.getStatus()) || expiredIgnore) {
            issueMapper.reopenAndDetect(
                    workspaceId,
                    existing.getId(),
                    candidate.targetName(),
                    rule.version(),
                    candidate.observedValue(),
                    candidate.thresholdValue(),
                    candidate.sampleCount(),
                    candidate.windowDays(),
                    now,
                    scanId,
                    now,
                    SYSTEM_ACTOR);
            appendEvent(
                    workspaceId,
                    existing.getId(),
                    GovernanceEventType.REOPENED,
                    existing.getStatus(),
                    IssueStatus.OPEN.getValue(),
                    existing.getOwnerId(),
                    null,
                    null,
                    SYSTEM_ACTOR,
                    now);
            return ReconcileOutcome.REOPENED;
        }
        issueMapper.touch(
                workspaceId,
                existing.getId(),
                candidate.targetName(),
                rule.version(),
                candidate.observedValue(),
                candidate.thresholdValue(),
                candidate.sampleCount(),
                candidate.windowDays(),
                now,
                scanId,
                now,
                SYSTEM_ACTOR);
        return ReconcileOutcome.REFRESHED;
    }

    private void insertIssue(
            String workspaceId, String scanId, GovernanceRule rule, IssueCandidate candidate, LocalDateTime now) {
        GovernanceIssueEntity row = new GovernanceIssueEntity();
        row.setId(IdUtils.nextId());
        row.setWorkspaceId(workspaceId);
        row.setRuleCode(rule.code());
        row.setRuleVersion(rule.version());
        row.setDimensionCode(rule.dimension().getValue());
        row.setSeverity(rule.severity().getValue());
        row.setTargetType(rule.targetType().getValue());
        row.setTargetId(candidate.targetId());
        row.setTargetName(candidate.targetName());
        row.setStatus(IssueStatus.OPEN.getValue());
        row.setObservedValue(candidate.observedValue());
        row.setThresholdValue(candidate.thresholdValue());
        row.setSampleCount(candidate.sampleCount());
        row.setWindowDays(candidate.windowDays());
        row.setFirstDetectedAt(now);
        row.setLastDetectedAt(now);
        row.setLastScanId(scanId);
        row.setRevision(1L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setCreateBy(SYSTEM_ACTOR);
        row.setUpdateBy(SYSTEM_ACTOR);
        issueMapper.insert(row);
        appendEvent(
                workspaceId,
                row.getId(),
                GovernanceEventType.CREATED,
                null,
                IssueStatus.OPEN.getValue(),
                null,
                null,
                null,
                SYSTEM_ACTOR,
                now);
    }

    private void appendEvent(
            String workspaceId,
            String issueId,
            GovernanceEventType type,
            String fromStatus,
            String toStatus,
            String ownerId,
            LocalDateTime ignoreUntil,
            String note,
            String actorId,
            LocalDateTime now) {
        GovernanceIssueEventEntity event = new GovernanceIssueEventEntity();
        event.setId(IdUtils.nextId());
        event.setWorkspaceId(workspaceId);
        event.setIssueId(issueId);
        event.setEventType(type.getValue());
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOwnerId(ownerId);
        event.setIgnoreUntil(ignoreUntil);
        event.setNote(note);
        event.setActorId(actorId);
        event.setCreateTime(now);
        eventMapper.insert(event);
    }
}
