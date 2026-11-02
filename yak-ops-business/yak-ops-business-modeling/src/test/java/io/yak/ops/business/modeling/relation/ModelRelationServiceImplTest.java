package io.yak.ops.business.modeling.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.relation.impl.ModelRelationServiceImpl;
import io.yak.ops.common.bean.dto.modeling.CreateModelRelationDTO;
import io.yak.ops.common.bean.dto.modeling.RelationKeyPairDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelRelationDTO;
import io.yak.ops.common.bean.vo.modeling.ModelRelationVO;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationColumnEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationEntity;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.ModelingRelationRepository;
import io.yak.ops.workspace.WorkspaceService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Validation matrix of the semantic relation surface, keyed on the proposal's decisions. */
class ModelRelationServiceImplTest {
    private static final String WS = "ws-1";
    private static final String ACTOR = "u-1";
    private static final String FROM_ID = "m-fact";
    private static final String TO_ID = "m-dim";

    private ModelRepository models;
    private ModelingRelationRepository relations;
    private ModelRelationServiceImpl service;

    @BeforeEach
    void setUp() {
        models = Mockito.mock(ModelRepository.class);
        relations = Mockito.mock(ModelingRelationRepository.class);
        WorkspaceService workspaces = Mockito.mock(WorkspaceService.class);
        Mockito.when(workspaces.isActiveMember(anyString(), anyString())).thenReturn(true);
        service = new ModelRelationServiceImpl();
        inject(service, "models", models);
        inject(service, "relations", relations);
        inject(service, "workspaces", workspaces);
    }

    private static void inject(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ModelEntity model(String id, String type, String name) {
        ModelEntity row = new ModelEntity();
        row.setId(id);
        row.setWorkspaceId("ws-1");
        row.setType(type);
        row.setName(name);
        row.setCnName(name);
        row.setRevision(3L);
        return row;
    }

    private static ModelFieldEntity field(String code, String standardId) {
        ModelFieldEntity row = new ModelFieldEntity();
        row.setEnCode(code);
        row.setCnName(code);
        row.setStandardId(standardId);
        return row;
    }

    private void givenFactAndDim() {
        when(models.find(WS, FROM_ID)).thenReturn(Optional.of(model(FROM_ID, "DWD", "dwd_order")));
        when(models.find(WS, TO_ID)).thenReturn(Optional.of(model(TO_ID, "DIM", "dim_customer")));
        when(models.fields(FROM_ID)).thenReturn(List.of(field("customer_id", "std-1")));
        when(models.fields(TO_ID)).thenReturn(List.of(field("customer_id", "std-1")));
    }

    private static CreateModelRelationDTO createDTO() {
        CreateModelRelationDTO dto = new CreateModelRelationDTO();
        dto.setTargetModelId(TO_ID);
        RelationKeyPairDTO pair = new RelationKeyPairDTO();
        pair.setFromFieldCode("customer_id");
        pair.setToFieldCode("customer_id");
        dto.setKeys(List.of(pair));
        return dto;
    }

    @Test
    void createGeneratesNameAndStoresCodePairs() {
        givenFactAndDim();
        when(relations.countName(anyString(), anyString(), any())).thenReturn(0L);
        when(relations.listByModel(anyString(), anyString())).thenReturn(List.of());

        ModelRelationVO vo = service.create(WS, ACTOR, FROM_ID, createDTO());

        assertEquals("dwd_order_to_dim_customer", vo.name());
        assertEquals("READY", vo.status());
        Mockito.verify(relations).add(any(ModelingRelationEntity.class));
        Mockito.verify(relations).addColumn(any(ModelingRelationColumnEntity.class));
    }

    @Test
    void createRejectsDimManySideAndNonDimTarget() {
        when(models.find(WS, FROM_ID)).thenReturn(Optional.of(model(FROM_ID, "DIM", "dim_x")));
        when(models.find(WS, TO_ID)).thenReturn(Optional.of(model(TO_ID, "DIM", "dim_y")));
        BusinessException dimFrom = assertThrows(BusinessException.class, () -> service.create(WS, ACTOR, FROM_ID, createDTO()));
        assertTrue(dimFrom.getMessage().contains(DimensionModelingErrorCode.RELATION_FROM_IS_DIM.getMessage()));

        when(models.find(WS, FROM_ID)).thenReturn(Optional.of(model(FROM_ID, "DWD", "dwd_order")));
        when(models.find(WS, TO_ID)).thenReturn(Optional.of(model(TO_ID, "DWD", "dwd_other")));
        BusinessException badTarget = assertThrows(BusinessException.class, () -> service.create(WS, ACTOR, FROM_ID, createDTO()));
        assertTrue(badTarget.getMessage().contains(DimensionModelingErrorCode.RELATION_TARGET_NOT_DIM.getMessage()));
    }

    @Test
    void createRejectsSelfRelation() {
        when(models.find(WS, FROM_ID)).thenReturn(Optional.of(model(FROM_ID, "DWD", "dwd_order")));
        when(models.find(WS, TO_ID)).thenReturn(Optional.of(model(FROM_ID, "DIM", "dim_x")));
        BusinessException failure = assertThrows(BusinessException.class, () -> service.create(WS, ACTOR, FROM_ID, createDTO()));
        assertTrue(failure.getMessage().contains(DimensionModelingErrorCode.RELATION_SELF.getMessage()));
    }

    @Test
    void createRejectsUnstandardizedKey() {
        givenFactAndDim();
        when(models.fields(FROM_ID)).thenReturn(List.of(field("customer_id", null)));
        BusinessException failure = assertThrows(BusinessException.class, () -> service.create(WS, ACTOR, FROM_ID, createDTO()));
        assertTrue(failure.getMessage().contains(DimensionModelingErrorCode.RELATION_KEY_FIELD_UNSTANDARDIZED.getMessage()));
    }

    @Test
    void createRejectsDuplicateKeySetAgainstSameModelPair() {
        givenFactAndDim();
        when(relations.countName(anyString(), anyString(), any())).thenReturn(0L);
        ModelingRelationEntity existing = new ModelingRelationEntity();
        existing.setId("rel-1");
        existing.setFromModelId(FROM_ID);
        existing.setToModelId(TO_ID);
        when(relations.listByModel(anyString(), anyString())).thenReturn(List.of(existing));
        ModelingRelationColumnEntity pair = new ModelingRelationColumnEntity();
        pair.setFromFieldCode("customer_id");
        pair.setToFieldCode("customer_id");
        when(relations.columns("rel-1")).thenReturn(List.of(pair));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.create(WS, ACTOR, FROM_ID, createDTO()));
        assertTrue(failure.getMessage().contains(DimensionModelingErrorCode.RELATION_DUPLICATE_KEY_SET.getMessage()));
    }

    @Test
    void updateRejectsRevisionConflict() {
        ModelingRelationEntity stored = new ModelingRelationEntity();
        stored.setId("rel-1");
        stored.setRevision(7L);
        when(relations.lock(WS, "rel-1")).thenReturn(Optional.of(stored));
        UpdateModelRelationDTO dto = new UpdateModelRelationDTO();
        dto.setName("order_to_customer");
        dto.setJoinType("LEFT");
        dto.setRevision(6L);
        dto.setKeys(List.of());

        BusinessException failure = assertThrows(BusinessException.class, () -> service.update(WS, ACTOR, "rel-1", dto));
        assertTrue(failure.getMessage().contains(DimensionModelingErrorCode.REVISION_CONFLICT.getMessage()));
    }

    @Test
    void listSurfacesFieldRemovedState() {
        when(models.find(WS, FROM_ID)).thenReturn(Optional.of(model(FROM_ID, "DWD", "dwd_order")));
        when(models.find(WS, TO_ID)).thenReturn(Optional.of(model(TO_ID, "DIM", "dim_customer")));
        when(models.fields(FROM_ID)).thenReturn(List.of(field("order_id", "std-1")));
        when(models.fields(TO_ID)).thenReturn(List.of(field("customer_id", "std-1")));
        ModelingRelationEntity relation = new ModelingRelationEntity();
        relation.setId("rel-1");
        relation.setName("order_to_customer");
        relation.setFromModelId(FROM_ID);
        relation.setToModelId(TO_ID);
        relation.setRevision(1L);
        when(relations.listByModel(WS, FROM_ID)).thenReturn(List.of(relation));
        ModelingRelationColumnEntity pair = new ModelingRelationColumnEntity();
        pair.setOrdinal(0);
        pair.setFromFieldCode("customer_id");
        pair.setToFieldCode("customer_id");
        when(relations.columns("rel-1")).thenReturn(List.of(pair));

        ModelRelationVO vo = service.list(WS, ACTOR, FROM_ID).get(0);

        assertEquals("FIELD_REMOVED", vo.status());
        assertEquals(false, vo.keys().get(0).resolved());
    }
}
