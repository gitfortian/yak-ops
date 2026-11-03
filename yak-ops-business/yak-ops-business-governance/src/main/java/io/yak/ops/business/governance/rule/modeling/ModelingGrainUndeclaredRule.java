package io.yak.ops.business.governance.rule.modeling;

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
 * Accuses DWD, DWS and ADS models that declare no grain. The finding is advisory by design: the
 * proposal blocks nothing at save time, because blocking would make every existing model fail on
 * upgrade. Neither this rule nor its sibling is wired into the health score in this release.
 */
@Component
public class ModelingGrainUndeclaredRule implements GovernanceRule {

    public static final String CODE = "MODELING_GRAIN_UNDECLARED";

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
        return IssueSeverity.MEDIUM;
    }

    @Override
    public IssueTargetType targetType() {
        return IssueTargetType.MODELING_MODEL;
    }

    @Override
    public boolean scored() {
        return false;
    }

    @Override
    public void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink) {
        for (var row : context.modeling().modelsMissingGrain(context.workspaceId())) {
            sink.accept(new IssueCandidate(row.modelId(), row.modelName(), BigDecimal.ZERO, BigDecimal.ONE, 1, 0));
        }
    }
}
