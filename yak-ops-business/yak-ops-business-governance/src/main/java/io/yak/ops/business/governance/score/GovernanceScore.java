package io.yak.ops.business.governance.score;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The documented score curve of one dimension.
 *
 * <p>A raw Issue count punishes a large Workspace for owning more assets and lets a small Workspace
 * game the denominator, so the score is a weighted rate over an eligible base with a floor of ten.
 * The constant is calibrated so five HIGH findings in a Workspace with between one and ten eligible
 * assets score 60.00.
 */
public final class GovernanceScore {

    /** Version persisted with every snapshot so a score stays explainable after the formula moves. */
    public static final String FORMULA_VERSION = "v1";

    /** Floor of the effective base; a Workspace below it is not scored as if it were empty. */
    private static final int BASE_FLOOR = 10;

    /** {@code 40 / ln(501)}, the curve constant. */
    private static final double C = 40d / Math.log(501d);

    private GovernanceScore() {}

    /**
     * Computes the score of one dimension.
     *
     * @param weightedIssues sum of the weights of the current OPEN and HANDLING Issues
     * @param baseCount eligible assets of the dimension; a dimension with none is not scored
     */
    public static BigDecimal compute(int weightedIssues, int baseCount) {
        if (baseCount <= 0) {
            throw new IllegalArgumentException("A dimension with no eligible base is N/A, not a score");
        }
        double rate = (double) weightedIssues * 100d / Math.max(baseCount, BASE_FLOOR);
        double raw = 100d - C * Math.log1p(rate);
        double clamped = Math.max(0d, Math.min(100d, raw));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    /** Whether a dimension with this base participates in the score at all. */
    public static boolean isEvaluated(int baseCount) {
        return baseCount > 0;
    }

    /** Equal-weight average of the dimensions evaluated on the same day. */
    public static BigDecimal overall(BigDecimal first, BigDecimal second) {
        return first.add(second).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}
