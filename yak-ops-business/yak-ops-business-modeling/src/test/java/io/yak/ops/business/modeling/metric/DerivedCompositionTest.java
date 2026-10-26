package io.yak.ops.business.modeling.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Composition rules shared by the server and the derived-metric contract. */
class DerivedCompositionTest {
    @Test
    void joinsTheCodeWithUnderscoresInTheSubmittedOrder() {
        var composed = DerivedComposition.compose("d7", List.of("muying", "vip"), "pay_amt", "最近7天", List.of("母婴", "会员"), "支付金额");
        assertEquals("d7_muying_vip_pay_amt", composed.enCode());
        assertEquals("最近7天母婴会员支付金额", composed.cnName());
    }

    @Test
    void composesWithoutModifiers() {
        var composed = DerivedComposition.compose("d1", List.of(), "pay_amt", "昨日", List.of(), "支付金额");
        assertEquals("d1_pay_amt", composed.enCode());
        assertEquals("昨日支付金额", composed.cnName());
    }

    @Test
    void collapsesTheOverlapBetweenAModifierAndTheAtomicName() {
        var composed = DerivedComposition.compose("d7", List.of("paid"), "pay_amount", "最近7天", List.of("支付"), "支付金额");

        assertEquals("d7_paid_pay_amount", composed.enCode());
        assertEquals("最近7天支付金额", composed.cnName());
    }

    @Test
    void keepsOneCopyOfARepeatedSegment() {
        var composed = DerivedComposition.compose("d7", List.of("d7"), "pay_amount", "最近7天", List.of("最近7天"), "支付金额");

        assertEquals("d7_pay_amount", composed.enCode());
        assertEquals("最近7天支付金额", composed.cnName());
    }

    @Test
    void acceptsValuesAtTheColumnLimits() {
        var period = "p".repeat(20);
        var modifier = "m".repeat(20);
        var atomic = "a".repeat(20);
        var composed = DerivedComposition.compose(period, List.of(modifier), atomic, "近".repeat(42), List.of("饰".repeat(42)), "支".repeat(42));
        assertEquals(62, composed.enCode().length());
        assertEquals(126, composed.cnName().length());
        assertTrue(DerivedComposition.fits(composed));
    }

    @Test
    void refusesValuesBeyondTheColumnLimits() {
        var composed = new DerivedComposition.Composed("a".repeat(65), "名");
        assertFalse(DerivedComposition.fits(composed));
        var longName = new DerivedComposition.Composed("code", "名".repeat(129));
        assertFalse(DerivedComposition.fits(longName));
    }
}
