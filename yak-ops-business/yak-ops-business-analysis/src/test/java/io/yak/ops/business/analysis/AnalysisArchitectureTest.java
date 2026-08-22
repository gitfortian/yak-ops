package io.yak.ops.business.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.analysis.controller.v1.AnalysisController;
import io.yak.ops.business.analysis.repository.AnalysisRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AnalysisArchitectureTest {

  @Test
  void controllerDoesNotDependOnPersistenceLayers() {
    for (Field field : AnalysisController.class.getDeclaredFields()) {
      String name = field.getType().getName();
      assertFalse(name.contains(".repository."));
      assertFalse(name.contains(".dao."));
      assertFalse(name.contains("JdbcTemplate"));
    }
  }

  @Test
  void serviceDoesNotDependOnDaoMapperPoOrJsonInfrastructure() {
    for (Field field : AnalysisService.class.getDeclaredFields()) {
      String name = field.getType().getName();
      assertFalse(name.contains(".dao."));
      assertFalse(name.contains(".dao.mapper."));
      assertFalse(name.contains(".dao.model."));
      assertFalse(name.contains("JdbcTemplate"));
      assertFalse(name.contains("ObjectMapper"));
    }
  }

  @Test
  void repositoryContractOnlyExposesDomainTypes() {
    assertTrue(AnalysisRepository.class.isInterface());
    for (Method method : AnalysisRepository.class.getDeclaredMethods()) {
      assertBoundaryType(method.getReturnType());
      Arrays.stream(method.getParameterTypes()).forEach(this::assertBoundaryType);
    }
  }

  private void assertBoundaryType(Class<?> type) {
    String name = type.getName();
    assertFalse(name.contains(".controller."));
    assertFalse(name.contains(".dao."));
    assertFalse(name.contains("com.baomidou.mybatisplus"));
    assertFalse(name.contains("JdbcTemplate"));
  }
}
