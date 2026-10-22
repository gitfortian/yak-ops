package io.yak.ops.business.task.definition.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Covers version-fenced publication after external resource validation. */
class PublicationServiceTest {
    private final PublicationServiceImpl service = new PublicationServiceImpl();
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final InstanceRepository instances = mock(InstanceRepository.class);

    PublicationServiceTest() {
        try {
            Field field = PublicationServiceImpl.class.getDeclaredField("definitions");
            field.setAccessible(true);
            field.set(service, definitions);
            Field instanceField = PublicationServiceImpl.class.getDeclaredField("instances");
            instanceField.setAccessible(true);
            instanceField.set(service, instances);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void publishesOnlyTheExpectedDefinitionVersion() {
        WorkspaceContext.bind("ws");
        DefinitionEntity definition = new DefinitionEntity();
        definition.setCurrentVersion(2);
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        when(definitions.queryByIdForUpdate("ws", "def")).thenReturn(Optional.of(definition));
        when(definitions.update(eq("ws"), any())).thenReturn(definition);

        DefinitionEntity published = service.publish("def", 2, "publisher");

        assertEquals(DefinitionStatus.PUBLISHED, published.getStatus());
        verify(definitions).update("ws", definition);
    }

    @Test
    void refusesPublishWhenAnInstanceIsNonterminal() {
        WorkspaceContext.bind("ws");
        DefinitionEntity definition = new DefinitionEntity();
        definition.setCurrentVersion(2);
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        when(definitions.queryByIdForUpdate("ws", "def")).thenReturn(Optional.of(definition));
        when(instances.latest("ws", List.of("def"), true)).thenReturn(List.of(new InstanceEntity()));

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.publish("def", 2, "publisher"));

        assertEquals(CommonErrorCode.TASK_INSTANCE_ACTIVE, error.getErrorCode());
        assertEquals(DefinitionStatus.UNPUBLISHED, definition.getStatus());
        verify(definitions, never()).update(eq("ws"), any());
    }

    @Test
    void refusesChangedVersionWithoutUpdatingDefinition() {
        WorkspaceContext.bind("ws");
        DefinitionEntity definition = new DefinitionEntity();
        definition.setCurrentVersion(3);
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        when(definitions.queryByIdForUpdate("ws", "def")).thenReturn(Optional.of(definition));

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.publish("def", 2, "publisher"));

        assertEquals(CommonErrorCode.RESOURCE_DUPLICATION, error.getErrorCode());
        verify(definitions, never()).update(eq("ws"), any());
    }
}
