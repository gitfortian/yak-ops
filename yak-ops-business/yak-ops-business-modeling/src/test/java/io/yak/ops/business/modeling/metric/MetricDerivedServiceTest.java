package io.yak.ops.business.modeling.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.metric.impl.MetricDerivedServiceImpl;
import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.enums.modeling.metric.MetricReferenceKind;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.metric.MetricAtomicEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricModifierEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricTimePeriodEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricAtomicRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricDerivedRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricModifierRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import io.yak.ops.dao.repository.modeling.metric.MetricReferenceRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricTimePeriodRepository;
import io.yak.ops.workspace.WorkspaceService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/** Derived-metric composition: parts resolution, collision, column limits and delete protection. */
class MetricDerivedServiceTest {
    @Mock MetricDerivedRepository derived;
    @Mock MetricAtomicRepository atomics;
    @Mock MetricTimePeriodRepository periods;
    @Mock MetricModifierRepository modifiers;
    @Mock MetricReferenceRepository references;
    @Mock MetricCompositionSync composition;
    @Mock WorkspaceService workspaces;
    @InjectMocks MetricDerivedServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        var catalog = MetricDerivedServiceImpl.class.getDeclaredField("catalog");
        catalog.setAccessible(true);
        catalog.set(service, new MetricCatalog(workspaces));
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(atomics.find("ws", "atomic"))
                .thenReturn(Optional.of(atomic("atomic", "pay_amt", "支付金额")));
        when(periods.find("ws", "period"))
                .thenReturn(Optional.of(period("period", "d7", "最近7天")));
        when(modifiers.find("ws", "muying"))
                .thenReturn(Optional.of(modifier("muying", "muying", "母婴")));
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void composesTheCodeAndNameFromTheSubmittedParts() {
        service.create("ws", "actor", input("atomic", "period", List.of("muying")));

        var saved = ArgumentCaptor.forClass(MetricDerivedEntity.class);
        verify(derived).insert(saved.capture());
        assertEquals("d7_muying_pay_amt", saved.getValue().getEnCode());
        assertEquals("最近7天母婴支付金额", saved.getValue().getCnName());
        assertEquals(1, saved.getValue().getModifiers().size());
        assertEquals("muying", saved.getValue().getModifiers().get(0).getModifierId());
    }

    @Test
    void keepsTheModifierOrderInTheComposition() {
        when(modifiers.find("ws", "vip")).thenReturn(Optional.of(modifier("vip", "vip", "会员")));

        service.create("ws", "actor", input("atomic", "period", List.of("vip", "muying")));

        var saved = ArgumentCaptor.forClass(MetricDerivedEntity.class);
        verify(derived).insert(saved.capture());
        assertEquals("d7_vip_muying_pay_amt", saved.getValue().getEnCode());
        assertEquals("最近7天会员母婴支付金额", saved.getValue().getCnName());
    }

    @Test
    void refusesTheSameModifierTwice() {
        var error = assertThrows(
                BusinessException.class, () -> service.create("ws", "actor", input("atomic", "period", List.of("muying", "muying"))));
        assertEquals(MetricErrorCode.INVALID_DERIVED_METRIC, error.getErrorCode());
        verify(derived, never()).insert(any());
    }

    @Test
    void refusesAPartOutsideTheWorkspace() {
        when(periods.find("ws", "period")).thenReturn(Optional.empty());

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("atomic", "period", List.of())));

        assertEquals(MetricErrorCode.INVALID_DERIVED_METRIC, error.getErrorCode());
    }

    @Test
    void refusesACompositionBeyondTheColumnLimits() {
        // 50 + 1 + 6 + 1 + 7 = 65 characters, one past the 64-character code column.
        when(periods.find("ws", "period"))
                .thenReturn(Optional.of(period("period", "p".repeat(50), "近".repeat(64))));

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("atomic", "period", List.of("muying"))));

        assertEquals(MetricErrorCode.INVALID_DERIVED_METRIC, error.getErrorCode());
    }

    @Test
    void reportsAnIdenticalPartsCollisionAsADuplicate() {
        doThrow(new DuplicateKeyException("uk")).when(derived).insert(any());

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("atomic", "period", List.of())));

        assertEquals(MetricErrorCode.DUPLICATE_CODE, error.getErrorCode());
    }

    @Test
    void answersAvailabilityFromTheComposedCode() {
        when(derived.occupied("ws", "d7_muying_pay_amt", null)).thenReturn(true);

        var free = service.available("ws", "actor", "atomic", "period", List.of("muying"), null);

        assertEquals(false, free);
    }

    @Test
    void regeneratesTheCompositionOnAnEdit() {
        when(derived.find("ws", "metric")).thenReturn(Optional.of(existing(2)));
        when(derived.update(any(MetricDerivedEntity.class))).thenReturn(true);
        var update = new MetricDerivedUpdateDTO();
        update.setAtomicMetricId("atomic");
        update.setPeriodId("period");
        update.setModifierIds(List.of("muying"));
        update.setRevision(2L);

        var saved = service.update("ws", "actor", "metric", update);

        var stored = ArgumentCaptor.forClass(MetricDerivedEntity.class);
        verify(derived).update(stored.capture());
        assertEquals("d7_muying_pay_amt", stored.getValue().getEnCode());
        assertEquals("最近7天母婴支付金额", stored.getValue().getCnName());
        assertEquals(3L, saved.revision());
        // The old code is gone from the row, so every composite expression citing it has to follow.
        verify(composition).renamed("ws", "actor", "metric", "d7_pay_amt", "d7_muying_pay_amt");
    }

    @Test
    void flagsAReferencedMetricAsUndeletable() {
        when(derived.find("ws", "metric")).thenReturn(Optional.of(existing(2)));
        doThrow(new DataIntegrityViolationException("fk")).when(derived).delete("ws", "metric", 2);

        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", "metric", 2));

        assertEquals(MetricErrorCode.REFERENCED, error.getErrorCode());
    }

    @Test
    void reportsTheCompositesAndFieldsThatDependOnAMetric() {
        when(derived.find("ws", "metric")).thenReturn(Optional.of(existing(2)));
        when(references.byDerived("ws", "metric"))
                .thenReturn(List.of(new MetricReferencesVO.Item(
                                MetricReferenceKind.COMPOSITE_METRIC, "composite", "增长率", "growth"),
                        new MetricReferencesVO.Item(
                                MetricReferenceKind.MODEL_FIELD, "model", "pay_amt", "dws_trade_order.pay_amt")));

        var result = service.references("ws", "actor", "metric");

        assertEquals(2L, result.count());
    }

    @Test
    void narrowsThePageToTheAtomicMetricProcess() {
        when(derived.count("ws", new MetricQuery("process", null, null, 0, 20))).thenReturn(0L);
        when(derived.page("ws", new MetricQuery("process", null, null, 0, 20))).thenReturn(List.of());

        service.page("ws", "actor", "process", null, 1, 20);

        verify(derived).page("ws", new MetricQuery("process", null, null, 0, 20));
    }

    private static MetricDerivedInputDTO input(String atomic, String period, List<String> modifierIds) {
        var input = new MetricDerivedInputDTO();
        input.setAtomicMetricId(atomic);
        input.setPeriodId(period);
        input.setModifierIds(modifierIds);
        return input;
    }

    private static MetricAtomicEntity atomic(String id, String enCode, String cnName) {
        var row = new MetricAtomicEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setEnCode(enCode);
        row.setCnName(cnName);
        return row;
    }

    private static MetricTimePeriodEntity period(String id, String enCode, String cnName) {
        var row = new MetricTimePeriodEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setEnCode(enCode);
        row.setCnName(cnName);
        return row;
    }

    private static MetricModifierEntity modifier(String id, String enCode, String cnName) {
        var row = new MetricModifierEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setEnCode(enCode);
        row.setCnName(cnName);
        return row;
    }

    private static MetricDerivedEntity existing(long revision) {
        var row = new MetricDerivedEntity();
        row.setId("metric");
        row.setWorkspaceId("ws");
        row.setEnCode("d7_pay_amt");
        row.setCnName("最近7天支付金额");
        row.setAtomicMetricId("atomic");
        row.setPeriodId("period");
        row.setModifiers(List.of());
        row.setRevision(revision);
        return row;
    }
}
