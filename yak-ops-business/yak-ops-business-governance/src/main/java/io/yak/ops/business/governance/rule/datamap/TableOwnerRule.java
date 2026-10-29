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
 * Accuses every ACTIVE catalog table without an accountable member.
 *
 * <p>{@code MISSING} tables are excluded by the port: a dropped table cannot be repaired by a member,
 * so accusing it would be a permanent false positive.
 */
@Component
public class TableOwnerRule implements GovernanceRule {

    public static final String CODE = "DATA_MAP_TABLE_NO_OWNER";

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
        return IssueSeverity.HIGH;
    }

    @Override
    public IssueTargetType targetType() {
        return IssueTargetType.DATA_MAP_TABLE;
    }

    @Override
    public void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink) {
        ActiveTableCursor.forEach(context, table -> {
            if (table.ownerId() == null || table.ownerId().isBlank()) {
                sink.accept(new IssueCandidate(table.id(), table.displayName(), BigDecimal.ZERO, BigDecimal.ONE, 1, 0));
            }
        });
    }
}
