package io.yak.ops.business.resource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.business.resource.controller.v1.ResourcesController;
import io.yak.ops.business.resource.dao.ResourceDao;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.service.impl.ResourceServiceImpl;
import io.yak.ops.business.resource.storage.StorageOperatorRegistry;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceLayeringConventionTest {

  private static final List<String> HTTP_OR_PERSISTENCE_PACKAGES =
      List.of(
          "io.yak.ops.common.bean.dto.",
          "io.yak.ops.common.bean.vo.",
          "io.yak.ops.common.bean.po.");

  @Test
  void repositoryOnlyExposesDomainContracts() {
    for (Method method : ResourceRepository.class.getDeclaredMethods()) {
      assertThat(containsAny(method.getGenericReturnType(), HTTP_OR_PERSISTENCE_PACKAGES))
          .as("Repository return type: %s", method)
          .isFalse();
      for (Type parameter : method.getGenericParameterTypes()) {
        assertThat(containsAny(parameter, HTTP_OR_PERSISTENCE_PACKAGES))
            .as("Repository parameter: %s", method)
            .isFalse();
      }
    }
  }

  @Test
  void daoDoesNotDependOnHttpContracts() {
    List<String> httpPackages =
        List.of("io.yak.ops.common.bean.dto.", "io.yak.ops.common.bean.vo.");
    for (Method method : ResourceDao.class.getDeclaredMethods()) {
      assertThat(containsAny(method.getGenericReturnType(), httpPackages))
          .as("DAO return type: %s", method)
          .isFalse();
      for (Type parameter : method.getGenericParameterTypes()) {
        assertThat(containsAny(parameter, httpPackages))
            .as("DAO parameter: %s", method)
            .isFalse();
      }
    }
  }

  @Test
  void serviceDoesNotInjectDaoOrPersistenceObjects() {
    for (Field field : ResourceServiceImpl.class.getDeclaredFields()) {
      String type = field.getGenericType().getTypeName();
      assertThat(type).doesNotContain(".resource.dao.");
      assertThat(type).doesNotContain(".bean.po.resource.");
    }
  }

  @Test
  void controllerOnlyEntersResourceBusinessThroughService() {
    for (Field field : ResourcesController.class.getDeclaredFields()) {
      String type = field.getGenericType().getTypeName();
      assertThat(type).doesNotContain(".repository.");
      assertThat(type).doesNotContain(".dao.");
      assertThat(type).doesNotContain(".storage.");
      assertThat(type).doesNotContain("StorageOperator");
    }
  }

  @Test
  void storageRegistryDoesNotExposeHttpViewModels() throws Exception {
    Method list = StorageOperatorRegistry.class.getMethod("list");
    assertThat(list.getGenericReturnType().getTypeName()).doesNotContain(".bean.vo.");
  }

  @Test
  void resourcePersistenceStillUsesTheSingleResourceTable() {
    TableName tableName = ResourcePO.class.getAnnotation(TableName.class);
    assertThat(tableName).isNotNull();
    assertThat(tableName.value()).isEqualTo("yak_ops_resource");
  }

  private boolean containsAny(Type type, List<String> packages) {
    if (type == null) {
      return false;
    }
    if (type instanceof Class<?> clazz) {
      return packages.stream().anyMatch(clazz.getName()::startsWith);
    }
    if (type instanceof ParameterizedType parameterized) {
      if (containsAny(parameterized.getRawType(), packages)) {
        return true;
      }
      for (Type argument : parameterized.getActualTypeArguments()) {
        if (containsAny(argument, packages)) {
          return true;
        }
      }
      return false;
    }
    String value = type.getTypeName();
    return packages.stream().anyMatch(value::contains);
  }
}
