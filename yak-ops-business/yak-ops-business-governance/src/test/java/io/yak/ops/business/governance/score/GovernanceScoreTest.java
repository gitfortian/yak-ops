package io.yak.ops.business.governance.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Pins the calibration points the accepted proposal names in its test plan. */
class GovernanceScoreTest {

    private static void assertScore(String expected, int weightedIssues, int baseCount) {
        assertEquals(0, GovernanceScore.compute(weightedIssues, baseCount).compareTo(new BigDecimal(expected)));
    }

    @Test
    void fiveHighFindingsInATinyWorkspaceScoreSixty() {
        assertScore("60.00", 50, 10);
    }

    @Test
    void theSameFindingsInAHundredTablesScoreSeventyFourPointSeven() {
        assertScore("74.70", 50, 100);
    }

    @Test
    void aHundredHighFindingsInTenTablesScoreFortyPointSeven() {
        assertScore("40.74", 1_000, 10);
    }

    @Test
    void noFindingsScoreOneHundred() {
        assertScore("100.00", 0, 128);
    }

    @Test
    void theFloorPreventsATinyBaseFromBeingScoredAsEmpty() {
        assertScore("60.00", 50, 1);
    }

    @Test
    void aDimensionWithoutBaseIsNotScored() {
        assertFalse(GovernanceScore.isEvaluated(0));
        assertTrue(GovernanceScore.isEvaluated(1));
        assertThrows(IllegalArgumentException.class, () -> GovernanceScore.compute(10, 0));
    }

    @Test
    void overallIsTheEqualWeightAverage() {
        assertEquals(
                0,
                GovernanceScore.overall(new BigDecimal("60.00"), new BigDecimal("74.70"))
                        .compareTo(new BigDecimal("67.35")));
    }
}
