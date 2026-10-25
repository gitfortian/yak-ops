package io.yak.ops.business.modeling.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedModifierEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricDerivedRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Composition propagation: recomposing every derived metric a part edit touches. */
class MetricCompositionSyncTest {
    @Mock MetricDerivedRepository derived;
    @InjectMocks MetricCompositionSync sync;
    AutoCloseable mocks;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void recomposesEveryDerivedMetricBuiltOnTheEditedPeriod() {
        when(derived.byPeriod("ws", "period")).thenReturn(List.of(row("d1"), row("d2")));
        when(derived.find("ws", "d1")).thenReturn(Optional.of(full("d1", "d7_muying_pay_amt", List.of("muying"))));
        when(derived.find("ws", "d2")).thenReturn(Optional.of(full("d2", "d7_pay_amt", List.of())));
        when(derived.rename(any())).thenReturn(true);

        sync.partEdited("ws", "actor", MetricCompositionSync.Part.PERIOD, "period", "d30", "最近30天");

        var captor = ArgumentCaptor.forClass(MetricDerivedEntity.class);
        verify(derived, times(2)).rename(captor.capture());
        var renamed = captor.getAllValues();
        assertEquals("d30_muying_pay_amt", renamed.get(0).getEnCode());
        assertEquals("最近30天母婴支付金额", renamed.get(0).getCnName());
        assertEquals("d30_pay_amt", renamed.get(1).getEnCode());
        assertEquals("最近30天支付金额", renamed.get(1).getCnName());
    }

    @Test
    void rewritesOnlyTheEditedModifierAmongTheOrderedLinks() {
        when(derived.byModifier("ws", "muying")).thenReturn(List.of(row("d1")));
        when(derived.find("ws", "d1"))
                .thenReturn(Optional.of(full("d1", "d7_muying_vip_pay_amt", List.of("muying", "vip"))));
        when(derived.rename(any())).thenReturn(true);

        sync.partEdited("ws", "actor", MetricCompositionSync.Part.MODIFIER, "muying", "baby", "婴童");

        var captor = ArgumentCaptor.forClass(MetricDerivedEntity.class);
        verify(derived).rename(captor.capture());
        assertEquals("d7_baby_vip_pay_amt", captor.getValue().getEnCode());
        assertEquals("最近7天婴童会员支付金额", captor.getValue().getCnName());
    }

    @Test
    void recomposesTheAtomicSegmentLast() {
        when(derived.byAtomic("ws", "atomic")).thenReturn(List.of(row("d1")));
        when(derived.find("ws", "d1")).thenReturn(Optional.of(full("d1", "d7_muying_pay_amt", List.of("muying"))));
        when(derived.rename(any())).thenReturn(true);

        sync.partEdited("ws", "actor", MetricCompositionSync.Part.ATOMIC, "atomic", "gmv", "成交额");

        var captor = ArgumentCaptor.forClass(MetricDerivedEntity.class);
        verify(derived).rename(captor.capture());
        assertEquals("d7_muying_gmv", captor.getValue().getEnCode());
        assertEquals("最近7天母婴成交额", captor.getValue().getCnName());
    }

    @Test
    void refusesAnEditThatWouldOverflowTheCodeColumn() {
        when(derived.byPeriod("ws", "period")).thenReturn(List.of(row("d1")));
        when(derived.find("ws", "d1")).thenReturn(Optional.of(full("d1", "d7_pay_amt", List.of())));

        var error = assertThrows(
                BusinessException.class,
                () -> sync.partEdited(
                        "ws", "actor", MetricCompositionSync.Part.PERIOD, "period", "p".repeat(58), "长周期"));

        assertEquals(MetricErrorCode.INVALID_DERIVED_METRIC, error.getErrorCode());
        verify(derived, never()).rename(any());
    }

    @Test
    void refusesACollisionWithAnotherDerivedMetric() {
        when(derived.byAtomic("ws", "atomic")).thenReturn(List.of(row("d1")));
        when(derived.find("ws", "d1")).thenReturn(Optional.of(full("d1", "d7_pay_amt", List.of())));
        when(derived.occupied("ws", "d7_gmv", "d1")).thenReturn(true);

        var error = assertThrows(
                BusinessException.class,
                () -> sync.partEdited(
                        "ws", "actor", MetricCompositionSync.Part.ATOMIC, "atomic", "gmv", "成交额"));

        assertEquals(MetricErrorCode.DUPLICATE_CODE, error.getErrorCode());
        verify(derived, never()).rename(any());
    }

    @Test
    void conflictsWhenTheRowMovedUnderTheEdit() {
        when(derived.byAtomic("ws", "atomic")).thenReturn(List.of(row("d1")));
        when(derived.find("ws", "d1")).thenReturn(Optional.of(full("d1", "d7_pay_amt", List.of())));
        when(derived.occupied(anyString(), anyString(), anyString())).thenReturn(false);
        when(derived.rename(any())).thenReturn(false);

        var error = assertThrows(
                BusinessException.class,
                () -> sync.partEdited(
                        "ws", "actor", MetricCompositionSync.Part.ATOMIC, "atomic", "gmv", "成交额"));

        assertEquals(MetricErrorCode.REVISION_CONFLICT, error.getErrorCode());
    }

    @Test
    void leavesEverythingAloneWhenNoDerivedMetricUsesThePart() {
        when(derived.byModifier("ws", "unused")).thenReturn(List.of());

        sync.partEdited("ws", "actor", MetricCompositionSync.Part.MODIFIER, "unused", "unused", "未使用");

        verify(derived, never()).find(anyString(), anyString());
        verify(derived, never()).rename(any());
    }

    private static MetricDerivedEntity row(String id) {
        var row = new MetricDerivedEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        return row;
    }

    private static MetricDerivedEntity full(String id, String enCode, List<String> modifierIds) {
        var row = row(id);
        row.setEnCode(enCode);
        row.setCnName("旧名称");
        row.setAtomicEnCode("pay_amt");
        row.setAtomicCnName("支付金额");
        row.setPeriodEnCode("d7");
        row.setPeriodCnName("最近7天");
        row.setRevision(1);
        row.setModifiers(modifierIds.stream()
                .map(modifierId -> link(modifierId, modifierId, "vip".equals(modifierId) ? "会员" : "母婴"))
                .toList());
        return row;
    }

    private static MetricDerivedModifierEntity link(String modifierId, String enCode, String cnName) {
        var link = new MetricDerivedModifierEntity();
        link.setDerivedId("d1");
        link.setModifierId(modifierId);
        link.setModifierEnCode(enCode);
        link.setModifierCnName(cnName);
        return link;
    }
}
