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
}
