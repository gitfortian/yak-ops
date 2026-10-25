package io.yak.ops.business.modeling.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.standard.impl.StandardNamingEntryServiceImpl;
import io.yak.ops.common.bean.dto.modeling.standard.StandardNamingEntryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardNamingEntryUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardReferenceKind;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.standard.StandardNamingEntryEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardNamingEntryRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardReferenceRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardRepository;
import io.yak.ops.workspace.WorkspaceService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/** Naming-dictionary writes: abbreviation shape, uniqueness and models that still name under an entry. */
class StandardNamingEntryServiceTest {
    @Mock StandardNamingEntryRepository entries;
    @Mock StandardReferenceRepository references;
    @Mock StandardRepository nodes;
    @Mock WorkspaceService workspaces;
    @InjectMocks StandardNamingEntryServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        var catalog = StandardNamingEntryServiceImpl.class.getDeclaredField("catalog");
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
    void refusesAnUppercaseAbbreviation() {
        var input = input("Trade");
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));
        assertEquals(StandardErrorCode.INVALID_CODE, error.getErrorCode());
        verify(entries, never()).insert(any());
    }

    @Test
    void refusesABlankDisplayName() {
        var input = input("trade");
        input.setCnName("  ");
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, error.getErrorCode());
    }

    @Test
    void mapsAnAbbreviationCollisionToADuplicateCodeFailure() {
        doThrow(new DuplicateKeyException("uk")).when(entries).insert(any());
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("trade")));
        assertEquals(StandardErrorCode.DUPLICATE_CODE, error.getErrorCode());
    }

    @Test
    void reportsAModelThatStillNamesUnderTheEntry() {
        when(entries.find("ws", "entry")).thenReturn(Optional.of(existing(2)));
        when(references.byNamingRoot("ws", "entry"))
                .thenReturn(List.of(new StandardReferencesVO.Item(
                        StandardReferenceKind.MODEL, "m1", "交易订单", "dwd_trade_order")));
        var result = service.references("ws", "actor", "entry");
        assertEquals(1L, result.count());
    }

    @Test
    void flagsAnEntryANameStillUsesAsUndeletable() {
        when(entries.find("ws", "entry")).thenReturn(Optional.of(existing(2)));
        doThrow(new DataIntegrityViolationException("fk")).when(entries).delete("ws", "entry", 2);
        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", "entry", 2));
        assertEquals(StandardErrorCode.REFERENCED, error.getErrorCode());
    }

    @Test
    void reportsAFreeAbbreviationAsAvailable() {
        when(entries.occupied("ws", "trade", null)).thenReturn(false);
        assertTrue(service.available("ws", "actor", "trade", null));
    }

    @Test
    void reportsATakenAbbreviationAsUnavailable() {
        when(entries.occupied("ws", "trade", "entry")).thenReturn(true);
        when(entries.find("ws", "entry")).thenReturn(Optional.of(existing(2)));
        assertFalse(service.available("ws", "actor", "trade", "entry"));
    }

    @Test
    void refusesAnUpdateAtAStaleRevision() {
        when(entries.find("ws", "entry")).thenReturn(Optional.of(existing(2)));
        var input = new StandardNamingEntryUpdateDTO();
        input.setEnCode("trade");
        input.setEnName("Trade");
        input.setCnName("交易");
        input.setRevision(1L);
        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "entry", input));
        assertEquals(StandardErrorCode.REVISION_CONFLICT, error.getErrorCode());
    }

    private static StandardNamingEntryInputDTO input(String enCode) {
        var input = new StandardNamingEntryInputDTO();
        input.setEnCode(enCode);
        input.setEnName("Trade");
        input.setCnName("交易");
        return input;
    }

    private static StandardNamingEntryEntity existing(long revision) {
        var row = new StandardNamingEntryEntity();
        row.setId("entry");
        row.setWorkspaceId("ws");
        row.setEnCode("trade");
        row.setEnName("Trade");
        row.setCnName("交易");
        row.setRevision(revision);
        return row;
    }
}
