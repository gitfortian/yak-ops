package io.yak.ops.business.governance.rule.datamap;

import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.business.governance.rule.GovernanceRuleContext;
import io.yak.ops.business.governance.rule.IssueCandidate;
import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.common.enums.governance.IssueTargetType;
import java.math.BigDecimal;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Accuses ACTIVE catalog tables with no description at all.
 *
 * <p>A nonblank engine comment counts as a description: the rule is "no description anywhere", not
 * "no curated business description". Requiring the curated field even when a comment exists would be
 * a new rule with new semantics and a version bump.
 */
@Component
public class TableDescriptionRule implements GovernanceRule {

    public static final String CODE = "DATA_MAP_TABLE_NO_DESCRIPTION";

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
        return GovernanceDimension.DEV;
    }

    @Override
    public IssueSeverity severity() {
        return IssueSeverity.LOW;
    }

    @Override
    public IssueTargetType targetType() {
        return IssueTargetType.DATA_MAP_TABLE;
    }

    @Override
    public void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink) {
        ActiveTableCursor.forEach(context, table -> {
            if (isBlank(table.description()) && isBlank(table.remarks())) {
                sink.accept(new IssueCandidate(table.id(), table.displayName(), BigDecimal.ZERO, BigDecimal.ONE, 1, 0));
            }
        });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
