package io.yak.ops.business.governance.rule;

import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.common.enums.governance.IssueTargetType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One code-owned governance rule.
 *
 * <p>The layer is an internal contract, not a public plugin SPI: no external code is loaded, no rule
 * is registered by writing to a table and no endpoint changes enablement or thresholds.
 *
 * <p>{@link #evaluate} must be read-only, deterministic for the same metadata and instant, and must
 * either enumerate every eligible target or throw. It must not write an Issue, close an Issue, read
 * credentials or inspect user data values.
 */
public interface GovernanceRule {

    /** Stable code persisted on every Issue the rule produces. */
    String code();

    /** Version of the rule semantics; a change that alters findings bumps it. */
    int version();

    /** Dimension the rule is scored in. */
    GovernanceDimension dimension();

    /** Severity every Issue of this rule carries. */
    IssueSeverity severity();

    /** Kind of object the rule accuses. */
    IssueTargetType targetType();

    /**
     * Fixed numeric thresholds the rule compares against, keyed by a stable name.
     *
     * <p>Returned as numbers rather than a sentence: the caller owns the wording, and an operator
     * reads the same value the scan used.
     */
    default Map<String, BigDecimal> thresholds() {
        return Map.of();
    }

    /** Emits one candidate per accused target; an empty enumeration is a valid, complete result. */
    void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink);
}
