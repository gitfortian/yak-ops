package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import io.yak.ops.business.job.task.SyncTaskRunner;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.workflow.persistence.WorkflowDefinitionPersistence;
import io.yak.ops.business.workflow.persistence.WorkflowRuntimePersistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class WorkflowPersistenceWiringTest {

  @Test
  void runtimeShouldFailFastWhenDatabaseIsEnabledButRepositoriesAreMissing() {
    DefaultListableBeanFactory beans = new DefaultListableBeanFactory();

    assertThatThrownBy(() -> new WorkflowRuntimeService(
        new WorkflowEventStreamService(),
        mock(TaskRegistry.class),
        mock(SyncTaskRunner.class),
        provider(beans, WorkflowDefinitionRepository.class),
        provider(beans, ExecutionRepository.class),
        provider(beans, WorkflowRuntimePersistence.class),
        true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WorkflowDefinitionRepository");
  }

  @Test
  void definitionServiceShouldFailFastWhenDatabaseIsEnabledButCatalogIsMissing() {
    DefaultListableBeanFactory beans = new DefaultListableBeanFactory();

    assertThatThrownBy(() -> new WorkflowDefinitionService(
        mock(WorkflowRuntimeService.class),
        mock(TaskRegistry.class),
        provider(beans, WorkflowDefinitionPersistence.class),
        true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WorkflowDefinitionPersistence");
  }

  @Test
  void runtimeShouldAllowExplicitDatabaseDisabledMode() {
    DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
    WorkflowRuntimeService runtime = new WorkflowRuntimeService(
        new WorkflowEventStreamService(),
        mock(TaskRegistry.class),
        mock(SyncTaskRunner.class),
        provider(beans, WorkflowDefinitionRepository.class),
        provider(beans, ExecutionRepository.class),
        provider(beans, WorkflowRuntimePersistence.class),
        false);
    try {
      // Construction itself proves the explicit in-memory development/test path remains available.
    } finally {
      runtime.shutdown();
    }
  }

  private static <T> ObjectProvider<T> provider(
      DefaultListableBeanFactory beans,
      Class<T> type) {
    return beans.getBeanProvider(type);
  }
}
