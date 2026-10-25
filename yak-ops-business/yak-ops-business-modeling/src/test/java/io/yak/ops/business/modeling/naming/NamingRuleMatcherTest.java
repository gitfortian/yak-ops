package io.yak.ops.business.modeling.naming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The head shape a layer rule accepts, expressed without persistence. */
class NamingRuleMatcherTest {
    @Test
    void acceptsAnExactHead() {
        assertEquals(Optional.of("trade"), NamingRuleMatcher.match("trade_order", "order", List.of("trade")));
    }

    @Test
    void acceptsOneQualifierAfterTheHead() {
        assertEquals(
                Optional.of("trade"), NamingRuleMatcher.match("trade_order_1d", "order", List.of("trade")));
    }

    @Test
    void refusesAHeadThatContinuesIntoAnotherWord() {
        assertTrue(NamingRuleMatcher.match("trade_orderbook", "order", List.of("trade")).isEmpty());
        assertTrue(NamingRuleMatcher.match("tradeorder", "order", List.of("trade")).isEmpty());
    }

    @Test
    void usesTheModelsOwnProcessInsteadOfWhateverFollowsTheRoot() {
        assertTrue(NamingRuleMatcher.match("trade_payment", "order", List.of("trade")).isEmpty());
    }

    @Test
    void prefersTheLongestRootSoOverlapsAreDeterministic() {
        // Both "trade" and "trade_order" form a conforming head for this suffix and process.
        var roots = List.of("trade", "trade_order");
        assertEquals(Optional.of("trade_order"), NamingRuleMatcher.match("trade_order_order", "order", roots));
    }

    @Test
    void reportsAnUnknownRootAsUnmatched() {
        assertTrue(NamingRuleMatcher.match("unknown_order", "order", List.of("trade")).isEmpty());
    }

    @Test
    void reportsAMissingProcessOrSuffixAsUnmatched() {
        assertTrue(NamingRuleMatcher.match(null, "order", List.of("trade")).isEmpty());
        assertTrue(NamingRuleMatcher.match("trade_order", null, List.of("trade")).isEmpty());
    }

    @Test
    void refusesAMissingProcessSegment() {
        assertTrue(NamingRuleMatcher.match("trade", "order", List.of("trade")).isEmpty());
    }

    @Test
    void measuresTheHeadAgainstTheSuffixBudget() {
        var root = "a".repeat(35);
        assertTrue(NamingRuleMatcher.fits(root, "abcde"));
        assertFalse(NamingRuleMatcher.fits(root, "abcdef"));
        assertEquals(41, NamingRuleMatcher.head(root, "abcde").length());
    }
}
