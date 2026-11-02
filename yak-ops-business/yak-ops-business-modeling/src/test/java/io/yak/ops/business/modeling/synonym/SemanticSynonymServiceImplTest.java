package io.yak.ops.business.modeling.synonym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.synonym.impl.SemanticSynonymServiceImpl;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.SemanticSynonymTargetType;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import io.yak.ops.dao.entity.modeling.ModelingSynonymEntity;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.ModelingSynonymRepository;
import io.yak.ops.workspace.WorkspaceService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Storage-key and normalization rules of model-field synonyms. */
class SemanticSynonymServiceImplTest {
    private static final String WS = "ws-1";
    private static final String ACTOR = "u-1";
    private static final String MODEL_ID = "m-1";

    private ModelRepository models;
    private ModelingSynonymRepository synonyms;
    private SemanticSynonymServiceImpl service;

    @BeforeEach
    void setUp() {
        models = Mockito.mock(ModelRepository.class);
        synonyms = Mockito.mock(ModelingSynonymRepository.class);
        WorkspaceService workspaces = Mockito.mock(WorkspaceService.class);
        Mockito.when(workspaces.isActiveMember(anyString(), anyString())).thenReturn(true);
        service = new SemanticSynonymServiceImpl();
        inject("models", models);
        inject("synonyms", synonyms);
        inject("workspaces", workspaces);
    }

    private void inject(String field, Object value) {
        try {
            var f = SemanticSynonymServiceImpl.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(service, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void givenFieldExists() {
        ModelFieldEntity field = new ModelFieldEntity();
        field.setEnCode("customer_id");
        when(models.fields(MODEL_ID)).thenReturn(List.of(field));
    }

    @Test
    void modelFieldSynonymsStoreTheCompositeKey() {
        givenFieldExists();

        service.replaceForModelField(WS, ACTOR, MODEL_ID, "customer_id", List.of("客户编号"));

        ArgumentCaptor<ModelingSynonymEntity> captor = ArgumentCaptor.forClass(ModelingSynonymEntity.class);
        verify(synonyms).deleteByTarget(WS, "MODEL_FIELD", MODEL_ID + ":customer_id");
        verify(synonyms, times(1)).add(captor.capture());
        assertEquals(MODEL_ID + ":customer_id", captor.getValue().getTargetId());
        assertEquals("客户编号", captor.getValue().getTerm());
    }

    @Test
    void rejectsMissingField() {
        when(models.fields(MODEL_ID)).thenReturn(List.of());

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.listForModelField(WS, ACTOR, MODEL_ID, "nope"));
        assertTrue(failure.getMessage().contains(CommonErrorCode.RESOURCE_NOT_EXISTS.getMessage()));
    }

    @Test
    void rejectsOversizedAndDuplicateTerms() {
        givenFieldExists();
        BusinessException oversize = assertThrows(BusinessException.class,
                () -> service.replaceForModelField(WS, ACTOR, MODEL_ID, "customer_id", List.of("x".repeat(65))));
        assertTrue(oversize.getMessage().contains(DimensionModelingErrorCode.SYNONYM_INVALID.getMessage()));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.replaceForModelField(WS, ACTOR, MODEL_ID, "customer_id", List.of("客户", "客户")));
        assertTrue(duplicate.getMessage().contains(DimensionModelingErrorCode.SYNONYM_INVALID.getMessage()));
    }

    @Test
    void standardTargetMustExist() {
        when(models.standard(WS, "std-x")).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.list(WS, ACTOR, SemanticSynonymTargetType.STANDARD_FIELD, "std-x"));
        assertTrue(failure.getMessage().contains(CommonErrorCode.RESOURCE_NOT_EXISTS.getMessage()));
    }

    @Test
    void modelFieldTargetIsRejectedOnGenericSurface() {
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.list(WS, ACTOR, SemanticSynonymTargetType.MODEL_FIELD, "whatever"));
        assertTrue(failure.getMessage().contains(DimensionModelingErrorCode.SYNONYM_TARGET_INVALID.getMessage()));
    }
}
