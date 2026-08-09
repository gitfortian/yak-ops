package io.yak.ops.business.workflow.service;

import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.model.WorkflowDefinitionCreateRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.EdgeRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.NodeRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionVO;
import io.yak.ops.business.workflow.model.WorkflowInstanceVO;
import io.yak.ops.business.workflow.model.WorkflowRunRequest;
import io.yak.ops.business.workflow.model.WorkflowVersionVO;
import io.yak.ops.business.workflow.model.WorkflowVersionVO.TaskBindingVO;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 工作流草稿、发布版本和运行入口。当前存储仍为进程内存。 */
@Service
public class WorkflowDefinitionService {
  private static final Set<String> ACTIVE = Set.of(
      "CREATED", "WAITING", "READY", "SUBMITTED", "RUNNING", "PAUSING", "PAUSED", "RESUMING");
  private final WorkflowRuntimeService runtimeService;
  private final TaskRegistry taskRegistry;
  private final ConcurrentMap<String, DefinitionState> definitions = new ConcurrentHashMap<>();

  public WorkflowDefinitionService(WorkflowRuntimeService runtimeService, TaskRegistry taskRegistry) {
    this.runtimeService = runtimeService;
    this.taskRegistry = taskRegistry;
  }

  public List<WorkflowDefinitionVO> list(String keyword, String status) {
    String k = normalize(keyword), s = normalizeUpper(status);
    return definitions.values().stream()
        .filter(x -> k == null || x.name.toLowerCase(Locale.ROOT).contains(k)
            || text(x.description).toLowerCase(Locale.ROOT).contains(k))
        .filter(x -> s == null || s.equals(x.status))
        .sorted(Comparator.comparing((DefinitionState x) -> x.updateTime).reversed())
        .map(this::toView).toList();
  }

  public WorkflowDefinitionVO create(WorkflowDefinitionCreateRequest request) {
    if (request == null) throw new IllegalArgumentException("工作流创建参数不能为空");
    DefinitionState s = new DefinitionState();
    Instant now = Instant.now();
    s.id = "workflow-" + UUID.randomUUID();
    s.name = required(request.name(), "工作流名称不能为空");
    s.description = trim(request.description());
    s.status = "DRAFT";
    s.nodes = List.of(); s.edges = List.of(); s.input = Map.of();
    s.failureStrategy = "CONTINUE_INDEPENDENT_BRANCHES";
    s.draftRevision = 1L; s.versions = new ArrayList<>();
    s.createTime = now; s.updateTime = now;
    definitions.put(s.id, s);
    return toView(s);
  }

  public WorkflowDefinitionVO get(String id) { return toView(require(id)); }

  public WorkflowDefinitionVO update(String id, WorkflowDefinitionUpdateRequest request) {
    if (request == null) throw new IllegalArgumentException("工作流配置不能为空");
    DefinitionState s = require(id);
    synchronized (s) {
      s.name = required(request.name(), "工作流名称不能为空");
      s.description = trim(request.description());
      s.nodes = List.copyOf(request.nodes()); s.edges = List.copyOf(request.edges());
      s.input = Map.copyOf(new LinkedHashMap<>(request.input()));
      s.workflowTimeoutSeconds = request.workflowTimeoutSeconds();
      s.failureStrategy = request.failureStrategy();
      s.draftRevision++; s.updateTime = Instant.now();
      return toView(s);
    }
  }

  public WorkflowDefinitionVO online(String id) {
    DefinitionState s = require(id);
    synchronized (s) {
      if (s.activeVersion == null || draftChanged(s)) {
        PreparedDraft draft = prepare(s);
        WorkflowVersion v = new WorkflowVersion(
            "workflow-version-" + UUID.randomUUID(), s.id, ++s.latestVersionNo,
            s.draftRevision, draft.request(), draft.tasks(), Instant.now());
        s.versions.add(v); s.activeVersion = v;
      }
      s.status = "ONLINE"; s.updateTime = Instant.now();
      return toView(s);
    }
  }

  /** 下线只阻止新的正式运行，已经启动的版本实例继续完成。 */
  public WorkflowDefinitionVO offline(String id) {
    DefinitionState s = require(id);
    synchronized (s) {
      if (s.activeVersion == null) throw new IllegalStateException("工作流还没有已发布版本");
      s.status = "OFFLINE"; s.updateTime = Instant.now();
      return toView(s);
    }
  }

  public WorkflowDefinitionVO run(String id) {
    DefinitionState s = require(id);
    synchronized (s) {
      if (!"ONLINE".equals(s.status) || s.activeVersion == null)
        throw new IllegalStateException("工作流没有启用的发布版本，不能正式执行");
      ensureIdle(s);
      WorkflowVersion v = s.activeVersion;
      return activate(s, runtimeService.run(v.runRequest(), v.taskVersionsByNode(), v.id(), v.versionNo(), false));
    }
  }

  /** 直接执行当前草稿；不要求发布，也不改变激活版本。 */
  public WorkflowDefinitionVO testRun(String id) {
    DefinitionState s = require(id);
    synchronized (s) {
      ensureIdle(s);
      PreparedDraft draft = prepare(s);
      return activate(s, runtimeService.run(draft.request(), draft.tasks(), null, null, true));
    }
  }

  public List<WorkflowVersionVO> versions(String id) {
    DefinitionState s = require(id);
    synchronized (s) {
      String activeId = s.activeVersion == null ? null : s.activeVersion.id();
      return s.versions.stream().sorted(Comparator.comparingInt(WorkflowVersion::versionNo).reversed())
          .map(v -> versionView(v, v.id().equals(activeId))).toList();
    }
  }

  public WorkflowDefinitionVO pause(String id) {
    DefinitionState s = require(id);
    synchronized (s) {
      WorkflowInstanceVO v = runtimeService.pause(requireLatestExecution(s));
      s.latestExecutionStatus = v.status(); s.updateTime = Instant.now(); return toView(s);
    }
  }

  public WorkflowDefinitionVO resume(String id) {
    DefinitionState s = require(id);
    synchronized (s) {
      WorkflowInstanceVO v = runtimeService.resume(requireLatestExecution(s));
      s.latestExecutionStatus = v.status(); s.updateTime = Instant.now(); return toView(s);
    }
  }

  public void delete(String id) {
    DefinitionState s = require(id);
    synchronized (s) {
      refresh(s);
      if (isActive(s.latestExecutionStatus)) throw new IllegalStateException("工作流正在运行，不能删除");
      if ("ONLINE".equals(s.status)) throw new IllegalStateException("已启用工作流请先下线后再删除");
      definitions.remove(s.id, s);
    }
  }

  private WorkflowDefinitionVO activate(DefinitionState s, WorkflowInstanceVO prepared) {
    WorkflowInstanceVO v = runtimeService.activate(prepared.id());
    s.latestExecutionId = v.id(); s.latestExecutionStatus = v.status(); s.updateTime = Instant.now();
    return toView(s);
  }

  private void ensureIdle(DefinitionState s) {
    refresh(s);
    if (isActive(s.latestExecutionStatus)) throw new IllegalStateException("工作流已有运行中的执行实例");
  }

  private PreparedDraft prepare(DefinitionState s) {
    validateGraph(s);
    WorkflowStartGraphCompiler.RuntimeGraph graph = WorkflowStartGraphCompiler.compile(s.nodes, s.edges, s.input);
    Map<String, TaskVersionSnapshot> tasks = new LinkedHashMap<>();
    for (NodeRequest n : graph.nodes()) {
      TaskVersionSnapshot task = taskRegistry.snapshot(n.taskId());
      if (!"SYNC".equalsIgnoreCase(task.type()))
        throw new IllegalStateException("第一阶段工作流仅支持 SYNC 任务：" + n.taskId());
      tasks.put(n.id(), task);
    }
    return new PreparedDraft(toRunRequest(s, graph), Map.copyOf(tasks));
  }

  private void validateGraph(DefinitionState s) {
    if (s.nodes.isEmpty()) throw new IllegalStateException("请先配置至少一个任务节点");
    Set<String> ids = new HashSet<>();
    for (NodeRequest n : s.nodes)
      if (!ids.add(n.id())) throw new IllegalStateException("工作流存在重复节点 ID：" + n.id());
    Map<String, Integer> in = new HashMap<>(); Map<String, List<String>> next = new HashMap<>();
    ids.forEach(x -> { in.put(x, 0); next.put(x, new ArrayList<>()); });
    for (EdgeRequest e : s.edges) {
      if (!ids.contains(e.source()) || !ids.contains(e.target()))
        throw new IllegalStateException("连线引用了不存在的节点：" + e.source() + " -> " + e.target());
      if (e.source().equals(e.target())) throw new IllegalStateException("工作流节点不能连接自身：" + e.source());
      next.get(e.source()).add(e.target()); in.compute(e.target(), (k, v) -> v == null ? 1 : v + 1);
    }
    ArrayDeque<String> q = new ArrayDeque<>(); in.forEach((x, d) -> { if (d == 0) q.add(x); });
    int visited = 0;
    while (!q.isEmpty()) {
      String x = q.removeFirst(); visited++;
      for (String y : next.get(x)) if (in.compute(y, (k, v) -> v == null ? 0 : v - 1) == 0) q.addLast(y);
    }
    if (visited != ids.size()) throw new IllegalStateException("工作流存在循环依赖，不能发布或测试");
  }

  private WorkflowRunRequest toRunRequest(DefinitionState s, WorkflowStartGraphCompiler.RuntimeGraph graph) {
    List<WorkflowRunRequest.NodeRequest> nodes = graph.nodes().stream().map(n -> new WorkflowRunRequest.NodeRequest(
        n.id(), n.taskId(), n.maxAttempts(), n.retryDelaySeconds(), n.dispatchTimeoutSeconds(),
        n.executionTimeoutSeconds(), n.inputMapping(), n.triggerRule(), n.failurePolicy())).toList();
    List<WorkflowRunRequest.EdgeRequest> edges = graph.edges().stream()
        .map(e -> new WorkflowRunRequest.EdgeRequest(e.source(), e.target())).toList();
    return new WorkflowRunRequest(s.name, nodes, edges, s.input, s.workflowTimeoutSeconds, s.failureStrategy);
  }

  private WorkflowVersionVO versionView(WorkflowVersion v, boolean active) {
    List<TaskBindingVO> bindings = v.taskVersionsByNode().entrySet().stream().map(e -> new TaskBindingVO(
        e.getKey(), e.getValue().taskId(), e.getValue().name(), e.getValue().version())).toList();
    return new WorkflowVersionVO(v.id(), v.versionNo(), active, v.runRequest().nodes().size(),
        v.runRequest().edges().size(), bindings, v.publishedAt());
  }

  private WorkflowDefinitionVO toView(DefinitionState s) {
    synchronized (s) {
      refresh(s); WorkflowVersion a = s.activeVersion;
      return new WorkflowDefinitionVO(s.id, s.name, s.description, s.status, s.nodes.size(), s.edges.size(),
          s.nodes, s.edges, s.input, s.workflowTimeoutSeconds, s.failureStrategy,
          a == null ? null : a.id(), a == null ? null : a.versionNo(), s.latestVersionNo, draftChanged(s),
          s.latestExecutionId, s.latestExecutionStatus, s.createTime, s.updateTime);
    }
  }

  private boolean draftChanged(DefinitionState s) {
    return s.activeVersion == null || s.activeVersion.draftRevision() != s.draftRevision;
  }

  private void refresh(DefinitionState s) {
    if (!StringUtils.hasText(s.latestExecutionId)) return;
    try { s.latestExecutionStatus = runtimeService.getInstance(s.latestExecutionId).status(); }
    catch (RuntimeException ignored) { }
  }

  private DefinitionState require(String id) {
    if (!StringUtils.hasText(id)) throw new IllegalArgumentException("工作流 ID 不能为空");
    DefinitionState s = definitions.get(id.trim());
    if (s == null) throw new IllegalArgumentException("工作流定义不存在：" + id);
    return s;
  }

  private String requireLatestExecution(DefinitionState s) {
    refresh(s);
    if (!StringUtils.hasText(s.latestExecutionId)) throw new IllegalStateException("工作流还没有可控制的执行实例");
    return s.latestExecutionId;
  }

  private boolean isActive(String status) { return status != null && ACTIVE.contains(status.toUpperCase(Locale.ROOT)); }
  private String required(String value, String message) {
    if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message); return value.trim();
  }
  private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
  private String text(String value) { return value == null ? "" : value; }
  private String normalize(String v) { return StringUtils.hasText(v) ? v.trim().toLowerCase(Locale.ROOT) : null; }
  private String normalizeUpper(String v) { return StringUtils.hasText(v) ? v.trim().toUpperCase(Locale.ROOT) : null; }

  private record PreparedDraft(WorkflowRunRequest request, Map<String, TaskVersionSnapshot> tasks) { }
  private static final class DefinitionState {
    String id, name, description, status, failureStrategy, latestExecutionId, latestExecutionStatus;
    List<NodeRequest> nodes; List<EdgeRequest> edges; Map<String, Object> input;
    long workflowTimeoutSeconds, draftRevision; int latestVersionNo;
    List<WorkflowVersion> versions; WorkflowVersion activeVersion; Instant createTime, updateTime;
  }
}
