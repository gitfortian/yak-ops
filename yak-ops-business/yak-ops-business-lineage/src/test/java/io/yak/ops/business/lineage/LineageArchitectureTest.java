package io.yak.ops.business.lineage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.yak.ops.business.lineage.controller.v1.LineageController;
import io.yak.ops.business.lineage.dao.LineageDao;
import io.yak.ops.business.lineage.repository.LineageRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Lightweight architecture tests protecting the Yak backend layering boundary. */
class LineageArchitectureTest {

  @Test
  void controllerOnlyDependsOnApplicationAndHttpMappers() {
    for (Field field : LineageController.class.getDeclaredFields()) {
      String type = field.getType().getName();
      assertFalse(type.contains(".repository."), type);
      assertFalse(type.contains(".dao."), type);
      assertFalse(type.contains("JdbcTemplate"), type);
    }
  }

  @Test
  void servicesDoNotDependOnPersistenceImplementationTypes() {
    assertServiceBoundary(LineageService.class);
    assertServiceBoundary(LineageMaintenanceService.class);
  }

  @Test
  void repositoryContractOnlyExposesDomainAndJdkTypes() {
    for (Method method : LineageRepository.class.getDeclaredMethods()) {
      assertPersistenceFree(method.getGenericReturnType());
      Arrays.stream(method.getGenericParameterTypes()).forEach(this::assertPersistenceFree);
    }
  }

  @Test
  void daoContractDoesNotExposeHttpContracts() {
    for (Method method : LineageDao.class.getDeclaredMethods()) {
      String signature = method.toGenericString();
      assertFalse(signature.contains(".controller."), signature);
      assertFalse(signature.contains(".dto."), signature);
      assertFalse(signature.contains(".vo."), signature);
    }
  }

  @Test
  void domainRecordsDoNotCarryHttpSerializationAnnotations() {
    assertNoJsonSerialize(LineageAsset.class);
    assertNoJsonSerialize(LineageRelation.class);
  }

  private void assertServiceBoundary(Class<?> serviceType) {
    for (Field field : serviceType.getDeclaredFields()) {
      String type = field.getType().getName();
      assertFalse(type.contains(".dao."), type);
      assertFalse(type.contains(".dao.model."), type);
      assertFalse(type.contains(".dao.mapper."), type);
      assertFalse(type.contains("com.baomidou.mybatisplus"), type);
      assertFalse(type.contains("JdbcTemplate"), type);
    }
  }

  private void assertPersistenceFree(Type type) {
    String value = type.getTypeName();
    assertFalse(value.contains(".dao."), value);
    assertFalse(value.contains("com.baomidou.mybatisplus"), value);
    assertFalse(value.contains("JdbcTemplate"), value);
    assertFalse(value.contains(".controller."), value);
  }

  private void assertNoJsonSerialize(Class<?> recordType) {
    assertTrue(recordType.isRecord());
    for (RecordComponent component : recordType.getRecordComponents()) {
      assertFalse(component.isAnnotationPresent(JsonSerialize.class), component.getName());
    }
  }
}
