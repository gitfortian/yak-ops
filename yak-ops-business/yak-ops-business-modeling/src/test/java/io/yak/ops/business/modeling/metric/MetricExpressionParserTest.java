package io.yak.ops.business.modeling.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The composite expression grammar: arithmetic, references, and every refused token class. */
class MetricExpressionParserTest {
    @Test
    void collectsReferencesInAppearanceOrder() {
        var references = MetricExpressionParser.references("(pay_amt - pay_amt_last) / pay_amt_last * 100");
        assertEquals(List.of("pay_amt", "pay_amt_last", "pay_amt_last"), references);
    }

    @Test
    void acceptsNumbersParensAndSigns() {
        assertEquals(List.of("a"), MetricExpressionParser.references("-a * (1.5 + 2) / -0.5"));
    }

    @Test
    void keepsIdentifierUnderscores() {
        assertEquals(List.of("d7_muying_pay_amt"), MetricExpressionParser.references("d7_muying_pay_amt"));
    }

    @Test
    void refusesAnEmptyExpression() {
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("   "));
    }

    @Test
    void refusesAMalformedExpression() {
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("a +"));
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("(a - b"));
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("a b"));
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("a * * b"));
    }

    @Test
    void refusesIllegalTokens() {
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("金额 + 1"));
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("SUM(a)"));
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("1..2 + a"));
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("a + 'x'"));
    }

    @Test
    void refusesAnOverlongIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references("a".repeat(65)));
        assertEquals(List.of("a".repeat(64)), MetricExpressionParser.references("a".repeat(64)));
    }

    @Test
    void reportsTheOneBasedCharacterPositionOfEveryFailure() {
        assertEquals("illegal character at position 1", parseError("金额 + 1"));
        assertEquals("malformed number at position 3", parseError("1..2 + a"));
        assertEquals("unexpected token at position 3", parseError("a b"));
        assertEquals("illegal token at position 5", parseError("a * * b"));
        assertEquals("expression ends at position 4", parseError("a +"));
        assertEquals("unbalanced parenthesis at position 7", parseError("(a - b"));
    }

    @Test
    void acceptsDivisionByZeroAsSyntax() {
        // The expression is never evaluated, so division by zero is a runtime concern; banning the
        // literal form would not catch `a / (b - b)` anyway. The grammar accepts it by design.
        assertEquals(List.of("a"), MetricExpressionParser.references("a / 0"));
        assertEquals(List.of("a", "b", "b"), MetricExpressionParser.references("a / (b - b)"));
    }

    private static String parseError(String expression) {
        var failure =
                assertThrows(IllegalArgumentException.class, () -> MetricExpressionParser.references(expression));
        return failure.getMessage();
    }
}
