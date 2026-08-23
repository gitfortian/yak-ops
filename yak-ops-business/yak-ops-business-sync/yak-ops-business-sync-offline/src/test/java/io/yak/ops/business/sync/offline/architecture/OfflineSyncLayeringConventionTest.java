package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.framework.common.PageData;
import io.yak.ops.business.sync.offline.controller.OfflineControlPlaneController;
import io.yak.ops.business.sync.offline.controller.OfflineJobDefinitionController;
import io.yak.ops.business.sync.offline.controller.OfflineJobExecutionController;
import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.dao.OfflineExecutionEventDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionQuery;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionControlRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionIdempotencyRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineSyncLayeringConventionTest {

  @Test
  void controllersDependOnServicesInsteadOfPersistenceOrEngineClients() {
    for (Class<?> type : List.of(
        OfflineJobDefinitionController.class,
        OfflineJobExecutionController.class,
        OfflineControlPlaneController.class)) {
      for (Field field : type.getDeclaredFields()) {
        String dependency = field.getType().getName();
        assertThat(dependency)
            .as("%s.%s must not bypass Service", type.getSimpleName(), field.getName())
            .doesNotContain(".repository.")
            .doesNotContain(".dao.")
            .doesNotContain(".engine.");
      }
    }
  }

  @Test
  void repositoriesExposeOnlyDomainContracts() {
    for (Class<?> type : List.of(
        OfflineBatchExecutionRepository.class,
        OfflineJobDefinitionRepository.class,
        OfflineJobExecutionRepository.class,
        OfflineExecutionEventRepository.class,
        OfflineScheduleRepository.class,
        OfflineExecutionControlRepository.class,
        OfflineExecutionIdempotencyRepository.class)) {
      assertMethodsAvoid(
          type,
          ".bean.dto.",
          ".bean.vo.",
          ".bean.po.",
          "com.baomidou.mybatisplus");
    }
  }

  @Test
  void repositoriesUseSharedPageData() throws Exception {
    Method definitions =
        OfflineJobDefinitionRepository.class.getMethod("page", OfflineDefinitionQuery.class);
    Method executions =
        OfflineJobExecutionRepository.class.getMethod("page", OfflineExecutionQuery.class);
    assertThat(((ParameterizedType) definitions.getGenericReturnType()).getRawType())
        .isEqualTo(PageData.class);
    assertThat(((ParameterizedType) executions.getGenericReturnType()).getRawType())
        .isEqualTo(PageData.class);
  }

  @Test
  void daosDoNotDependOnTransportModels() {
    for (Class<?> type : List.of(
        OfflineBatchExecutionDao.class,
        OfflineJobDefinitionDao.class,
        OfflineJobExecutionDao.class,
        OfflineExecutionEventDao.class)) {
      assertMethodsAvoid(type, ".bean.dto.", ".bean.vo.");
    }
  }

  @Test
  void waveOnePersistenceAddsBatchTableWithoutReplacingLegacyTables() throws Exception {
    assertTable(OfflineBatchExecutionPO.class, "yak_offline_batch_execution");
    assertTable(OfflineJobDefinitionPO.class, "yak_offline_job_definition");
    assertTable(OfflineJobExecutionPO.class, "yak_offline_job_execution");
    assertTable(OfflineExecutionEventPO.class, "yak_offline_execution_event");

    Field batchId = OfflineJobExecutionPO.class.getDeclaredField("batchId");
    assertThat(batchId.getType()).isEqualTo(Long.class);
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
