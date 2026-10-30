package io.yak.ops.business.governance.score;

import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.IssueSeverity;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Current inputs and the resulting score of one dimension.
 *
 * <p>{@code weightedIssues} sums the weights of the OPEN and HANDLING Issues only; the severity
 * counts describe the same set, so a dimension card always adds up. The score is fixed at
 * construction: a dimension row uses the curve, while {@code OVERALL} uses the equal-weight average
 * and cannot be recomputed from its own base.
 *
 * @param dimension scored dimension
 * @param baseCount eligible assets of the dimension
 * @param weightedIssues sum of the weights of the current score-bearing Issues
 * @param issueCount number of score-bearing Issues
 * @param severityCounts counts keyed by severity
 * @param score score of this dimension; null when it is N/A
 */
public record GovernanceDimensionFacts(
        GovernanceDimension dimension,
        int baseCount,
        int weightedIssues,
        int issueCount,
        Map<IssueSeverity, Integer> severityCounts,
        BigDecimal score) {

    /** Builds one dimension row and applies the curve when the base is eligible. */
    public static GovernanceDimensionFacts of(
            GovernanceDimension dimension,
            int baseCount,
            int weightedIssues,
            int issueCount,
            Map<IssueSeverity, Integer> severityCounts) {
        BigDecimal score =
                GovernanceScore.isEvaluated(baseCount) ? GovernanceScore.compute(weightedIssues, baseCount) : null;
        return new GovernanceDimensionFacts(dimension, baseCount, weightedIssues, issueCount, severityCounts, score);
    }

    /**
     * Equal-weight average of the two dimensions, or null when either is N/A.
     *
     * <p>The base of the returned row is the sum of the evaluated bases: the assets the overall
     * number covers, which is the only base that can honestly be reported for it.
     */
    public static GovernanceDimensionFacts overall(GovernanceDimensionFacts dev, GovernanceDimensionFacts compute) {
        if (!dev.evaluated() || !compute.evaluated()) {
            return null;
        }
        Map<IssueSeverity, Integer> counts = new EnumMap<>(IssueSeverity.class);
        for (IssueSeverity severity : IssueSeverity.values()) {
            counts.put(severity, dev.count(severity) + compute.count(severity));
        }
        return new GovernanceDimensionFacts(
                GovernanceDimension.OVERALL,
                dev.baseCount() + compute.baseCount(),
                dev.weightedIssues() + compute.weightedIssues(),
                dev.issueCount() + compute.issueCount(),
                counts,
                GovernanceScore.overall(dev.score(), compute.score()));
    }

    /** Whether the dimension has an eligible base and therefore a score. */
    public boolean evaluated() {
        return score != null;
    }

    public int count(IssueSeverity severity) {
        return severityCounts.getOrDefault(severity, 0);
    }
}
