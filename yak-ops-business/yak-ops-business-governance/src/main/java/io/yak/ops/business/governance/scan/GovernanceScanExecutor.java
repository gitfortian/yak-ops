package io.yak.ops.business.governance.scan;

import io.yak.ops.business.datamap.governance.DataMapGovernanceQuery;
import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.business.governance.rule.GovernanceRuleContext;
import io.yak.ops.business.governance.rule.GovernanceRuleRegistry;
import io.yak.ops.business.governance.rule.IssueCandidate;
import io.yak.ops.business.governance.score.GovernanceDimensionFacts;
import io.yak.ops.business.governance.score.GovernanceScoreCalculator;
import io.yak.ops.business.task.governance.TaskGovernanceQuery;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.governance.GovernanceScanStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;

/**
 * Runs one governance scan on a worker thread.
 *
 * <p>The run is a persisted row, not a thread: the worker claims the {@code PENDING} row with a
 * conditional update, so two workers racing on one run produce exactly one execution. Every write
 * goes through {@link GovernanceScanWriter}, which commits per rule batch.
 */
@Service
public class GovernanceScanExecutor {

    /** Wall-clock budget of one run; a rule that crosses it fails and resolves nothing. */
    private static final Duration BUDGET = Duration.ofMinutes(10);

    /** Reason code of a rule that ran out of budget or timed out on a statement. */
    private static final String RULE_TIMEOUT = "RULE_TIMEOUT";

    /** Reason code of a rule that threw; the exception itself never leaves the server log. */
    private static final String RULE_FAILED = "RULE_FAILED";

    /** Reason code of a run the data map gate skipped. */
    private static final String DATA_MAP_BUSY = "DATA_MAP_BUSY";

    /** Reason code of a run that could not be handed to a worker. */
    private static final String SCAN_QUEUE_FULL = "SCAN_QUEUE_FULL";

    /** Reason code of a run whose orchestration threw outside a rule. */
    private static final String SCAN_FAILED = "SCAN_FAILED";

    private static final Logger log = LoggerFactory.getLogger(GovernanceScanExecutor.class);

    private final GovernanceRuleRegistry registry;

    private final GovernanceScanWriter writer;

    private final GovernanceScoreCalculator calculator;

    private final DataMapGovernanceQuery dataMap;

    private final TaskGovernanceQuery tasks;

    public GovernanceScanExecutor(
            GovernanceRuleRegistry registry,
            GovernanceScanWriter writer,
            GovernanceScoreCalculator calculator,
            DataMapGovernanceQuery dataMap,
            TaskGovernanceQuery tasks) {
        this.registry = registry;
        this.writer = writer;
        this.calculator = calculator;
        this.dataMap = dataMap;
        this.tasks = tasks;
    }

    /** Executes one run; a run another worker already claimed is left untouched. */
    public void execute(String workspaceId, String scanId) {
        LocalDateTime claimedAt = LocalDateTime.now();
        if (!writer.claim(workspaceId, scanId, claimedAt)) {
            return;
        }
        try {
            WorkspaceContext.bind(workspaceId);
            run(workspaceId, scanId, claimedAt);
        } catch (RuntimeException failure) {
            log.error("governance scan {} of workspace {} aborted", scanId, workspaceId, failure);
            writer.finish(
                    workspaceId, scanId, GovernanceScanStatus.FAILED, 0, 0, 0, 0, 0, SCAN_FAILED, LocalDateTime.now());
        } finally {
            WorkspaceContext.clear();
        }
    }

    /**
     * Releases a run that could not be handed to a worker.
     *
     * <p>Without this the {@code PENDING} row would hold the active key and block every later
     * submission, so a rejected hand-off is a terminal failure rather than a silent stall.
     */
    public void abandon(String workspaceId, String scanId) {
        writer.finish(
                workspaceId, scanId, GovernanceScanStatus.FAILED, 0, 0, 0, 0, 0, SCAN_QUEUE_FULL, LocalDateTime.now());
    }

    private void run(String workspaceId, String scanId, LocalDateTime claimedAt) {
        if (dataMap.hasActiveCrawl(workspaceId)) {
            writer.finish(
                    workspaceId,
                    scanId,
                    GovernanceScanStatus.SKIPPED,
                    0,
                    0,
                    0,
                    0,
                    0,
                    DATA_MAP_BUSY,
                    LocalDateTime.now());
            return;
        }

        writer.openExpiredIgnores(workspaceId, claimedAt);

        List<GovernanceRule> rules = registry.rules();
        LocalDateTime deadline = claimedAt.plus(BUDGET);
        int succeeded = 0;
        int failed = 0;
        int created = 0;
        int reopened = 0;
        int resolved = 0;

        for (GovernanceRule rule : rules) {
            LocalDateTime startedAt = LocalDateTime.now();
            String resultId = writer.startRule(workspaceId, scanId, rule, startedAt);
            RuleRun run = new RuleRun(workspaceId, scanId, rule, deadline);
            try {
                if (startedAt.isAfter(deadline)) {
                    throw new ScanBudgetExceededException();
                }
                GovernanceRuleContext context =
                        new GovernanceRuleContext(workspaceId, scanId, startedAt, dataMap, tasks);
                rule.evaluate(context, run::accept);
                run.flush();
                int autoResolved = writer.autoResolveUnobserved(workspaceId, scanId, rule.code(), LocalDateTime.now());
                writer.completeRule(
                        workspaceId,
                        resultId,
                        "SUCCEEDED",
                        run.findings,
                        run.created,
                        run.reopened,
                        autoResolved,
                        null,
                        LocalDateTime.now());
                succeeded++;
                resolved += autoResolved;
            } catch (RuntimeException failure) {
                log.warn(
                        "governance rule {} failed in scan {} of workspace {}",
                        rule.code(),
                        scanId,
                        workspaceId,
                        failure);
                writer.completeRule(
                        workspaceId,
                        resultId,
                        "FAILED",
                        run.findings,
                        run.created,
                        run.reopened,
                        0,
                        reasonFor(failure),
                        LocalDateTime.now());
                failed++;
            }
            created += run.created;
            reopened += run.reopened;
        }

        GovernanceScanStatus status = terminalStatus(succeeded, failed, rules.size());
        writer.finish(
                workspaceId, scanId, status, succeeded, failed, created, reopened, resolved, null, LocalDateTime.now());

        if (status == GovernanceScanStatus.SUCCEEDED) {
            writeSnapshot(workspaceId, scanId);
        }
    }

    /**
     * Writes the day's snapshot from the facts the run just converged.
     *
     * <p>Only a complete run reaches this method: a {@code PARTIAL} run keeps the counts it observed
     * and leaves the previous score in place rather than publishing a number computed from missing
     * rules.
     */
    private void writeSnapshot(String workspaceId, String scanId) {
        LocalDateTime now = LocalDateTime.now();
        GovernanceDimensionFacts dev = calculator.dev(workspaceId);
        GovernanceDimensionFacts compute = calculator.compute(workspaceId, now);
        GovernanceDimensionFacts overall = GovernanceDimensionFacts.overall(dev, compute);
        writer.writeSnapshot(workspaceId, scanId, LocalDate.now(ZoneOffset.UTC), List.of(dev, compute), overall, now);
    }

    private static GovernanceScanStatus terminalStatus(int succeeded, int failed, int total) {
        if (failed == 0 && succeeded == total) {
            return GovernanceScanStatus.SUCCEEDED;
        }
        if (succeeded == 0) {
            return GovernanceScanStatus.FAILED;
        }
        return GovernanceScanStatus.PARTIAL;
    }

    private static String reasonFor(RuntimeException failure) {
        if (failure instanceof ScanBudgetExceededException) {
            return RULE_TIMEOUT;
        }
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof QueryTimeoutException || cause instanceof TransactionTimedOutException) {
                return RULE_TIMEOUT;
            }
            cause = cause.getCause();
        }
        return RULE_FAILED;
    }

    /** Signals that the run crossed its wall-clock budget while a rule was still enumerating. */
    static final class ScanBudgetExceededException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ScanBudgetExceededException() {
            super("The governance scan exceeded its wall-clock budget", null, false, false);
        }
    }

    /** Buffers candidates of one rule and writes them in batches of two hundred. */
    private final class RuleRun {

        private final String workspaceId;

        private final String scanId;

        private final GovernanceRule rule;

        private final LocalDateTime deadline;

        private final List<IssueCandidate> buffer = new ArrayList<>(GovernanceScanWriter.BATCH_SIZE);

        private int findings;

        private int created;

        private int reopened;

        private RuleRun(String workspaceId, String scanId, GovernanceRule rule, LocalDateTime deadline) {
            this.workspaceId = workspaceId;
            this.scanId = scanId;
            this.rule = rule;
            this.deadline = deadline;
        }

        private void accept(IssueCandidate candidate) {
            if (LocalDateTime.now().isAfter(deadline)) {
                throw new ScanBudgetExceededException();
            }
            findings++;
            buffer.add(candidate);
            if (buffer.size() >= GovernanceScanWriter.BATCH_SIZE) {
                flush();
            }
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            GovernanceScanWriter.BatchCounts counts =
                    writer.applyBatch(workspaceId, scanId, rule, List.copyOf(buffer), LocalDateTime.now());
            created += counts.created();
            reopened += counts.reopened();
            buffer.clear();
        }
    }
}
