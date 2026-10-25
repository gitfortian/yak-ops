package io.yak.ops.business.modeling.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.metric.impl.MetricAtomicServiceImpl;
import io.yak.ops.common.bean.dto.modeling.metric.MetricAtomicInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricAtomicUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricAtomicVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.enums.modeling.metric.MetricReferenceKind;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.metric.MetricAtomicEntity;
import io.yak.ops.dao.repository.modeling.PlanningRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricAtomicRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import io.yak.ops.dao.repository.modeling.metric.MetricReferenceRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardMeasureUnitRepository;
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

/** Atomic-metric writes: the aggregate matrix, ownership and reference protection. */
class MetricAtomicServiceTest {
    @Mock MetricAtomicRepository atomics;
    @Mock MetricReferenceRepository references;
    @Mock PlanningRepository planning;
    @Mock StandardMeasureUnitRepository units;
    @Mock WorkspaceService workspaces;
    @InjectMocks MetricAtomicServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        var catalog = MetricAtomicServiceImpl.class.getDeclaredField("catalog");
        catalog.setAccessible(true);
        catalog.set(service, new MetricCatalog(workspaces));
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(planning.find("ws", NodeKind.PROCESS, "process")).thenReturn(Optional.of(new io.yak.ops.dao.entity.modeling.PlanningNodeEntity()));
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void refusesAnAggregateThatDoesNotMatchTheType() {
        var input = input();
        input.setDataType(DataType.STRING);
        input.setAggregateType(AggregateType.SUM);

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(MetricErrorCode.INVALID_ATOMIC_METRIC, error.getErrorCode());
        verify(atomics, never()).insert(any());
    }

    @Test
    void acceptsCountOnEveryType() {
        var input = input();
        input.setDataType(DataType.TIMESTAMP);
        input.setAggregateType(AggregateType.COUNT);

        service.create("ws", "actor", input);

        verify(atomics).insert(any());
    }

    @Test
    void refusesAnUnknownBusinessProcess() {
        when(planning.find("ws", NodeKind.PROCESS, "process")).thenReturn(Optional.empty());

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input()));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
    }

    @Test
    void refusesAnUnknownMeasureUnit() {
        var input = input();
        input.setMeasureUnitId("unit");

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
    }

    @Test
    void defaultsTheOwnerToTheCaller() {
        service.create("ws", "actor", input());

        var saved = ArgumentCaptor.forClass(MetricAtomicEntity.class);
        verify(atomics).insert(saved.capture());
        assertEquals("actor", saved.getValue().getOwnerId());
    }

    @Test
    void refusesAnOwnerOutsideTheWorkspace() {
        var input = input();
        input.setOwnerId("stranger");

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(MetricErrorCode.INVALID_OWNER, error.getErrorCode());
    }

    @Test
    void flagsAReferencedMetricAsUndeletable() {
        when(atomics.find("ws", "metric")).thenReturn(Optional.of(existing(2)));
        doThrow(new DataIntegrityViolationException("fk")).when(atomics).delete("ws", "metric", 2);

        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", "metric", 2));

        assertEquals(MetricErrorCode.REFERENCED, error.getErrorCode());
    }

    @Test
    void reportsTheDerivedMetricsBuiltOnAMetric() {
        when(atomics.find("ws", "metric")).thenReturn(Optional.of(existing(2)));
        when(references.byAtomic("ws", "metric"))
                .thenReturn(List.of(new MetricReferencesVO.Item(
                        MetricReferenceKind.DERIVED_METRIC, "derived", "最近7天支付金额", "d7_pay_amt")));

        var result = service.references("ws", "actor", "metric");

        assertEquals(1L, result.count());
    }

    @Test
    void narrowsThePageToTheRequestedProcess() {
        when(atomics.count("ws", new MetricQuery("process", null, null, 0, 20))).thenReturn(0L);
        when(atomics.page("ws", new MetricQuery("process", null, null, 0, 20))).thenReturn(List.of());

        service.page("ws", "actor", "process", null, 1, 20);

        verify(atomics).page("ws", new MetricQuery("process", null, null, 0, 20));
    }

    @Test
    void refusesAStaleRevisionOnUpdate() {
        when(atomics.find("ws", "metric")).thenReturn(Optional.of(existing(2)));
        var update = new MetricAtomicUpdateDTO();
        update.setProcessId("process");
        update.setEnCode("pay_amt");
        update.setCnName("支付金额");
        update.setDataType(DataType.DECIMAL);
        update.setAggregateType(AggregateType.SUM);
        update.setRevision(1L);

        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "metric", update));

        assertEquals(MetricErrorCode.REVISION_CONFLICT, error.getErrorCode());
    }

    @Test
    void projectsTheJoinedProcessAndUnitNames() {
        var row = existing(2);
        row.setProcessName("下单");
        row.setMeasureUnitEnCode("cny");
        when(atomics.find("ws", "metric")).thenReturn(Optional.of(row));

        MetricAtomicVO vo = service.detail("ws", "actor", "metric");

        assertEquals("下单", vo.processName());
        assertEquals("cny", vo.measureUnitEnCode());
    }

    private static MetricAtomicInputDTO input() {
        var input = new MetricAtomicInputDTO();
        input.setProcessId("process");
        input.setEnCode("pay_amt");
        input.setCnName("支付金额");
        input.setDataType(DataType.DECIMAL);
        input.setAggregateType(AggregateType.SUM);
        return input;
    }

    private static MetricAtomicEntity existing(long revision) {
        var row = new MetricAtomicEntity();
        row.setId("metric");
        row.setWorkspaceId("ws");
        row.setProcessId("process");
        row.setEnCode("pay_amt");
        row.setCnName("支付金额");
        row.setDataType(DataType.DECIMAL);
        row.setAggregateType(AggregateType.SUM);
        row.setOwnerId("actor");
        row.setRevision(revision);
        return row;
    }
}
