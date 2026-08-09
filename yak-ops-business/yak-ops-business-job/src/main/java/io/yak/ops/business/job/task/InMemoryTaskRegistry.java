package io.yak.ops.business.job.task;

import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflinePage;
import io.yak.ops.business.sync.offline.service.OfflineJobDefinitionService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 第一阶段任务注册表；同步任务元数据由离线同步定义服务投影。 */
@Service
public class InMemoryTaskRegistry implements TaskRegistry {

  private static final int PAGE_SIZE = 200;
  private static final String RELEASE_STATE_ONLINE = "ONLINE";

  private final ObjectProvider<OfflineJobDefinitionService> definitionServiceProvider;
  private final ConcurrentMap<String, TaskDefinition> tasks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, TaskVersionSnapshot> snapshots = new ConcurrentHashMap<>();

  public InMemoryTaskRegistry(ObjectProvider<OfflineJobDefinitionService> definitionServiceProvider) {
    this.definitionServiceProvider = definitionServiceProvider;
  }

  @Override
  public List<TaskDefinition> list() {
    refresh();
    return tasks.values().stream().sorted(Comparator.comparing(TaskDefinition::name)).toList();
  }

  @Override
  public TaskDefinition get(String taskId) {
    String normalized = requireTaskId(taskId);
    refresh();
    TaskDefinition task = tasks.get(normalized);
    if (task == null) throw new IllegalArgumentException("任务不存在或尚不可执行：" + taskId);
    return task;
  }

  @Override
  public TaskVersionSnapshot snapshot(String taskId) {
    String normalized = requireTaskId(taskId);
    refresh();
    TaskVersionSnapshot snapshot = snapshots.get(normalized);
    if (snapshot == null) throw new IllegalArgumentException("任务不存在或尚不可执行：" + taskId);
    return snapshot;
  }

  private String requireTaskId(String taskId) {
    if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId 不能为空");
    return taskId.trim();
  }

  private void refresh() {
    OfflineJobDefinitionService service = definitionServiceProvider.getIfAvailable();
    if (service == null) {
      tasks.clear();
      snapshots.clear();
      return;
    }

    Map<String, TaskDefinition> taskSnapshot = new LinkedHashMap<>();
    Map<String, TaskVersionSnapshot> versionSnapshot = new LinkedHashMap<>();
    int pageNo = 1;
    while (true) {
      OfflinePage<OfflineJobDefinition> page = service.pageDomain(
          new OfflineDefinitionQuery(
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
              null));
      for (OfflineJobDefinition definition : page.records()) {
        if (!isWorkflowEligible(definition)) continue;
        try {
          String logicalJobSpec = service.resolveLogicalJobSpec(definition);
          String id = String.valueOf(definition.getId());
          String name = definition.getJobName();
          TaskDefinition task = new TaskDefinition(id, name, "SYNC");
          taskSnapshot.put(id, task);
          versionSnapshot.put(
              id,
              new TaskVersionSnapshot(
                  id,
                  name,
                  "SYNC",
                  Math.max(1, definition.getVersion() == null ? 1 : definition.getVersion()),
                  definition.getConfigDigest(),
                  definition.getDefinitionJson(),
                  logicalJobSpec));
        } catch (RuntimeException ignored) {
          // 草稿或没有可执行 JobSpec 的同步任务不进入工作流任务列表。
        }
      }
      if (pageNo >= page.pages()) break;
      pageNo++;
    }

    tasks.clear();
    tasks.putAll(taskSnapshot);
    snapshots.clear();
    snapshots.putAll(versionSnapshot);
  }

  static boolean isWorkflowEligible(OfflineJobDefinition definition) {
    return definition != null
        && definition.getId() != null
        && definition.getJobName() != null
        && RELEASE_STATE_ONLINE.equalsIgnoreCase(definition.getReleaseState())
        && !Boolean.TRUE.equals(definition.getScheduleEnabled());
  }
}
