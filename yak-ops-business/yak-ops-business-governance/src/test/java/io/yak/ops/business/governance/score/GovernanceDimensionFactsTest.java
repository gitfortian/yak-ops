package io.yak.ops.business.governance.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.IssueSeverity;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pins the coverage rules of OVERALL and the N/A behaviour of an empty dimension. */
class GovernanceDimensionFactsTest {

    private static GovernanceDimensionFacts facts(GovernanceDimension dimension, int base, int issues) {
        Map<IssueSeverity, Integer> counts = new EnumMap<>(IssueSeverity.class);
        counts.put(IssueSeverity.HIGH, issues);
        return GovernanceDimensionFacts.of(dimension, base, issues * IssueSeverity.HIGH.getWeight(), issues, counts);
    }

    @Test
    void anEmptyBaseIsNotEvaluatedAndCarriesNoScore() {
        GovernanceDimensionFacts empty = facts(GovernanceDimension.COMPUTE, 0, 0);

        assertFalse(empty.evaluated());
        assertNull(empty.score());
    }

    @Test
    void overallIsAbsentWhenOneDimensionIsNotEvaluated() {
        assertNull(GovernanceDimensionFacts.overall(
                facts(GovernanceDimension.DEV, 10, 0), facts(GovernanceDimension.COMPUTE, 0, 0)));
    }

    @Test
    void overallAveragesTheTwoEvaluatedDimensions() {
        GovernanceDimensionFacts dev = facts(GovernanceDimension.DEV, 10, 5);
        GovernanceDimensionFacts compute = facts(GovernanceDimension.COMPUTE, 100, 0);

        GovernanceDimensionFacts overall = GovernanceDimensionFacts.overall(dev, compute);

        assertEquals(0, overall.score().compareTo(new BigDecimal("80.00")));
        assertEquals(110, overall.baseCount());
        assertEquals(GovernanceDimension.OVERALL, overall.dimension());
    }

    @Test
    void theScoreMatchesTheCurveCalibration() {
        assertEquals(0, facts(GovernanceDimension.DEV, 10, 5).score().compareTo(new BigDecimal("60.00")));
        assertEquals(0, facts(GovernanceDimension.DEV, 100, 5).score().compareTo(new BigDecimal("74.70")));
        assertTrue(facts(GovernanceDimension.DEV, 100, 0).score().compareTo(new BigDecimal("100.00")) == 0);
    }
}
