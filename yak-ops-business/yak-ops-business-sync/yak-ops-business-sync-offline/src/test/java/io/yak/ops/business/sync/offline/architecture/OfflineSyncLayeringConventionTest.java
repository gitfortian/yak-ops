package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.framework.common.PageData;
import io.yak.ops.business.sync.offline.backfill.OfflineBackfillDispatcher;
import io.yak.ops.business.sync.offline.backfill.OfflineBackfillService;
import io.yak.ops.business.sync.offline.controller.OfflineBackfillController;
import io.yak.ops.business.sync.offline.controller.OfflineControlPlaneController;
import io.yak.ops.business.sync.offline.controller.OfflineJobDefinitionController;
import io.yak.ops.business.sync.offline.controller.OfflineJobExecutionController;
import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.dao.OfflineExecutionEventDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionQuery;
import io.yak.ops.business.sync.offline.execution.OfflineJobExecutionService;
import io.yak.ops.business.sync.offline.reconcile.OfflineExecutionReconciler;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionControlRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionIdempotencyRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleHandler;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OfflineSyncLayeringConventionTest {

  private static final Set<String> EXECUTION_INTERNAL_IMPORTS = Set.of(
      "io.yak.ops.business.sync.offline.execution.OfflineExecutionOrchestrator",
      "io.yak.ops.business.sync.offline.execution.OfflineExecutionClaimService",
      "io.yak.ops.business.sync.offline.execution.OfflineBatchRuntimeService",
      "io.yak.ops.business.sync.offline.execution.query.",
      "io.yak.ops.business.sync.offline.execution.adapter.");

  private static final Set<String> EXECUTION_INTERNAL_FACADE_EXCEPTIONS = Set.of(
      "definition/OfflineJobDefinitionService.java",
      "backfill/OfflineBackfillService.java");

  @Test
  void controllersDependOnlyOnStableApplicationFacades() {
    Set<Class<?>> facades = Set.of(
        OfflineJobDefinitionService.class,
        OfflineJobExecutionService.class,
        OfflineBackfillService.class);

    for (Class<?> type : List.of(
        OfflineJobDefinitionController.class,
        OfflineJobExecutionController.class,
        OfflineControlPlaneController.class,
        OfflineBackfillController.class)) {
      for (Field field : type.getDeclaredFields()) {
        assertThat(field.getType())
            .as("%s.%s must depend on a stable Application Facade", type.getSimpleName(), field.getName())
            .isIn(facades);
      }
    }
  }

  @Test
  void backgroundEntrypointsReachExecutionThroughFacade() {
    for (Class<?> type : List.of(
        OfflineScheduleHandler.class,
        OfflineBackfillDispatcher.class,
        OfflineExecutionReconciler.class)) {
      List<Class<?>> dependencies = Arrays.stream(type.getDeclaredFields())
          .map(Field::getType)
          .toList();

      assertThat(dependencies)
          .as("%s must enter execution through OfflineJobExecutionService", type.getSimpleName())
          .contains(OfflineJobExecutionService.class);

      for (Class<?> dependency : dependencies) {
        String name = dependency.getName();
        assertThat(name)
            .as("%s must not depend on an execution internal component", type.getSimpleName())
            .doesNotContain("OfflineExecutionOrchestrator")
            .doesNotContain("OfflineExecutionClaimService")
            .doesNotContain("OfflineBatchRuntimeService")
            .doesNotContain(".execution.query.")
            .doesNotContain(".execution.adapter.");
      }
    }
  }

  @Test
  void nonFacadeProductionCodeDoesNotImportExecutionInternals() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (relative.startsWith("execution/")
            || EXECUTION_INTERNAL_FACADE_EXCEPTIONS.contains(relative)) {
          continue;
        }

        String source = Files.readString(file);
        for (String forbidden : EXECUTION_INTERNAL_IMPORTS) {
          assertThat(source)
              .as("%s must not import execution internal API %s", relative, forbidden)
              .doesNotContain("import " + forbidden);
        }
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
  void attemptPersistenceDoesNotExposeLegacyTaskRuntimeOrRetroactiveBinding() {
    List<String> repositoryMethods = Arrays.stream(OfflineJobExecutionRepository.class.getMethods())
        .map(Method::getName)
        .toList();
    List<String> daoMethods = Arrays.stream(OfflineJobExecutionDao.class.getMethods())
        .map(Method::getName)
        .toList();

    assertThat(repositoryMethods).doesNotContain("hasActiveExecution", "bindBatch");
    assertThat(daoMethods).doesNotContain("hasActiveExecution", "bindBatch");
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

  private Path productionRoot() {
    Path moduleRoot = Path.of("src/main/java/io/yak/ops/business/sync/offline");
    if (Files.isDirectory(moduleRoot)) return moduleRoot;

    Path repositoryRoot = Path.of(
        "yak-ops-business",
        "yak-ops-business-sync",
        "yak-ops-business-sync-offline",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "business",
        "sync",
        "offline");
    assertThat(Files.isDirectory(repositoryRoot))
        .as("offline-sync production source root must be available to architecture test")
        .isTrue();
    return repositoryRoot;
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
