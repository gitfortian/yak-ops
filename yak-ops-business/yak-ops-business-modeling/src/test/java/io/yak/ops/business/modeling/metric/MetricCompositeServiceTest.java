package io.yak.ops.business.modeling.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.metric.impl.MetricCompositeServiceImpl;
import io.yak.ops.common.bean.dto.modeling.metric.MetricCompositeInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricCompositeUpdateDTO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.metric.MetricCompositeEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricCompositeRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricDerivedRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
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

/** Composite-metric writes: expression parsing, reference resolution and unrestricted deletion. */
class MetricCompositeServiceTest {
    @Mock MetricCompositeRepository composites;
    @Mock MetricDerivedRepository derived;
    @Mock WorkspaceService workspaces;
    @InjectMocks MetricCompositeServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        var catalog = MetricCompositeServiceImpl.class.getDeclaredField("catalog");
        catalog.setAccessible(true);
        catalog.set(service, new MetricCatalog(workspaces));
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(derived.byCode("ws", "pay_amt")).thenReturn(Optional.of(derivedMetric("d1", "pay_amt")));
        when(derived.byCode("ws", "pay_amt_last"))
                .thenReturn(Optional.of(derivedMetric("d2", "pay_amt_last")));
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void storesTheResolvedReferencesInFirstAppearanceOrder() {
        service.create("ws", "actor", input("(pay_amt - pay_amt_last) / pay_amt_last * 100"));

        var saved = ArgumentCaptor.forClass(MetricCompositeEntity.class);
        verify(composites).insert(saved.capture());
        assertEquals(2, saved.getValue().getReferences().size());
        assertEquals("d1", saved.getValue().getReferences().get(0).getDerivedMetricId());
        assertEquals("d2", saved.getValue().getReferences().get(1).getDerivedMetricId());
    }

    @Test
    void collapsesRepeatedReferences() {
        service.create("ws", "actor", input("pay_amt + pay_amt"));

        var saved = ArgumentCaptor.forClass(MetricCompositeEntity.class);
        verify(composites).insert(saved.capture());
        assertEquals(1, saved.getValue().getReferences().size());
    }

    @Test
    void refusesAReferenceThatIsNotADerivedMetric() {
        when(derived.byCode("ws", "ghost")).thenReturn(Optional.empty());
        var input = input("ghost * 100");

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(MetricErrorCode.INVALID_METRIC_EXPRESSION, error.getErrorCode());
        verify(composites, never()).insert(any());
    }

    @Test
    void namesTheUnknownDerivedCodeInTheErrorDetail() {
        when(derived.byCode("ws", "ghost")).thenReturn(Optional.empty());

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("ghost * 100")));

        assertEquals(MetricErrorCode.INVALID_METRIC_EXPRESSION, error.getErrorCode());
        assertEquals("unknown derived metric code ghost", error.getDetail());
    }

    @Test
    void carriesTheParsePositionInTheErrorDetail() {
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("pay_amt +")));

        assertEquals(MetricErrorCode.INVALID_METRIC_EXPRESSION, error.getErrorCode());
        assertEquals("expression ends at position 10", error.getDetail());
    }

    @Test
    void acceptsAnExpressionAtTheColumnLimit() {
        service.create("ws", "actor", input("pay_amt + ".repeat(204) + " pay_amt"));

        var saved = ArgumentCaptor.forClass(MetricCompositeEntity.class);
        verify(composites).insert(saved.capture());
        assertEquals(1, saved.getValue().getReferences().size());
    }

    @Test
    void refusesAnExpressionBeyondTheColumnLimit() {
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("a".repeat(2049))));

        assertEquals(CommonErrorCode.PARAM_NOT_VALID.getCode(), error.getErrorCode().getCode());
        verify(composites, never()).insert(any());
    }

    @Test
    void refusesAMalformedExpression() {
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("pay_amt +")));

        assertEquals(MetricErrorCode.INVALID_METRIC_EXPRESSION, error.getErrorCode());
    }

    @Test
    void refusesAnExpressionWithoutReferences() {
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("1 + 2 * 3")));

        assertEquals(MetricErrorCode.INVALID_METRIC_EXPRESSION, error.getErrorCode());
    }

    @Test
    void reResolvesReferencesOnAnEdit() {
        var existing = existing(2);
        when(composites.find("ws", "composite")).thenReturn(Optional.of(existing));
        when(composites.update(any(MetricCompositeEntity.class))).thenReturn(true);
        var update = new MetricCompositeUpdateDTO();
        update.setEnCode("growth");
        update.setCnName("增长率");
        update.setExpression("pay_amt");
        update.setRevision(2L);

        service.update("ws", "actor", "composite", update);

        var saved = ArgumentCaptor.forClass(MetricCompositeEntity.class);
        verify(composites).update(saved.capture());
        assertEquals(1, saved.getValue().getReferences().size());
        assertEquals("d1", saved.getValue().getReferences().get(0).getDerivedMetricId());
    }

    @Test
    void deletesWithoutReferenceChecks() {
        when(composites.find("ws", "composite")).thenReturn(Optional.of(existing(2)));
        when(composites.delete("ws", "composite", 2)).thenReturn(true);

        service.delete("ws", "actor", "composite", 2);

        verify(composites).delete("ws", "composite", 2);
    }

    @Test
    void narrowsThePageToTheKeywordOnly() {
        when(composites.count("ws", new MetricQuery(null, null, null, 0, 20))).thenReturn(0L);
        when(composites.page("ws", new MetricQuery(null, null, null, 0, 20))).thenReturn(List.of());

        service.page("ws", "actor", null, 1, 20);

        verify(composites).page("ws", new MetricQuery(null, null, null, 0, 20));
    }

    private static MetricCompositeInputDTO input(String expression) {
        var input = new MetricCompositeInputDTO();
        input.setEnCode("growth");
        input.setCnName("增长率");
        input.setExpression(expression);
        return input;
    }

    private static MetricDerivedEntity derivedMetric(String id, String enCode) {
        var row = new MetricDerivedEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setEnCode(enCode);
        row.setCnName(enCode);
        return row;
    }

    private static MetricCompositeEntity existing(long revision) {
        var row = new MetricCompositeEntity();
        row.setId("composite");
        row.setWorkspaceId("ws");
        row.setEnCode("growth");
        row.setCnName("增长率");
        row.setExpression("pay_amt");
        row.setReferences(List.of());
        row.setRevision(revision);
        return row;
    }
}
