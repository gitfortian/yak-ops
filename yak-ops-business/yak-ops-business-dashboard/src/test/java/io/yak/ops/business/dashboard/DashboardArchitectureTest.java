package io.yak.ops.business.dashboard;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.business.dashboard.controller.v1.DashboardController;
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.service.DashboardService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Protects the Dashboard layering boundaries documented by yak-framework. */
class DashboardArchitectureTest {

  @Test
  void controllerOnlyDependsOnApplicationBoundaryAndViewMappers() {
    for (Field field : DashboardController.class.getDeclaredFields()) {
      String type = field.getType().getName();
      assertFalse(type.contains(".repository."), type);
      assertFalse(type.contains(".dao."), type);
      assertFalse(type.contains(".mapper.DashboardMapper"), type);
      assertFalse(type.contains("JdbcTemplate"), type);
    }
  }

  @Test
  void serviceDoesNotDependOnPersistenceImplementationTypes() {
    for (Field field : DashboardService.class.getDeclaredFields()) {
      String type = field.getType().getName();
      assertFalse(type.contains(".dao."), type);
      assertFalse(type.contains(".dao.mapper."), type);
      assertFalse(type.contains(".dao.model."), type);
      assertFalse(type.contains("JdbcTemplate"), type);
    }
  }

  @Test
  void repositoryContractOnlyUsesDomainAndJdkTypes() {
    for (Method method : DashboardRepository.class.getDeclaredMethods()) {
      String signature = method.toGenericString();
      assertFalse(signature.contains(".dao."), signature);
      assertFalse(signature.contains("com.baomidou.mybatisplus"), signature);
      assertFalse(signature.contains("JdbcTemplate"), signature);
      assertFalse(signature.contains("controller.v1"), signature);
    }
  }
}
