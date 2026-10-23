package io.yak.ops.business.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.dimension.impl.DimensionServiceImpl;
import io.yak.ops.common.bean.dto.modeling.DimensionInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateDimensionDTO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.DimensionEntity;
import io.yak.ops.dao.repository.modeling.DimensionRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Concept dimension scope rules, per-scope uniqueness and revision contracts without a database. */
class DimensionServiceTest {
    @Mock DimensionRepository dimensions;
    @Mock WorkspaceService workspaces;
    @InjectMocks DimensionServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(dimensions.domainExists("ws", "domain")).thenReturn(true);
        when(dimensions.datamartExists("ws", "mart")).thenReturn(true);
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void rejectsInactiveActorsBeforeReadingDimensions() {
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(false);

        var error = assertThrows(BusinessException.class, () -> service.list("ws", "actor", null, null, null));

        assertEquals(WorkspaceErrorCode.ACCESS_DENIED, error.getErrorCode());
        verifyNoInteractions(dimensions);
    }

    @Test
    void bindsAPublicDimensionToItsDomainAndDropsTheDataMart() {
        var captor = ArgumentCaptor.forClass(DimensionEntity.class);
        var stored = row("dimension", "domain", "order");
        stored.setDomainName("交易域");
        when(dimensions.find(eq("ws"), anyString())).thenReturn(Optional.of(stored));

        var saved = service.create("ws", "actor", input(DimensionScope.PUBLIC, "domain", "mart", "order"));

        verify(dimensions).add(captor.capture());
        var row = captor.getValue();
        assertEquals(DimensionScope.PUBLIC, row.getScope());
        assertEquals("domain", row.getDomainId());
        assertNull(row.getDatamartId());
        assertEquals("order", row.getCode());
        assertEquals(1, row.getRevision());
        assertEquals("交易域", saved.domainName());
    }

    @Test
    void requiresTheScopeIdentifierOwnedByTheSelectedScope() {
        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", input(DimensionScope.APPLICATION, "domain", null, "order")));

        assertEquals(DimensionModelingErrorCode.INVALID_SCOPE, error.getErrorCode());
        verify(dimensions, never()).add(any());
    }

    @Test
    void rejectsAScopeThatDoesNotBelongToTheWorkspace() {
        when(dimensions.domainExists("ws", "domain")).thenReturn(false);

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", input(DimensionScope.PUBLIC, "domain", null, "order")));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
        verify(dimensions, never()).add(any());
    }

    @Test
    void rejectsADuplicateAbbreviationInsideTheSameScope() {
        when(dimensions.countCode("ws", "domain", "order", null)).thenReturn(1L);

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", input(DimensionScope.PUBLIC, "domain", null, "order")));

        assertEquals(DimensionModelingErrorCode.DUPLICATE_CODE, error.getErrorCode());
        verify(dimensions, never()).add(any());
    }

    @Test
    void allowsTheSameAbbreviationInAnotherScope() {
        when(dimensions.countCode("ws", "mart", "order", null)).thenReturn(0L);
        var stored = row("dimension", null, "order");
        stored.setScope(DimensionScope.APPLICATION);
        stored.setDatamartId("mart");
        stored.setDatamartName("运营集市");
        when(dimensions.find(eq("ws"), anyString())).thenReturn(Optional.of(stored));

        var saved = service.create("ws", "actor", input(DimensionScope.APPLICATION, null, "mart", "order"));

        assertEquals(DimensionScope.APPLICATION, saved.scope());
        assertEquals("运营集市", saved.datamartName());
        verify(dimensions).countCode("ws", "mart", "order", null);
    }

    @Test
    void rejectsAbbreviationsThatBreakTheLowercaseContract() {
        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", input(DimensionScope.PUBLIC, "domain", null, "Order-1")));

        assertEquals(DimensionModelingErrorCode.INVALID_CODE, error.getErrorCode());
    }

    @Test
    void refusesToMoveADimensionToAnotherScope() {
        when(dimensions.lock("ws", "dimension")).thenReturn(Optional.of(row("dimension", "domain", "order")));

        var moved = update(DimensionScope.APPLICATION, null, "mart", "order", 3);
        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "dimension", moved));

        assertEquals(DimensionModelingErrorCode.INVALID_SCOPE, error.getErrorCode());
        verify(dimensions, never()).update(any());
    }

    @Test
    void rejectsStaleRevisionsBeforeWriting() {
        when(dimensions.lock("ws", "dimension")).thenReturn(Optional.of(row("dimension", "domain", "order")));

        var error = assertThrows(BusinessException.class,
                () -> service.update("ws", "actor", "dimension", update(DimensionScope.PUBLIC, "domain", null, "order", 2)));

        assertEquals(DimensionModelingErrorCode.REVISION_CONFLICT, error.getErrorCode());
        verify(dimensions, never()).update(any());
    }

    @Test
    void updatesFieldsAndAdvancesTheRevision() {
        var locked = row("dimension", "domain", "order");
        locked.setDomainName("交易域");
        when(dimensions.lock("ws", "dimension")).thenReturn(Optional.of(locked));
        when(dimensions.find("ws", "dimension")).thenReturn(Optional.of(locked));
        when(dimensions.update(any())).thenReturn(true);

        var saved = service.update(
                "ws", "actor", "dimension", update(DimensionScope.PUBLIC, "domain", null, "orders", 3));

        assertEquals(4, saved.revision());
        assertEquals("orders", saved.code());
        assertEquals("交易域", saved.domainName());
        verify(dimensions).countCode("ws", "domain", "orders", "dimension");
    }

    @Test
    void deletesWithTheMatchingRevision() {
        when(dimensions.lock("ws", "dimension")).thenReturn(Optional.of(row("dimension", "domain", "order")));
        when(dimensions.delete("ws", "dimension", 3)).thenReturn(true);

        service.delete("ws", "actor", "dimension", 3);

        verify(dimensions).delete("ws", "dimension", 3);
    }

    @Test
    void reportsAMissingDimensionAsNotExisting() {
        when(dimensions.lock("ws", "missing")).thenReturn(Optional.empty());

        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", "missing", 1));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
    }

    @Test
    void rejectsAForeignScopeIdentifierBeforeQuerying() {
        when(dimensions.domainExists("ws", "elsewhere")).thenReturn(false);

        var error = assertThrows(BusinessException.class,
                () -> service.list("ws", "actor", DimensionScope.PUBLIC, "elsewhere", null));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
        verify(dimensions, never()).dimensions(anyString(), any(), anyString(), isNull());
    }

    static DimensionInputDTO input(DimensionScope scope, String domainId, String datamartId, String code) {
        var input = new DimensionInputDTO();
        input.setScope(scope);
        input.setDomainId(domainId);
        input.setDatamartId(datamartId);
        input.setName("商品");
        input.setEnName("Product");
        input.setCode(code);
        return input;
    }

    static UpdateDimensionDTO update(
            DimensionScope scope, String domainId, String datamartId, String code, long revision) {
        var input = new UpdateDimensionDTO();
        input.setScope(scope);
        input.setDomainId(domainId);
        input.setDatamartId(datamartId);
        input.setName("商品");
        input.setEnName("Product");
        input.setCode(code);
        input.setRevision(revision);
        return input;
    }

    static DimensionEntity row(String id, String domainId, String code) {
        var row = new DimensionEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setScope(DimensionScope.PUBLIC);
        row.setDomainId(domainId);
        row.setName("商品");
        row.setEnName("Product");
        row.setCode(code);
        row.setRevision(3);
        return row;
    }
}
