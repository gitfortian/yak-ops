package io.yak.ops.business.datamap.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Verifies that LIKE wildcards in a keyword are matched literally. */
class DataMapSearchTextTest {

    @Test
    void noKeywordProducesNoPattern() {
        assertNull(DataMapSearchText.containsPattern(null));
    }

    @Test
    void percentUnderscoreAndEscapeAreEscaped() {
        assertEquals("%50!%!_!!%", DataMapSearchText.containsPattern("50%_!"));
    }

    @Test
    void chineseSubstringIsWrappedWithoutTokenizing() {
        assertEquals("%订单%", DataMapSearchText.containsPattern("订单"));
    }
}
