package io.yak.ops.business.job.task;

import io.yak.framework.common.PageData;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.service.OfflineJobDefinitionService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Workflow task registry. Concrete business domains contribute tasks through {@link TaskProvider}. */
@Service
public class InMemoryTaskRegistry implements TaskRegistry {

  private static final int PAGE_SIZE = 200;
  private static final String RELEASE_STATE_ONLINE = "ONLINE";

  private final ObjectProvider<OfflineJobDefinitionService> definitionServiceProvider;
  private final ObjectProvider<TaskProvider> taskProviderProvider;
  private final ConcurrentMap<String, TaskDefinition> tasks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, TaskVersionSnapshot> snapshots = new ConcurrentHashMap<>();

  public InMemoryTaskRegistry(
      ObjectProvider<OfflineJobDefinitionService> definitionServiceProvider,
      ObjectProvider<TaskProvider> taskProviderProvider) {
    this.definitionServiceProvider = definitionServiceProvider;
    this.taskProviderProvider = taskProviderProvider;
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
    Map<String, TaskDefinition> taskSnapshot = new LinkedHashMap<>();
    Map<String, TaskVersionSnapshot> versionSnapshot = new LinkedHashMap<>();
    appendOfflineSyncTasks(taskSnapshot, versionSnapshot);
    appendProvidedTasks(taskSnapshot, versionSnapshot);

    tasks.clear();
    tasks.putAll(taskSnapshot);
    snapshots.clear();
    snapshots.putAll(versionSnapshot);
  }

  private void appendOfflineSyncTasks(
      Map<String, TaskDefinition> taskSnapshot,
      Map<String, TaskVersionSnapshot> versionSnapshot) {
    OfflineJobDefinitionService service = definitionServiceProvider.getIfAvailable();
    if (service == null) return;

    int pageNo = 1;
    while (true) {
      PageData<OfflineJobDefinition> page = service.pageDomain(
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
          OfflineJobDefinition current = service.require(definition.getId());
          String logicalJobSpec = service.resolveLogicalJobSpec(current);
          String id = String.valueOf(current.getId());
          String name = current.getJobName();
          putTask(
              taskSnapshot,
              versionSnapshot,
              new TaskDefinition(id, name, "SYNC"),
              new TaskVersionSnapshot(
                  id,
                  name,
                  "SYNC",
                  Math.max(1, current.getVersion() == null ? 1 : current.getVersion()),
                  current.getConfigDigest(),
                  current.getDefinitionJson(),
                  logicalJobSpec));
        } catch (RuntimeException ignored) {
          // 草稿、被删除或没有可执行 JobSpec 的同步任务不进入工作流任务列表。
        }
      }
      if (pageNo >= page.pages()) break;
      pageNo++;
    }
  }

  private void appendProvidedTasks(
      Map<String, TaskDefinition> taskSnapshot,
      Map<String, TaskVersionSnapshot> versionSnapshot) {
    for (TaskProvider provider : taskProviderProvider.orderedStream().toList()) {
      for (TaskDefinition task : provider.list()) {
        if (task == null || task.id() == null || task.id().isBlank()) continue;
        TaskVersionSnapshot snapshot = provider.snapshot(task.id());
        putTask(taskSnapshot, versionSnapshot, task, snapshot);
      }
    }
  }

  private void putTask(
      Map<String, TaskDefinition> taskSnapshot,
      Map<String, TaskVersionSnapshot> versionSnapshot,
      TaskDefinition task,
      TaskVersionSnapshot snapshot) {
    if (snapshot == null || !task.id().equals(snapshot.taskId())) {
      throw new IllegalStateException("任务定义与版本快照不匹配：" + task.id());
    }
    TaskDefinition existing = taskSnapshot.putIfAbsent(task.id(), task);
    if (existing != null) {
      throw new IllegalStateException(
          "重复的工作流任务 ID：" + task.id() + "，类型=" + existing.type() + "/" + task.type());
    }
    versionSnapshot.put(task.id(), snapshot);
  }

  static boolean isWorkflowEligible(OfflineJobDefinition definition) {
    return definition != null
        && definition.getId() != null
        && definition.getJobName() != null
        && RELEASE_STATE_ONLINE.equalsIgnoreCase(definition.getReleaseState())
        && !Boolean.TRUE.equals(definition.getScheduleEnabled());
  }
}
