package io.yak.ops.business.job.task;

import io.yak.framework.common.PagingData;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.service.OfflineJobDefinitionService;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionQueryDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobDefinitionVO;
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
  private static final String SCHEDULE_STATUS_PAUSED = "PAUSED";

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
      OfflineJobDefinitionQueryDTO query = new OfflineJobDefinitionQueryDTO();
      query.setCurrent(pageNo);
      query.setPageSize(PAGE_SIZE);
      PagingData<OfflineJobDefinitionVO> page = service.page(query);
      for (OfflineJobDefinitionVO definition : page.getBizData()) {
        if (!isWorkflowEligible(definition)) continue;
        try {
          OfflineJobDefinition current = service.require(definition.getId());
          String logicalJobSpec = service.resolveLogicalJobSpec(current);
          String id = String.valueOf(current.getId());
          String name = current.getJobName();
          TaskDefinition task = new TaskDefinition(id, name, "SYNC");
          taskSnapshot.put(id, task);
          versionSnapshot.put(
              id,
              new TaskVersionSnapshot(
                  id,
                  name,
                  "SYNC",
                  Math.max(1, current.getVersion() == null ? 1 : current.getVersion()),
                  current.getConfigDigest(),
                  current.getDefinitionJson(),
                  logicalJobSpec));
        } catch (RuntimeException ignored) {
          // 草稿或没有可执行 JobSpec 的同步任务不进入工作流任务列表。
        }
      }
      if (page.getPagination() == null || pageNo >= page.getPagination().getPages()) break;
      pageNo++;
    }

    tasks.clear();
    tasks.putAll(taskSnapshot);
    snapshots.clear();
    snapshots.putAll(versionSnapshot);
  }

  static boolean isWorkflowEligible(OfflineJobDefinitionVO definition) {
    return definition != null
        && definition.getId() != null
        && definition.getJobName() != null
        && RELEASE_STATE_ONLINE.equalsIgnoreCase(definition.getReleaseState())
        && SCHEDULE_STATUS_PAUSED.equalsIgnoreCase(definition.getScheduleStatus());
  }
}
