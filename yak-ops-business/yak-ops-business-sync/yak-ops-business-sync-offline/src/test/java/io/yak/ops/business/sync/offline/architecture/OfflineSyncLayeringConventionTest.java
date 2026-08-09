package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.business.sync.offline.dao.OfflineExecutionEventDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionControlRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionIdempotencyRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineSyncLayeringConventionTest {

  @Test
  void repositoriesExposeOnlyDomainContracts() {
    for (Class<?> type : List.of(
        OfflineJobDefinitionRepository.class,
        OfflineJobExecutionRepository.class,
        OfflineExecutionEventRepository.class,
        OfflineScheduleRepository.class,
        OfflineExecutionControlRepository.class,
        OfflineExecutionIdempotencyRepository.class)) {
      assertMethodsAvoid(type, ".bean.dto.", ".bean.vo.", ".bean.po.");
    }
  }

  @Test
  void daosDoNotDependOnTransportModels() {
    for (Class<?> type : List.of(
        OfflineJobDefinitionDao.class,
        OfflineJobExecutionDao.class,
        OfflineExecutionEventDao.class)) {
      assertMethodsAvoid(type, ".bean.dto.", ".bean.vo.");
    }
  }

  @Test
  void phaseOnePersistenceRemainsThreeTables() {
    assertTable(OfflineJobDefinitionPO.class, "yak_offline_job_definition");
    assertTable(OfflineJobExecutionPO.class, "yak_offline_job_execution");
    assertTable(OfflineExecutionEventPO.class, "yak_offline_execution_event");
  }

  private void assertMethodsAvoid(Class<?> owner, String... forbidden) {
    for (Method method : owner.getMethods()) {
      assertTypeAvoids(owner, method, method.getGenericReturnType(), forbidden);
      for (Type parameter : method.getGenericParameterTypes()) {
        assertTypeAvoids(owner, method, parameter, forbidden);
      }
    }
  }

  private void assertTypeAvoids(Class<?> owner, Method method, Type type, String... forbidden) {
    String signature = typeName(type);
    for (String packagePart : forbidden) {
      assertThat(signature)
          .as("%s.%s must not expose %s", owner.getSimpleName(), method.getName(), packagePart)
          .doesNotContain(packagePart);
    }
  }

  private String typeName(Type type) {
    if (type instanceof ParameterizedType parameterized) {
      StringBuilder value = new StringBuilder(parameterized.getRawType().getTypeName());
      for (Type argument : parameterized.getActualTypeArguments()) {
        value.append('<').append(typeName(argument)).append('>');
      }
      return value.toString();
    }
    return type.getTypeName();
  }

  private void assertTable(Class<?> poType, String tableName) {
    TableName mapping = poType.getAnnotation(TableName.class);
    assertThat(mapping).as(poType.getSimpleName() + " must declare @TableName").isNotNull();
    assertThat(mapping.value()).isEqualTo(tableName);
  }
}
