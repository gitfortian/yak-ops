package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.workflow.observability.WorkflowEventStream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class WorkflowRuntimeSpringTest {

  @Test
  void shouldCreateRuntimeServiceThroughSpring() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext()) {
      context.register(WorkflowEventStream.class, WorkflowRuntime.class);
      context.refresh();

      assertThat(context.getBean(WorkflowRuntime.class)).isNotNull();
    }
  }
}
