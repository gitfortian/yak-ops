package io.yak.ops.business.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.impl.PlanningServiceImpl;
import io.yak.ops.common.bean.dto.modeling.DatamartInputDTO;
import io.yak.ops.common.bean.dto.modeling.NodeInputDTO;
import io.yak.ops.common.bean.dto.modeling.SubjectInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateNodeDTO;
import io.yak.ops.common.bean.vo.modeling.AssociatedModelsVO;
import io.yak.ops.common.bean.vo.modeling.LogicalModelSummaryVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.PlanningNodeEntity;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.PlanningRepository;
import io.yak.ops.dao.repository.security.UserRepository;
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
import org.springframework.dao.DuplicateKeyException;

/** Planning authorization, hierarchy locking, revision and error contracts without external resources. */
class PlanningServiceTest {
    @Mock PlanningRepository nodes;
    @Mock ModelRepository models;
    @Mock WorkspaceService workspaces;
    @Mock UserRepository users;
    @InjectMocks PlanningServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(workspaces.isActiveMember("ws", "owner")).thenReturn(true);
    }
    @AfterEach void close() throws Exception { mocks.close(); }

    @Test void rejectsInvalidCodeBeforePersistence() {
        var input = input("Invalid_Code");
        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", NodeKind.CATEGORY, null, input));
        assertEquals(ModelingErrorCode.INVALID_CODE, error.getErrorCode());
        verifyNoInteractions(nodes);
    }

    @Test void rejectsInactiveActorsBeforeReadingNodesOrUserProfiles() {
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.tree("ws", "actor"));
        verifyNoInteractions(nodes, users);
    }

    @Test void assemblesOneImmutableTreeWithoutExposingUserPrivateFields() throws Exception {
        var category = node(NodeKind.CATEGORY, "category", null);
        var domain = node(NodeKind.DOMAIN, "domain", "category");
        category.setOwnerId("owner");
        domain.setOwnerId("owner");
        var owner = new io.yak.ops.dao.entity.security.UserEntity();
        owner.setId("owner");
        owner.setUserName("owner-account");
        owner.setPw("test-only-private-value");
        var member = new io.yak.ops.common.bean.vo.workspace.WorkspaceMemberVO();
        member.setUserId("owner");
        when(nodes.tree("ws")).thenReturn(List.of(domain, category));
        when(workspaces.queryWorkspaceMembers("ws", "actor")).thenReturn(List.of(member));
        when(users.queryByIds(List.of("owner"))).thenReturn(List.of(owner));
        var tree = service.tree("ws", "actor");
        var roots = tree.categories();
        assertTrue(tree.datamarts().isEmpty());
        assertEquals("domain", roots.getFirst().getChildren().getFirst().getId());
        assertTrue(roots.getFirst().isOwnerActive());
        assertThrows(UnsupportedOperationException.class, () -> roots.getFirst().getChildren().clear());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(roots);
        assertFalse(json.contains("test-only-private-value"));
        assertFalse(json.contains("workspaceId"));
    }

    @Test void requiresAnActiveWorkspaceOwner() {
        when(workspaces.isActiveMember("ws", "owner")).thenReturn(false);
        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", NodeKind.CATEGORY, null, input("orders")));
        assertEquals(ModelingErrorCode.INVALID_OWNER, error.getErrorCode());
        verify(nodes, never()).add(any());
    }

    @Test void preservesUniqueConflictCauseWithoutParsingItsMessage() {
        var cause = new DuplicateKeyException("opaque internal database diagnostic");
        doThrow(cause).when(nodes).add(any());
        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", NodeKind.CATEGORY, null, input("orders")));
        assertEquals(ModelingErrorCode.DUPLICATE_CODE, error.getErrorCode());
        assertSame(cause, error.getCause());
        assertFalse(error.getMessage().contains("diagnostic"));
    }

    @Test void authorizesTheExcludedNodeBeforeGlobalAvailabilityLookup() {
        when(nodes.find("ws", NodeKind.CATEGORY, "other")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.available("ws", "actor", "orders", NodeKind.CATEGORY, "other"));
        verify(nodes, never()).occupied(anyString(), any(), any());
    }

    @Test void rejectsPartialExclusionAndDoesNotDiscloseGlobalOwner() {
        assertThrows(BusinessException.class, () -> service.available("ws", "actor", "orders", NodeKind.DOMAIN, null));
        when(nodes.occupied("orders", null, null)).thenReturn(true);
        assertFalse(service.available("ws", "actor", "orders", null, null));
    }

    @Test void locksAncestorsBeforeUpdatingTheProcess() {
        var category = node(NodeKind.CATEGORY, "category", null);
        var domain = node(NodeKind.DOMAIN, "domain", "category");
        var process = node(NodeKind.PROCESS, "process", "domain");
        for (var node : List.of(category, domain, process)) {
            when(nodes.find("ws", node.getKind(), node.getId())).thenReturn(Optional.of(node));
            when(nodes.lock("ws", node.getKind(), node.getId())).thenReturn(Optional.of(node));
        }
        when(nodes.update(process, 7, true)).thenReturn(true);
        var updated = service.update("ws", "actor", NodeKind.PROCESS, "process", update("orders", 7));
        assertEquals(8, updated.revision());
        var order = inOrder(nodes);
        order.verify(nodes).lock("ws", NodeKind.CATEGORY, "category");
        order.verify(nodes).lock("ws", NodeKind.DOMAIN, "domain");
        order.verify(nodes).lock("ws", NodeKind.PROCESS, "process");
        order.verify(nodes).update(process, 7, true);
        assertEquals("domain", process.getParentId());
    }

    @Test void blocksDeletionWhileAChildExists() {
        var category = node(NodeKind.CATEGORY, "category", null);
        when(nodes.find("ws", NodeKind.CATEGORY, "category")).thenReturn(Optional.of(category));
        when(nodes.lock("ws", NodeKind.CATEGORY, "category")).thenReturn(Optional.of(category));
        when(nodes.hasChildren("ws", NodeKind.CATEGORY, "category")).thenReturn(true);
        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", NodeKind.CATEGORY, "category", 7));
        assertEquals(ModelingErrorCode.HAS_DEPENDENCIES, error.getErrorCode());
        verify(nodes, never()).delete(any(), any(), any(), anyLong());
    }

    @Test void retainsTheReservationWhenTheCodeIsUnchanged() {
        var category = node(NodeKind.CATEGORY, "category", null);
        category.setCode("orders");
        when(nodes.find("ws", NodeKind.CATEGORY, "category")).thenReturn(Optional.of(category));
        when(nodes.lock("ws", NodeKind.CATEGORY, "category")).thenReturn(Optional.of(category));
        when(nodes.update(category, 7, false)).thenReturn(true);
        assertEquals(8, service.update("ws", "actor", NodeKind.CATEGORY, "category", update("orders", 7)).revision());
        verify(nodes).update(category, 7, false);
    }

    @Test void rejectsStaleRevisionsBeforeWriting() {
        var category = node(NodeKind.CATEGORY, "category", null);
        when(nodes.find("ws", NodeKind.CATEGORY, "category")).thenReturn(Optional.of(category));
        when(nodes.lock("ws", NodeKind.CATEGORY, "category")).thenReturn(Optional.of(category));
        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", NodeKind.CATEGORY, "category", update("orders", 6)));
        assertEquals(ModelingErrorCode.REVISION_CONFLICT, error.getErrorCode());
        verify(nodes, never()).update(any(), anyLong(), anyBoolean());
    }

    @Test void reportsTheModelsHangingBelowAProcess() {
        var process = node(NodeKind.PROCESS, "process", "domain");
        when(nodes.find("ws", NodeKind.PROCESS, "process")).thenReturn(Optional.of(process));
        when(models.countByProcess("ws", "process")).thenReturn(2L);
        when(models.pageByProcess("ws", "process", 20, 20))
                .thenReturn(List.of(model("model-1", "订单明细", "dwd_trade_order_detail"),
                        model("model-2", "支付明细", "dwd_trade_payment_detail")));
        var result = service.associatedModels("ws", "actor", "process", 2, 20);
        assertEquals(AssociatedModelsVO.AVAILABLE, result.availability());
        assertEquals(2L, result.page().getPagination().getTotal());
        assertEquals(2L, result.page().getPagination().getPageNo());
        assertEquals(1L, result.page().getPagination().getPages());
        assertEquals(List.of("订单明细", "支付明细"), result.page().getBizData().stream()
                .map(LogicalModelSummaryVO::name)
                .toList());
        assertEquals(List.of("dwd_trade_order_detail", "dwd_trade_payment_detail"), result.page().getBizData().stream()
                .map(LogicalModelSummaryVO::code)
                .toList());
    }

    @Test void skipsTheRowQueryWhenAProcessHasNoModels() {
        var process = node(NodeKind.PROCESS, "process", "domain");
        when(nodes.find("ws", NodeKind.PROCESS, "process")).thenReturn(Optional.of(process));
        when(models.countByProcess("ws", "process")).thenReturn(0L);
        var result = service.associatedModels("ws", "actor", "process", 1, 20);
        assertTrue(result.page().getBizData().isEmpty());
        assertEquals(0L, result.page().getPagination().getTotal());
        verify(models, never()).pageByProcess(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test void rejectsAnUnknownProcessBeforeCountingModels() {
        when(nodes.find("ws", NodeKind.PROCESS, "missing")).thenReturn(Optional.empty());
        var error = assertThrows(BusinessException.class,
                () -> service.associatedModels("ws", "actor", "missing", 1, 20));
        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
        verifyNoInteractions(models);
    }

    @Test void createsADatamartRootWithAnEnglishName() {
        var captor = ArgumentCaptor.forClass(PlanningNodeEntity.class);

        var saved = service.createDatamart("ws", "actor", datamart("operating", "Operating Mart"));

        verify(nodes).add(captor.capture());
        var row = captor.getValue();
        assertEquals(NodeKind.DATASET, saved.kind());
        assertEquals(NodeKind.DATASET, row.getKind());
        assertNull(row.getParentId());
        assertEquals("Operating Mart", row.getEnName());
        assertEquals("owner", row.getOwnerId());
    }

    @Test void createsASubjectUnderItsDatamartWithoutAnOwner() {
        var datamart = node(NodeKind.DATASET, "datamart", null);
        when(nodes.find("ws", NodeKind.DATASET, "datamart")).thenReturn(Optional.of(datamart));
        when(nodes.lock("ws", NodeKind.DATASET, "datamart")).thenReturn(Optional.of(datamart));
        var captor = ArgumentCaptor.forClass(PlanningNodeEntity.class);

        var saved = service.createSubject("ws", "actor", "datamart", subject("realtime"));

        verify(nodes).add(captor.capture());
        var row = captor.getValue();
        assertEquals(NodeKind.SUBJECT, saved.kind());
        assertEquals("datamart", row.getParentId());
        assertEquals("Realtime Report", row.getEnName());
        assertNull(row.getOwnerId());
    }

    @Test void requiresAnEnglishNameForApplicationLayerNodes() {
        var datamart = node(NodeKind.DATASET, "datamart", null);
        when(nodes.find("ws", NodeKind.DATASET, "datamart")).thenReturn(Optional.of(datamart));
        when(nodes.lock("ws", NodeKind.DATASET, "datamart")).thenReturn(Optional.of(datamart));
        var blank = subject("realtime");
        blank.setEnName("  ");

        var error = assertThrows(BusinessException.class,
                () -> service.createSubject("ws", "actor", "datamart", blank));

        assertEquals(CommonErrorCode.PARAM_NOT_VALID, error.getErrorCode());
        verify(nodes, never()).add(any());
    }

    @Test void refusesToDeleteADatamartThatStillHasSubjects() {
        var datamart = node(NodeKind.DATASET, "datamart", null);
        when(nodes.find("ws", NodeKind.DATASET, "datamart")).thenReturn(Optional.of(datamart));
        when(nodes.lock("ws", NodeKind.DATASET, "datamart")).thenReturn(Optional.of(datamart));
        when(nodes.hasChildren("ws", NodeKind.DATASET, "datamart")).thenReturn(true);

        var error = assertThrows(BusinessException.class,
                () -> service.delete("ws", "actor", NodeKind.DATASET, "datamart", 7));

        assertEquals(ModelingErrorCode.HAS_DEPENDENCIES, error.getErrorCode());
        verify(nodes, never()).delete(any(), any(), any(), anyLong());
    }

    @Test void splitsCategoryAndDatamartRootsIntoSeparateTrees() {
        var category = node(NodeKind.CATEGORY, "category", null);
        var domain = node(NodeKind.DOMAIN, "domain", "category");
        var datamart = node(NodeKind.DATASET, "datamart", null);
        var subject = node(NodeKind.SUBJECT, "subject", "datamart");
        when(nodes.tree("ws")).thenReturn(List.of(subject, domain, datamart, category));
        when(workspaces.queryWorkspaceMembers("ws", "actor")).thenReturn(List.of());

        var tree = service.tree("ws", "actor");

        assertEquals(1, tree.categories().size());
        assertEquals("domain", tree.categories().getFirst().getChildren().getFirst().getId());
        assertEquals(1, tree.datamarts().size());
        assertEquals("subject", tree.datamarts().getFirst().getChildren().getFirst().getId());
    }

    static NodeInputDTO input(String code) {
        var input = new NodeInputDTO();
        input.setName("订单");
        input.setCode(code);
        input.setOwnerId("owner");
        return input;
    }

    static DatamartInputDTO datamart(String code, String enName) {
        var input = new DatamartInputDTO();
        input.setName("运营集市");
        input.setEnName(enName);
        input.setCode(code);
        input.setOwnerId("owner");
        return input;
    }

    static SubjectInputDTO subject(String code) {
        var input = new SubjectInputDTO();
        input.setName("实时战报");
        input.setEnName("Realtime Report");
        input.setCode(code);
        return input;
    }

    static UpdateNodeDTO update(String code, long revision) {
        var input = new UpdateNodeDTO();
        input.setName("订单");
        input.setCode(code);
        input.setOwnerId("owner");
        input.setRevision(revision);
        return input;
    }

    static ModelEntity model(String id, String cnName, String name) {
        var row = new ModelEntity();
        row.setId(id);
        row.setCnName(cnName);
        row.setName(name);
        return row;
    }

    static PlanningNodeEntity node(NodeKind kind, String id, String parent) {
        var node = new PlanningNodeEntity();
        node.setWorkspaceId("ws");
        node.setId(id);
        node.setKind(kind);
        node.setParentId(parent);
        node.setRevision(7);
        return node;
    }
}
