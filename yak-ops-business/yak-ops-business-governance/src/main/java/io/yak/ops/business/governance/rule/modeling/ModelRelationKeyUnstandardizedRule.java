package io.yak.ops.business.governance.rule.modeling;

import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.business.governance.rule.GovernanceRuleContext;
import io.yak.ops.business.governance.rule.IssueCandidate;
import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.common.enums.governance.IssueTargetType;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Accuses models that own a relation whose key field is missing or unstandardized. One candidate
 * per model per scan even when several relations offend, so a single broken relation does not
 * flood the issue list. Excluded from the health score in this release, like its sibling rule.
 */
@Component
public class ModelRelationKeyUnstandardizedRule implements GovernanceRule {

    public static final String CODE = "MODEL_RELATION_KEY_UNSTANDARDIZED";

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
        return IssueTargetType.MODELING_MODEL;
    }

    @Override
    public boolean scored() {
        return false;
    }

    @Override
    public void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink) {
        Set<String> accused = new HashSet<>();
        for (var row : context.modeling().modelsWithUnstandardizedRelationKeys(context.workspaceId())) {
            if (!accused.add(row.modelId())) continue;
            sink.accept(new IssueCandidate(row.modelId(), row.modelName(), BigDecimal.ZERO, BigDecimal.ONE, 1, 0));
        }
    }
}
