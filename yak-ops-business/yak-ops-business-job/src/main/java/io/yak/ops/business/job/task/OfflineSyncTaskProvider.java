package io.yak.ops.business.job.task;

import io.yak.framework.common.PageData;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.service.OfflineJobDefinitionService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Adapts workflow-eligible Offline Sync definitions into the generic TaskProvider contract. */
@Component
public class OfflineSyncTaskProvider implements TaskProvider {

  private static final int PAGE_SIZE = 200;
  private static final String RELEASE_STATE_ONLINE = "ONLINE";

  private final ObjectProvider<OfflineJobDefinitionService> definitionServiceProvider;

  public OfflineSyncTaskProvider(
      ObjectProvider<OfflineJobDefinitionService> definitionServiceProvider) {
    this.definitionServiceProvider = definitionServiceProvider;
  }

  @Override
  public List<TaskDefinition> list() {
    return registrations().stream().map(TaskRegistration::definition).toList();
  }

  @Override
  public TaskVersionSnapshot snapshot(String taskId) {
    OfflineJobDefinition current = service().require(parseTaskId(taskId));
    if (!isWorkflowEligible(current)) {
      throw new IllegalArgumentException("离线同步任务当前不可用于工作流：" + taskId);
    }
    return registration(current).snapshot();
  }

  @Override
  public List<TaskRegistration> registrations() {
    OfflineJobDefinitionService service = definitionServiceProvider.getIfAvailable();
    if (service == null) return List.of();

    List<TaskRegistration> registrations = new ArrayList<>();
    int pageNo = 1;
    while (true) {
      PageData<OfflineJobDefinition> page = service.pageDomain(query(pageNo));
      for (OfflineJobDefinition definition : page.records()) {
        if (!isWorkflowEligible(definition)) continue;
        try {
          registrations.add(registration(service.require(definition.getId())));
        } catch (RuntimeException ignored) {
          // Draft/deleted definitions or definitions without executable JobSpec are not discoverable.
        }
      }
      if (pageNo >= page.pages()) break;
      pageNo++;
    }
    return List.copyOf(registrations);
  }

  private TaskRegistration registration(OfflineJobDefinition definition) {
    if (!isWorkflowEligible(definition)) {
      throw new IllegalArgumentException("离线同步任务当前不可用于工作流：" + definition.getId());
    }
    String logicalJobSpec = service().resolveLogicalJobSpec(definition);
    String id = String.valueOf(definition.getId());
    String name = definition.getJobName();
    TaskDefinition descriptor = new TaskDefinition(id, name, "SYNC");
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        id,
        name,
        "SYNC",
        Math.max(1, definition.getVersion() == null ? 1 : definition.getVersion()),
        definition.getConfigDigest(),
        definition.getDefinitionJson(),
        logicalJobSpec);
    return new TaskRegistration(descriptor, snapshot);
  }

  private OfflineDefinitionQuery query(int pageNo) {
    return new OfflineDefinitionQuery(
        pageNo,
        PAGE_SIZE,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private OfflineJobDefinitionService service() {
    OfflineJobDefinitionService service = definitionServiceProvider.getIfAvailable();
    if (service == null) {
      throw new IllegalStateException("离线同步能力未启用");
    }
    return service;
  }

  private Long parseTaskId(String taskId) {
    if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId 不能为空");
    try {
      long value = Long.parseLong(taskId.trim());
      if (value <= 0L) throw new NumberFormatException("not positive");
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("SYNC taskId 不合法：" + taskId, exception);
    }
  }

  static boolean isWorkflowEligible(OfflineJobDefinition definition) {
    return definition != null
        && definition.getId() != null
        && definition.getJobName() != null
        && RELEASE_STATE_ONLINE.equalsIgnoreCase(definition.getReleaseState())
        && !Boolean.TRUE.equals(definition.getScheduleEnabled());
  }
}
