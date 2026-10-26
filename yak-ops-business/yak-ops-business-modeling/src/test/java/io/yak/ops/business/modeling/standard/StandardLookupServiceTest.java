package io.yak.ops.business.modeling.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.standard.impl.StandardLookupServiceImpl;
import io.yak.ops.common.bean.dto.modeling.standard.StandardLookupInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardLookupUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardLookupValueInputDTO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardKind;
import io.yak.ops.common.enums.modeling.standard.StandardNodeType;
import io.yak.ops.common.enums.modeling.standard.StandardReferenceKind;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardLookupEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardLookupValueEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardLookupRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardReferenceRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardRepository;
import io.yak.ops.workspace.WorkspaceService;
import java.util.ArrayList;
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

/** Lookup-table writes: kind confinement, whole-list replacement, revision and reference guards. */
class StandardLookupServiceTest {
    @Mock StandardLookupRepository lookups;
    @Mock StandardReferenceRepository references;
    @Mock StandardRepository nodes;
    @Mock WorkspaceService workspaces;
    @InjectMocks StandardLookupServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        var catalog = StandardLookupServiceImpl.class.getDeclaredField("catalog");
        catalog.setAccessible(true);
        catalog.set(service, new StandardCatalog(nodes, workspaces));
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(nodes.lockCatalog("ws", "actor")).thenReturn(true);
        when(nodes.category("ws", "category")).thenReturn(Optional.of(node(StandardNodeType.DIRECTORY, null)));
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void refusesARecordUnderAStandardSetOfTheOtherKind() {
        when(nodes.category("ws", "category")).thenReturn(Optional.of(node(StandardNodeType.STANDARD_SET, StandardKind.FIELD)));
        var error = assertThrows(
                BusinessException.class, () -> service.create("ws", "actor", "category", input("order_status")));
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, error.getErrorCode());
        verify(lookups, never()).insert(any());
    }

    @Test
    void numbersTheSubmittedValuesAndReplacesTheWholeList() {
        service.create("ws", "actor", "category", input("order_status"));
        var saved = ArgumentCaptor.<List<StandardLookupValueEntity>>captor();
        verify(lookups).replaceValues(anyString(), saved.capture());
        assertEquals(List.of(1, 2), saved.getValue().stream().map(StandardLookupValueEntity::getOrdinal).toList());
        assertEquals(
                List.of("paid", "refunded"),
                saved.getValue().stream().map(StandardLookupValueEntity::getValueCode).toList());
    }

    @Test
    void stripsAValueCodeBeforeStoringIt() {
        var submitted = input("order_status");
        submitted.getValues().getFirst().setValueCode("  paid  ");
        service.create("ws", "actor", "category", submitted);
        var saved = ArgumentCaptor.<List<StandardLookupValueEntity>>captor();
        verify(lookups).replaceValues(anyString(), saved.capture());
        assertEquals("paid", saved.getValue().getFirst().getValueCode());
    }

    @Test
    void refusesDuplicatedValueCodesInsteadOfKeepingAPartialList() {
        var submitted = input("order_status");
        submitted.getValues().add(value("paid"));
        var error =
                assertThrows(BusinessException.class, () -> service.create("ws", "actor", "category", submitted));
        assertEquals(StandardErrorCode.INVALID_LOOKUP_VALUE, error.getErrorCode());
        verify(lookups, never()).replaceValues(anyString(), any());
    }

    @Test
    void refusesValueCodesThatOnlyDifferInCase() {
        // The column collation is case-insensitive, so the database would reject the list; the
        // contract error has to come from us instead of a driver exception turning into a 500.
        var submitted = input("order_status");
        submitted.getValues().add(value("PAID"));
        var error =
                assertThrows(BusinessException.class, () -> service.create("ws", "actor", "category", submitted));
        assertEquals(StandardErrorCode.INVALID_LOOKUP_VALUE, error.getErrorCode());
        verify(lookups, never()).replaceValues(anyString(), any());
    }

    @Test
    void mapsAValueCollisionSeenOnlyByTheDatabaseToTheContractError() {
        doThrow(new DuplicateKeyException("uk_lookup_value"))
                .when(lookups)
                .replaceValues(anyString(), any());
        var error = assertThrows(
                BusinessException.class,
                () -> service.create("ws", "actor", "category", input("order_status")));
        assertEquals(StandardErrorCode.INVALID_LOOKUP_VALUE, error.getErrorCode());
    }

    @Test
    void refusesABlankOrOversizedValueCode() {
        var blank = input("order_status");
        blank.getValues().getFirst().setValueCode("   ");
        assertEquals(
                StandardErrorCode.INVALID_LOOKUP_VALUE,
                assertThrows(BusinessException.class, () -> service.create("ws", "actor", "category", blank))
                        .getErrorCode());

        var oversized = input("order_status");
        oversized.getValues().getFirst().setValueCode("a".repeat(65));
        assertEquals(
                StandardErrorCode.INVALID_LOOKUP_VALUE,
                assertThrows(BusinessException.class, () -> service.create("ws", "actor", "category", oversized))
                        .getErrorCode());
    }

    @Test
    void refusesAnOversizedValueListInsteadOfWritingIt() {
        var submitted = input("order_status");
        while (submitted.getValues().size() <= 1000)
            submitted.getValues().add(value("v" + submitted.getValues().size()));
        var error =
                assertThrows(BusinessException.class, () -> service.create("ws", "actor", "category", submitted));
        assertEquals(StandardErrorCode.INVALID_LOOKUP_VALUE, error.getErrorCode());
        verify(lookups, never()).replaceValues(anyString(), any());
    }

    @Test
    void refusesAMissingValueList() {
        var submitted = input("order_status");
        submitted.setValues(null);
        var error =
                assertThrows(BusinessException.class, () -> service.create("ws", "actor", "category", submitted));
        assertEquals(StandardErrorCode.INVALID_LOOKUP_VALUE, error.getErrorCode());
    }

    @Test
    void mapsACodeCollisionToADuplicateCodeFailure() {
        doThrow(new DuplicateKeyException("uk")).when(lookups).insert(any());
        var error =
                assertThrows(BusinessException.class, () -> service.create("ws", "actor", "category", input("order_status")));
        assertEquals(StandardErrorCode.DUPLICATE_CODE, error.getErrorCode());
    }

    @Test
    void refusesAnUpdateAtAStaleRevision() {
        when(lookups.find("ws", "lookup")).thenReturn(Optional.of(existing(3)));
        var input = new StandardLookupUpdateDTO();
        input.setEnCode("order_status");
        input.setCnName("订单状态");
        input.setEnName("Order Status");
        input.setValues(List.of(value("paid")));
        input.setRevision(2L);
        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "lookup", input));
        assertEquals(StandardErrorCode.REVISION_CONFLICT, error.getErrorCode());
        verify(lookups, never()).update(any());
    }

    @Test
    void flagsAReferencedLookupAsUndeletable() {
        when(lookups.find("ws", "lookup")).thenReturn(Optional.of(existing(3)));
        doThrow(new DataIntegrityViolationException("fk")).when(lookups).delete("ws", "lookup", 3);
        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", "lookup", 3));
        assertEquals(StandardErrorCode.REFERENCED, error.getErrorCode());
    }

    @Test
    void returnsTheDependantsOfALookup() {
        when(lookups.find("ws", "lookup")).thenReturn(Optional.of(existing(3)));
        when(references.byLookup("ws", "lookup"))
                .thenReturn(List.of(new StandardReferencesVO.Item(
                        StandardReferenceKind.FIELD_STANDARD, "std", "订单状态", "订单状态")));
        var result = service.references("ws", "actor", "lookup");
        assertEquals("AVAILABLE", result.availability());
        assertTrue(result.referenced());
        assertEquals(1L, result.count());
    }

    @Test
    void reportsAnUnusedLookupAsDeletable() {
        when(lookups.find("ws", "lookup")).thenReturn(Optional.of(existing(3)));
        when(references.byLookup("ws", "lookup")).thenReturn(List.of());
        var result = service.references("ws", "actor", "lookup");
        assertFalse(result.referenced());
        assertEquals(0L, result.count());
    }

    @Test
    void refusesACategoryFilterNextToAKeyword() {
        var error = assertThrows(
                BusinessException.class, () -> service.page("ws", "actor", "category", "订单", 1, 20));
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, error.getErrorCode());
    }

    @Test
    void refusesAnInvalidCodeWhenCheckingAvailability() {
        var error =
                assertThrows(BusinessException.class, () -> service.available("ws", "actor", "Order_Status", null));
        assertEquals(StandardErrorCode.INVALID_CODE, error.getErrorCode());
    }

    private static StandardLookupInputDTO input(String enCode) {
        var input = new StandardLookupInputDTO();
        input.setEnCode(enCode);
        input.setCnName("订单状态");
        input.setEnName("Order Status");
        input.setValues(new ArrayList<>(List.of(value("paid"), value("refunded"))));
        return input;
    }

    private static StandardLookupValueInputDTO value(String code) {
        var value = new StandardLookupValueInputDTO();
        value.setValueCode(code);
        value.setCnName("已支付");
        value.setEnName("Paid");
        value.setMeaning("订单已支付成功");
        return value;
    }

    private static StandardLookupEntity existing(long revision) {
        var row = new StandardLookupEntity();
        row.setId("lookup");
        row.setWorkspaceId("ws");
        row.setCategoryId("category");
        row.setEnCode("order_status");
        row.setCnName("订单状态");
        row.setEnName("Order Status");
        row.setRevision(revision);
        return row;
    }

    private static CategoryEntity node(StandardNodeType type, StandardKind kind) {
        var row = new CategoryEntity();
        row.setId("category");
        row.setWorkspaceId("ws");
        row.setName("公共标准");
        row.setNodeType(type);
        row.setStandardKind(kind);
        return row;
    }
}
