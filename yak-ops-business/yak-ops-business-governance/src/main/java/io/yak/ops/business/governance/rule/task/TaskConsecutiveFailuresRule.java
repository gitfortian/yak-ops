package io.yak.ops.business.governance.rule.task;

import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.business.governance.rule.GovernanceRuleContext;
import io.yak.ops.business.governance.rule.IssueCandidate;
import io.yak.ops.business.task.governance.TaskFailureCandidate;
import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.common.enums.governance.IssueTargetType;
import java.math.BigDecimal;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Accuses published Task definitions whose latest scheduled runs all failed.
 *
 * <p>Only {@code SCHEDULE} instances of the current definition version inside the window count. A
 * {@code CANCELED} instance breaks the streak; it is neither a failure nor silently skipped.
 */
@Component
public class TaskConsecutiveFailuresRule implements GovernanceRule {

    public static final String CODE = "TASK_CONSECUTIVE_FAILURES";

    private static final int WINDOW_DAYS = 7;
    private static final int MINIMUM_CONSECUTIVE = 3;
    private static final BigDecimal THRESHOLD = BigDecimal.valueOf(MINIMUM_CONSECUTIVE);

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
        return IssueSeverity.HIGH;
    }

    @Override
    public IssueTargetType targetType() {
        return IssueTargetType.TASK_DEFINITION;
    }

    @Override
    public void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink) {
        for (TaskFailureCandidate candidate : context.tasks()
                .consecutiveFailureCandidates(
                        context.workspaceId(), context.evaluatedAt().minusDays(WINDOW_DAYS), MINIMUM_CONSECUTIVE)) {
            sink.accept(new IssueCandidate(
                    candidate.definitionId(),
                    candidate.displayName(),
                    BigDecimal.valueOf(candidate.consecutiveFailures()),
                    THRESHOLD,
                    candidate.sampleCount(),
                    WINDOW_DAYS));
        }
    }
}
