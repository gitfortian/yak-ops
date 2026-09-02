package io.yak.ops.business.workflow.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

class WorkflowExecutionAuditBridgeSpringTest {

  @Test
  void componentScanSelectsAutowiredProviderConstructor() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(TestConfiguration.class)) {
      assertThat(context.getBean(WorkflowExecutionAuditBridge.class)).isNotNull();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ComponentScan(
      basePackageClasses = WorkflowExecutionAuditBridge.class,
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = WorkflowExecutionAuditBridge.class))
  static class TestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
