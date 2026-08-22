package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.business.dataset.controller.v1.DatasetController;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;

/** Lightweight architecture checks protecting the boundaries documented in yak-framework. */
class DatasetArchitectureTest {

  @Test
  void controllerCannotDependOnPersistenceInfrastructure() {
    for (Field field : DatasetController.class.getDeclaredFields()) {
      assertNoPersistence(field.getGenericType());
    }
  }

  @Test
  void serviceCannotDependOnDaoMapperPoOrJdbc() {
    for (Field field : DatasetService.class.getDeclaredFields()) {
      String type = field.getGenericType().getTypeName();
      assertFalse(type.contains(".dao."), type);
      assertFalse(type.contains(".dao.model."), type);
      assertFalse(type.contains(".dao.mapper."), type);
      assertFalse(type.contains("JdbcTemplate"), type);
      assertFalse(type.contains("com.baomidou.mybatisplus"), type);
      assertFalse(type.contains("ObjectMapper"), type);
    }
  }

  @Test
  void repositoryContractCannotExposeHttpPoMybatisOrServiceTypes() {
    for (Method method : DatasetRepository.class.getDeclaredMethods()) {
      assertRepositoryType(method.getGenericReturnType());
      for (Type parameter : method.getGenericParameterTypes()) {
        assertRepositoryType(parameter);
      }
    }
  }

  @Test
  void daoContractCannotExposeHttpTypes() {
    for (Method method : DatasetDao.class.getDeclaredMethods()) {
      assertNoHttp(method.getGenericReturnType());
      for (Type parameter : method.getGenericParameterTypes()) {
        assertNoHttp(parameter);
      }
    }
  }

  private static void assertNoPersistence(Type type) {
    String name = type.getTypeName();
    assertFalse(name.contains(".repository."), name);
    assertFalse(name.contains(".dao."), name);
    assertFalse(name.contains("JdbcTemplate"), name);
    assertFalse(name.contains("com.baomidou.mybatisplus"), name);
  }

  private static void assertRepositoryType(Type type) {
    String name = type.getTypeName();
    assertNoHttp(type);
    assertFalse(name.contains(".dao."), name);
    assertFalse(name.contains("com.baomidou.mybatisplus"), name);
    assertFalse(name.contains("DatasetService"), name);
  }

  private static void assertNoHttp(Type type) {
    String name = type.getTypeName();
    assertFalse(name.contains(".controller."), name);
    assertFalse(name.contains(".dto."), name);
    assertFalse(name.contains(".vo."), name);
  }
}
