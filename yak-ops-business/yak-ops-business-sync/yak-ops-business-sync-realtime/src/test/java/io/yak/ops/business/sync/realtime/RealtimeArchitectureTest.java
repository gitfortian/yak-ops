package io.yak.ops.business.sync.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.realtime.controller.v1.ComputeEnvironmentController;
import io.yak.ops.business.sync.realtime.controller.v1.RealtimeJobController;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobListQuery;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import io.yak.ops.business.sync.realtime.service.ComputeEnvironmentService;
import io.yak.ops.business.sync.realtime.service.RealtimeJobLifecycleCoordinator;
import io.yak.ops.business.sync.realtime.service.RealtimeJobQueryService;
import io.yak.ops.business.sync.realtime.service.RealtimeJobService;
import io.yak.ops.business.sync.realtime.service.RealtimeObservabilityService;
import io.yak.ops.business.sync.realtime.service.RealtimeRuntimeResolver;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class RealtimeArchitectureTest {

  @Test
  void controllersDependOnApplicationBoundariesInsteadOfPersistenceOrEnginePorts() {
    assertFieldsAvoid(RealtimeJobController.class, ".repository.", ".dao.", "RealtimeEngineGateway", "JdbcTemplate");
    assertFieldsAvoid(ComputeEnvironmentController.class, ".repository.", ".dao.", "RealtimeEngineGateway", "JdbcTemplate");
  }

  @Test
  void servicesDoNotDependOnDaoMapperPoOrJdbcTemplate() {
    for (Class<?> type : new Class<?>[] {
        RealtimeJobService.class,
        RealtimeJobLifecycleCoordinator.class,
        RealtimeJobQueryService.class,
        RealtimeObservabilityService.class,
        RealtimeRuntimeResolver.class,
        ComputeEnvironmentService.class
    }) {
      assertFieldsAvoid(type, ".dao.", ".dao.mapper.", ".dao.model.", "JdbcTemplate");
    }
  }

  @Test
  void repositoryContractsDoNotExposeDaoOrControllerTypes() {
    for (Class<?> repository : new Class<?>[] {
        RealtimeJobStore.class,
        RealtimeJobListQuery.class,
        RealtimeRuntimeIdentityStore.class,
        ComputeEnvironmentStore.class
    }) {
      for (Method method : repository.getDeclaredMethods()) {
        assertTypeBoundary(method.getReturnType());
        for (Class<?> parameterType : method.getParameterTypes()) assertTypeBoundary(parameterType);
      }
    }
  }

  private static void assertFieldsAvoid(Class<?> type, String... forbidden) {
    for (Field field : type.getDeclaredFields()) {
      String name = field.getType().getName();
      for (String value : forbidden) {
        assertThat(name)
            .as("%s.%s must not depend on %s", type.getSimpleName(), field.getName(), value)
            .doesNotContain(value);
      }
    }
  }

  private static void assertTypeBoundary(Class<?> type) {
    String name = type.getName();
    assertThat(name).doesNotContain(".dao.");
    assertThat(name).doesNotContain(".controller.");
    assertThat(name).doesNotContain("JdbcTemplate");
  }
}
