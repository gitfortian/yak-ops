package io.yak.ops.business.governance.rule.task;

import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.business.governance.rule.GovernanceRuleContext;
import io.yak.ops.business.governance.rule.IssueCandidate;
import io.yak.ops.business.task.governance.TaskSlowCandidate;
import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.common.enums.governance.IssueTargetType;
import java.math.BigDecimal;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Accuses published Task definitions whose finished scheduled runs average above two hours.
 *
 * <p>A definition with fewer than three finished scheduled runs in the window is not accused, and a
 * single manual backfill cannot make a definition slow because non-scheduled runs are excluded.
 */
@Component
public class TaskSlowRunsRule implements GovernanceRule {

    public static final String CODE = "TASK_SLOW_RUNS";

    private static final int WINDOW_DAYS = 7;
    private static final int MINIMUM_RUNS = 3;
    private static final long THRESHOLD_SECONDS = 7_200L;
    private static final BigDecimal THRESHOLD = BigDecimal.valueOf(THRESHOLD_SECONDS);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public GovernanceDimension dimension() {
        return GovernanceDimension.COMPUTE;
    }

    @Override
    public IssueSeverity severity() {
        return IssueSeverity.MEDIUM;
    }

    @Override
    public IssueTargetType targetType() {
        return IssueTargetType.TASK_DEFINITION;
    }

    @Override
    public void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink) {
        for (TaskSlowCandidate candidate : context.tasks()
                .slowRunCandidates(
                        context.workspaceId(),
                        context.evaluatedAt().minusDays(WINDOW_DAYS),
                        MINIMUM_RUNS,
                        THRESHOLD_SECONDS)) {
            sink.accept(new IssueCandidate(
                    candidate.definitionId(),
                    candidate.displayName(),
                    BigDecimal.valueOf(candidate.averageSeconds()),
                    THRESHOLD,
                    candidate.sampleCount(),
                    WINDOW_DAYS));
        }
    }
}
