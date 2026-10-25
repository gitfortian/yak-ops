package io.yak.ops.business.modeling.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.standard.impl.StandardMeasureUnitServiceImpl;
import io.yak.ops.common.bean.dto.modeling.standard.StandardMeasureUnitInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardMeasureUnitUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.MeasureUnitCategory;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardReferenceKind;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.standard.StandardMeasureUnitEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardMeasureUnitRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardQuery;
import io.yak.ops.dao.repository.modeling.standard.StandardReferenceRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardRepository;
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

/** Measurement-unit writes: category requirement, revision guard and reference protection. */
class StandardMeasureUnitServiceTest {
    @Mock StandardMeasureUnitRepository units;
    @Mock StandardReferenceRepository references;
    @Mock StandardRepository nodes;
    @Mock WorkspaceService workspaces;
    @InjectMocks StandardMeasureUnitServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        var catalog = StandardMeasureUnitServiceImpl.class.getDeclaredField("catalog");
        catalog.setAccessible(true);
        catalog.set(service, new StandardCatalog(nodes, workspaces));
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(nodes.lockCatalog("ws", "actor")).thenReturn(true);
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void requiresACategory() {
        var input = input();
        input.setCategory(null);
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, error.getErrorCode());
        verify(units, never()).insert(any());
    }

    @Test
    void keepsTheSubmittedCategoryAndStripsNames() {
        var input = input();
        input.setCnName("  元  ");
        service.create("ws", "actor", input);
        var saved = ArgumentCaptor.forClass(StandardMeasureUnitEntity.class);
        verify(units).insert(saved.capture());
        assertEquals(MeasureUnitCategory.CURRENCY, saved.getValue().getCategory());
        assertEquals("元", saved.getValue().getCnName());
        assertEquals(1, saved.getValue().getRevision());
    }

    @Test
    void refusesAnInvalidCode() {
        var input = input();
        input.setEnCode("CNY ");
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));
        assertEquals(StandardErrorCode.INVALID_CODE, error.getErrorCode());
    }

    @Test
    void refusesAnUpdateAtAStaleRevision() {
        when(units.find("ws", "unit")).thenReturn(Optional.of(existing(2)));
        var input = new StandardMeasureUnitUpdateDTO();
        input.setCategory(MeasureUnitCategory.CURRENCY);
        input.setEnCode("cny");
        input.setCnName("元");
        input.setEnName("Yuan");
        input.setRevision(1L);
        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "unit", input));
        assertEquals(StandardErrorCode.REVISION_CONFLICT, error.getErrorCode());
        verify(units, never()).update(any());
    }

    @Test
    void flagsAReferencedUnitAsUndeletable() {
        when(units.find("ws", "unit")).thenReturn(Optional.of(existing(2)));
        doThrow(new DataIntegrityViolationException("fk")).when(units).delete("ws", "unit", 2);
        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", "unit", 2));
        assertEquals(StandardErrorCode.REFERENCED, error.getErrorCode());
    }

    @Test
    void narrowsThePageToTheRequestedCategory() {
        when(units.count("ws", new StandardQuery(null, "TIME", null, 0, 20))).thenReturn(0L);
        when(units.page("ws", new StandardQuery(null, "TIME", null, 0, 20))).thenReturn(List.of());
        service.page("ws", "actor", MeasureUnitCategory.TIME, null, 1, 20);
        verify(units).page("ws", new StandardQuery(null, "TIME", null, 0, 20));
    }

    @Test
    void reportsTheDependantsOfAUnit() {
        when(units.find("ws", "unit")).thenReturn(Optional.of(existing(2)));
        when(references.byUnit("ws", "unit"))
                .thenReturn(List.of(new StandardReferencesVO.Item(
                        StandardReferenceKind.MODEL_FIELD, "model", "订单金额", "dwd_trade_order.order_amt")));
        var result = service.references("ws", "actor", "unit");
        assertEquals(1L, result.count());
    }

    private static StandardMeasureUnitInputDTO input() {
        var input = new StandardMeasureUnitInputDTO();
        input.setCategory(MeasureUnitCategory.CURRENCY);
        input.setEnCode("cny");
        input.setCnName("元");
        input.setEnName("Yuan");
        return input;
    }

    private static StandardMeasureUnitEntity existing(long revision) {
        var row = new StandardMeasureUnitEntity();
        row.setId("unit");
        row.setWorkspaceId("ws");
        row.setCategory(MeasureUnitCategory.CURRENCY);
        row.setEnCode("cny");
        row.setCnName("元");
        row.setEnName("Yuan");
        row.setRevision(revision);
        return row;
    }
}
