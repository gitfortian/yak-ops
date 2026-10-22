package io.yak.ops.business.modeling.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.standard.impl.StandardServiceImpl;
import io.yak.ops.common.bean.dto.modeling.standard.CategoryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardUpdateDTO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceMemberVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.enums.modeling.standard.SecurityLevel;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.security.user.UserStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardFieldEntity;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardRepository;
import io.yak.ops.dao.repository.security.UserRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
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
import org.springframework.dao.DuplicateKeyException;

/** Data standard authorization, validation, revision and dependency contracts without external resources. */
class StandardServiceTest {
    @Mock StandardRepository nodes;
    @Mock WorkspaceService workspaces;
    @Mock UserRepository users;
    @InjectMocks StandardServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(workspaces.isActiveMember("ws", "owner")).thenReturn(true);
        when(nodes.lockCatalog("ws", "actor")).thenReturn(true);
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void rejectsInactiveActorsBeforeReadingTheCatalog() {
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(false);
        var error = assertThrows(BusinessException.class, () -> service.tree("ws", "actor"));
        assertEquals(WorkspaceErrorCode.ACCESS_DENIED, error.getErrorCode());
        verifyNoInteractions(nodes, users);
    }

    @Test
    void seedsDefaultRootsOnlyForTheFirstWriter() {
        when(nodes.lockCatalog("ws", "actor")).thenReturn(false);
        when(nodes.categories("ws")).thenReturn(List.of());
        assertTrue(service.initialize("ws", "actor").isEmpty());
        verify(nodes, times(2)).insertCategory(any());
        verify(nodes).markInitialized("ws");
    }

    @Test
    void neverReseedsAnInitializedCatalog() {
        when(nodes.categories("ws")).thenReturn(List.of());
        assertTrue(service.initialize("ws", "actor").isEmpty());
        verify(nodes, never()).insertCategory(any());
        verify(nodes, never()).markInitialized(any());
    }

    @Test
    void rejectsInvalidCodesBeforeWritingAField() {
        when(nodes.category("ws", "category")).thenReturn(Optional.of(category("category")));
        var input = input("Order_Amount");
        var error = assertThrows(
                BusinessException.class, () -> service.createField("ws", "actor", "category", input));
        assertEquals(StandardErrorCode.INVALID_CODE, error.getErrorCode());
        verify(nodes, never()).insertField(any());
    }

    @Test
    void treatsBlankPrecisionAsAbsent() {
        when(nodes.category("ws", "category")).thenReturn(Optional.of(category("category")));
        var input = input("order_amount");
        input.setLengthPrecision("   ");
        service.createField("ws", "actor", "category", input);
        var captured = ArgumentCaptor.forClass(StandardFieldEntity.class);
        verify(nodes).insertField(captured.capture());
        assertNull(captured.getValue().getLengthPrecision());
    }

    @Test
    void acceptsDecimalPrecisionAndRejectsMismatchedOnes() {
        when(nodes.category("ws", "category")).thenReturn(Optional.of(category("category")));
        var decimal = input("order_amount");
        decimal.setDataType(DataType.DECIMAL);
        decimal.setLengthPrecision("18,2");
        service.createField("ws", "actor", "category", decimal);

        assertPrecisionRejected(precision(DataType.BIGINT, "18,2"));
        assertPrecisionRejected(precision(DataType.DECIMAL, "39,2"));
        assertPrecisionRejected(precision(DataType.DECIMAL, "8,9"));
        assertPrecisionRejected(precision(DataType.STRING, "0"));
        assertPrecisionRejected(precision(DataType.STRING, "2147483648"));
        assertPrecisionRejected(precision(DataType.DATE, "8"));
    }

    @Test
    void requiresAnActiveWorkspaceOwner() {
        when(nodes.category("ws", "category")).thenReturn(Optional.of(category("category")));
        when(workspaces.isActiveMember("ws", "stranger")).thenReturn(false);
        var input = input("order_amount");
        input.setOwnerId("stranger");
        var error = assertThrows(
                BusinessException.class, () -> service.createField("ws", "actor", "category", input));
        assertEquals(StandardErrorCode.INVALID_OWNER, error.getErrorCode());
        verify(nodes, never()).insertField(any());
    }

    @Test
    void mapsDuplicateCodesWithoutLeakingTheDatabaseCause() {
        when(nodes.category("ws", "category")).thenReturn(Optional.of(category("category")));
        var cause = new DuplicateKeyException("opaque internal duplicate diagnostic");
        doThrow(cause).when(nodes).insertField(any());
        var error = assertThrows(
                BusinessException.class,
                () -> service.createField("ws", "actor", "category", input("order_amount")));
        assertEquals(StandardErrorCode.DUPLICATE_CODE, error.getErrorCode());
        assertSame(cause, error.getCause());
        assertFalse(error.getMessage().contains("diagnostic"));
    }

    @Test
    void rejectsStaleRevisionsBeforeWriting() {
        when(nodes.field("ws", "field")).thenReturn(Optional.of(field("field", 7)));
        var error = assertThrows(
                BusinessException.class,
                () -> service.updateField("ws", "actor", "field", update("order_amount", 6)));
        assertEquals(StandardErrorCode.REVISION_CONFLICT, error.getErrorCode());
        verify(nodes, never()).updateField(any());
    }

    @Test
    void advancesTheRevisionEvenWhenNothingChanged() {
        var field = field("field", 7);
        when(nodes.field("ws", "field")).thenReturn(Optional.of(field));
        when(nodes.updateField(field)).thenReturn(true);
        assertEquals(
                8,
                service.updateField("ws", "actor", "field", update("order_amount", 7))
                        .revision());
        verify(nodes).updateField(field);
    }

    @Test
    void blocksDeletionWhileFieldsOrChildrenRemain() {
        var parent = category("parent");
        var child = category("child");
        child.setParentId("parent");
        when(nodes.category("ws", "parent")).thenReturn(Optional.of(parent));
        when(nodes.categories("ws")).thenReturn(List.of(parent, child));
        when(nodes.hasFields("ws", "parent")).thenReturn(true);
        var error = assertThrows(
                BusinessException.class, () -> service.deleteCategory("ws", "actor", "parent", 1));
        assertEquals(StandardErrorCode.HAS_DEPENDENCIES, error.getErrorCode());
        verify(nodes, never()).deleteCategory("ws", "parent", 1);
    }

    @Test
    void reportsCrossWorkspaceIdentitiesAsMissing() {
        when(nodes.category("ws", "foreign")).thenReturn(Optional.empty());
        var error = assertThrows(
                BusinessException.class, () -> service.deleteCategory("ws", "actor", "foreign", 1));
        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
        verify(nodes, never()).deleteCategory(any(), any(), anyLong());
    }

    @Test
    void authorizesTheExcludedFieldBeforeTheGlobalAvailabilityProbe() {
        when(nodes.field("ws", "foreign")).thenReturn(Optional.empty());
        var error = assertThrows(
                BusinessException.class, () -> service.available("ws", "actor", "order_amount", "foreign"));
        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
        verify(nodes, never()).occupied(any(), any());
        when(nodes.occupied("order_amount", null)).thenReturn(true);
        assertFalse(service.available("ws", "actor", "order_amount", null));
    }

    @Test
    void rejectsCategoriesBeyondTheSupportedDepth() {
        var chain = new ArrayList<CategoryEntity>();
        for (int index = 0; index < 32; index++) {
            var node = category("level" + index);
            node.setParentId(index == 0 ? null : "level" + (index - 1));
            chain.add(node);
        }
        when(nodes.category("ws", "level31")).thenReturn(Optional.of(chain.getLast()));
        when(nodes.categories("ws")).thenReturn(chain);
        var input = new CategoryInputDTO();
        input.setParentId("level31");
        input.setName("溢出层级");
        var error = assertThrows(
                BusinessException.class, () -> service.createCategory("ws", "actor", input));
        assertEquals(StandardErrorCode.DEPTH_LIMIT, error.getErrorCode());
        verify(nodes, never()).insertCategory(any());
    }

    @Test
    void rejectsAmbiguousScopesAndOutOfRangePaging() {
        for (Runnable call : List.<Runnable>of(
                () -> service.page("ws", "actor", "category", "keyword", 1, 20),
                () -> service.page("ws", "actor", null, null, 1, 20),
                () -> service.page("ws", "actor", "category", null, 0, 20),
                () -> service.page("ws", "actor", "category", null, 1, 101))) {
            var error = assertThrows(BusinessException.class, call::run);
            assertEquals(CommonErrorCode.PARAM_NOT_VALID, error.getErrorCode());
        }
        verifyNoInteractions(nodes);
    }

    @Test
    void projectsActiveOwnersOnly() {
        var member = new WorkspaceMemberVO();
        member.setUserId("owner");
        var disabled = new UserEntity();
        disabled.setId("left");
        disabled.setUserName("left-account");
        disabled.setStatus(UserStatus.DISABLED);
        var active = new UserEntity();
        active.setId("owner");
        active.setUserName("owner-account");
        active.setStatus(UserStatus.ACTIVE);
        when(workspaces.queryWorkspaceMembers("ws", "actor")).thenReturn(List.of(member));
        when(users.queryByIds(List.of("owner"))).thenReturn(List.of(active, disabled));
        var owners = service.owners("ws", "actor");
        assertEquals(1, owners.size());
        assertEquals("owner-account", owners.getFirst().displayName());
    }

    @Test
    void reportsReferencesAsExplicitlyDisabledForOwnedFields() {
        when(nodes.field("ws", "field")).thenReturn(Optional.of(field("field", 1)));
        assertEquals(
                "NOT_ENABLED", service.references("ws", "actor", "field").availability());
    }

    private void assertPrecisionRejected(StandardInputDTO input) {
        var error = assertThrows(
                BusinessException.class, () -> service.createField("ws", "actor", "category", input));
        assertEquals(StandardErrorCode.INVALID_PRECISION, error.getErrorCode());
    }

    private static StandardInputDTO precision(DataType type, String value) {
        var input = input("order_amount");
        input.setDataType(type);
        input.setLengthPrecision(value);
        return input;
    }

    static StandardInputDTO input(String code) {
        var input = new StandardInputDTO();
        input.setCnName("订单金额");
        input.setEnCode(code);
        input.setDataType(DataType.STRING);
        input.setIsNotNull(false);
        input.setSecurityLevel(SecurityLevel.INTERNAL);
        input.setOwnerId("owner");
        return input;
    }

    static StandardUpdateDTO update(String code, long revision) {
        var input = new StandardUpdateDTO();
        input.setCnName("订单金额");
        input.setEnCode(code);
        input.setDataType(DataType.STRING);
        input.setIsNotNull(false);
        input.setSecurityLevel(SecurityLevel.INTERNAL);
        input.setOwnerId("owner");
        input.setRevision(revision);
        return input;
    }

    static CategoryEntity category(String id) {
        var node = new CategoryEntity();
        node.setWorkspaceId("ws");
        node.setId(id);
        node.setName("目录" + id);
        node.setRevision(1);
        return node;
    }

    static StandardFieldEntity field(String id, long revision) {
        var node = new StandardFieldEntity();
        node.setWorkspaceId("ws");
        node.setId(id);
        node.setCategoryId("category");
        node.setCnName("订单金额");
        node.setEnCode("order_amount");
        node.setDataType(DataType.STRING);
        node.setIsNotNull(false);
        node.setSecurityLevel(SecurityLevel.INTERNAL);
        node.setOwnerId("owner");
        node.setRevision(revision);
        return node;
    }
}
