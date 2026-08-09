package io.yak.ops.business.workflow.service;

import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.domain.WorkflowEdgeSpec;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import io.yak.ops.business.workflow.domain.WorkflowVersion;
import io.yak.ops.business.workflow.persistence.NoopWorkflowDefinitionPersistence;
import io.yak.ops.business.workflow.persistence.WorkflowDefinitionPersistence;
import io.yak.ops.business.workflow.persistence.WorkflowDefinitionPersistence.DefinitionRecord;
import io.yak.ops.business.workflow.persistence.WorkflowDefinitionPersistence.VersionRecord;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO.EdgeDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO.NodeDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO.EdgeVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO.NodeVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowVersionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowVersionVO.TaskBindingVO;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 工作流草稿、不可变发布版本和运行入口。数据库是生产环境事实来源。 */
@Service
public class WorkflowDefinitionService {
  private static final Set<String> ACTIVE = Set.of(
      "CREATED", "WAITING", "READY", "SUBMITTED", "RUNNING", "PAUSING", "PAUSED", "RESUMING");

  private final WorkflowRuntimeService runtimeService;
  private final TaskRegistry taskRegistry;
  private final WorkflowDefinitionPersistence persistence;
  private final ConcurrentMap<String, DefinitionState> definitions = new ConcurrentHashMap<>();

  @Autowired
  public WorkflowDefinitionService(
      WorkflowRuntimeService runtimeService,
      TaskRegistry taskRegistry,
      ObjectProvider<WorkflowDefinitionPersistence> persistence,
      @Value("${yak.database.enabled:true}") boolean databaseEnabled) {
    this(runtimeService, taskRegistry, resolvePersistence(persistence, databaseEnabled));
  }

  /** Focused tests and explicit database-disabled development retain the lightweight catalog. */
  WorkflowDefinitionService(
      WorkflowRuntimeService runtimeService,
      TaskRegistry taskRegistry) {
    this(runtimeService, taskRegistry, NoopWorkflowDefinitionPersistence.INSTANCE);
  }

  private static WorkflowDefinitionPersistence resolvePersistence(
      ObjectProvider<WorkflowDefinitionPersistence> provider,
      boolean databaseEnabled) {
    WorkflowDefinitionPersistence resolved = provider.getIfAvailable();
    if (resolved != null) {
      return resolved;
    }
    if (!databaseEnabled) {
      return NoopWorkflowDefinitionPersistence.INSTANCE;
    }
    throw new IllegalStateException(
        "Workflow durable persistence bean is missing while yak.database.enabled=true: "
            + "WorkflowDefinitionPersistence");
  }

  WorkflowDefinitionService(
      WorkflowRuntimeService runtimeService,
      TaskRegistry taskRegistry,
      WorkflowDefinitionPersistence persistence) {
    this.runtimeService = runtimeService;
    this.taskRegistry = taskRegistry;
    this.persistence = persistence;
    restoreCatalog();
  }

  public List<WorkflowDefinitionVO> list(String keyword, String status) {
    String k = normalize(keyword);
    String s = normalizeUpper(status);
    return definitions.values().stream()
        .filter(x -> k == null
            || x.name.toLowerCase(Locale.ROOT).contains(k)
            || text(x.description).toLowerCase(Locale.ROOT).contains(k))
        .filter(x -> s == null || s.equals(x.status))
        .sorted(Comparator.comparing((DefinitionState x) -> x.updateTime).reversed())
        .map(this::toView)
        .toList();
  }

  public WorkflowDefinitionVO create(WorkflowDefinitionCreateDTO request) {
    if (request == null) throw new IllegalArgumentException("工作流创建参数不能为空");
    DefinitionState state = new DefinitionState();
    Instant now = Instant.now();
    state.id = "workflow-" + UUID.randomUUID();
    state.name = required(request.name(), "工作流名称不能为空");
    state.description = trim(request.description());
    state.status = "DRAFT";
    state.nodes = List.of();
    state.edges = List.of();
    state.input = Map.of();
    state.editorMeta = Map.of();
    state.failureStrategy = "CONTINUE_INDEPENDENT_BRANCHES";
    state.draftRevision = 1L;
    state.versions = new ArrayList<>();
    state.createTime = now;
    state.updateTime = now;
    persistence.saveDefinition(toRecord(state));
    definitions.put(state.id, state);
    return toView(state);
  }

  public WorkflowDefinitionVO get(String id) {
    return toView(require(id));
  }

  public WorkflowDefinitionVO update(String id, WorkflowDefinitionUpdateDTO request) {
    if (request == null) throw new IllegalArgumentException("工作流配置不能为空");
    DefinitionState state = require(id);
    synchronized (state) {
      String nextName = required(request.name(), "工作流名称不能为空");
      String nextDescription = trim(request.description());
      List<WorkflowNodeSpec> nextNodes = request.nodes().stream().map(this::toSpec).toList();
      List<WorkflowEdgeSpec> nextEdges = request.edges().stream().map(this::toSpec).toList();
      Map<String, Object> nextInput = Map.copyOf(new LinkedHashMap<>(request.input()));
      Map<String, Object> nextEditorMeta = Map.copyOf(new LinkedHashMap<>(request.editorMeta()));
      long nextTimeout = request.workflowTimeoutSeconds();
      String nextFailureStrategy = request.failureStrategy();
      boolean changed = !Objects.equals(state.name, nextName)
          || !Objects.equals(state.description, nextDescription)
          || !Objects.equals(state.nodes, nextNodes)
          || !Objects.equals(state.edges, nextEdges)
          || !Objects.equals(state.input, nextInput)
          || !Objects.equals(state.editorMeta, nextEditorMeta)
          || state.workflowTimeoutSeconds != nextTimeout
          || !Objects.equals(state.failureStrategy, nextFailureStrategy);

      if (!changed) return toView(state);

      DefinitionStateSnapshot previous = DefinitionStateSnapshot.capture(state);
      state.name = nextName;
      state.description = nextDescription;
      state.nodes = nextNodes;
      state.edges = nextEdges;
      state.input = nextInput;
      state.editorMeta = nextEditorMeta;
      state.workflowTimeoutSeconds = nextTimeout;
      state.failureStrategy = nextFailureStrategy;
      state.draftRevision++;
      state.updateTime = Instant.now();
      try {
        persistence.saveDefinition(toRecord(state));
      } catch (RuntimeException exception) {
        previous.restore(state);
        throw exception;
      }
      return toView(state);
    }
  }

  public WorkflowDefinitionVO online(String id) {
    DefinitionState state = require(id);
    synchronized (state) {
      DefinitionStateSnapshot previous = DefinitionStateSnapshot.capture(state);
      WorkflowVersion published = null;
      try {
        if (state.activeVersion == null || draftChanged(state)) {
          PreparedDraft draft = prepare(state);
          published = new WorkflowVersion(
              "workflow-version-" + UUID.randomUUID(),
              state.id,
              state.latestVersionNo + 1,
              state.draftRevision,
              draft.spec(),
              state.editorMeta,
              draft.tasks(),
              Instant.now());
          state.latestVersionNo = published.versionNo();
          state.versions.add(published);
          state.activeVersion = published;
        }
        state.status = "ONLINE";
        state.updateTime = Instant.now();
        if (published == null) {
          persistence.saveDefinition(toRecord(state));
        } else {
          persistence.publish(toRecord(state), toRecord(published));
        }
      } catch (RuntimeException exception) {
        if (published != null) state.versions.remove(published);
        previous.restore(state);
        throw exception;
      }
      return toView(state);
    }
  }

  /** 下线只阻止新的正式运行，已经启动的版本实例继续完成。 */
  public WorkflowDefinitionVO offline(String id) {
    DefinitionState state = require(id);
    synchronized (state) {
      if (state.activeVersion == null) {
        throw new IllegalStateException("工作流还没有已发布版本");
      }
      String previousStatus = state.status;
      Instant previousUpdateTime = state.updateTime;
      state.status = "OFFLINE";
      state.updateTime = Instant.now();
      try {
        persistence.saveDefinition(toRecord(state));
      } catch (RuntimeException exception) {
        state.status = previousStatus;
        state.updateTime = previousUpdateTime;
        throw exception;
      }
      return toView(state);
    }
  }

  public WorkflowDefinitionVO run(String id) {
    DefinitionState state = require(id);
    synchronized (state) {
      if (!"ONLINE".equals(state.status) || state.activeVersion == null) {
        throw new IllegalStateException("工作流没有启用的发布版本，不能正式执行");
      }
      ensureIdle(state);
      WorkflowVersion version = state.activeVersion;
      return activate(
          state,
          runtimeService.run(
              version.runSpec(),
              version.taskVersionsByNode(),
              version.id(),
              version.versionNo(),
              false));
    }
  }

  /** 直接执行当前草稿；不要求发布，也不改变激活版本。 */
  public WorkflowDefinitionVO testRun(String id) {
    DefinitionState state = require(id);
    synchronized (state) {
      ensureIdle(state);
      PreparedDraft draft = prepare(state);
      return activate(
          state,
          runtimeService.run(draft.spec(), draft.tasks(), null, null, true));
    }
  }

  public List<WorkflowVersionVO> versions(String id) {
    DefinitionState state = require(id);
    synchronized (state) {
      String activeId = state.activeVersion == null ? null : state.activeVersion.id();
      return state.versions.stream()
          .sorted(Comparator.comparingInt(WorkflowVersion::versionNo).reversed())
          .map(version -> versionView(version, version.id().equals(activeId)))
          .toList();
    }
  }

  public WorkflowDefinitionVO pause(String id) {
    DefinitionState state = require(id);
    synchronized (state) {
      WorkflowInstanceVO execution = runtimeService.pause(requireLatestExecution(state));
      state.latestExecutionStatus = execution.status();
      state.updateTime = Instant.now();
      persistence.saveDefinition(toRecord(state));
      return toView(state);
    }
  }

  public WorkflowDefinitionVO resume(String id) {
    DefinitionState state = require(id);
    synchronized (state) {
      WorkflowInstanceVO execution = runtimeService.resume(requireLatestExecution(state));
      state.latestExecutionStatus = execution.status();
      state.updateTime = Instant.now();
      persistence.saveDefinition(toRecord(state));
      return toView(state);
    }
  }

  public void delete(String id) {
    DefinitionState state = require(id);
    synchronized (state) {
      refresh(state);
      if (isActive(state.latestExecutionStatus)) {
        throw new IllegalStateException("工作流正在运行，不能删除");
      }
      if ("ONLINE".equals(state.status)) {
        throw new IllegalStateException("已启用工作流请先下线后再删除");
      }
      persistence.deleteDefinition(state.id);
      definitions.remove(state.id, state);
    }
  }

  private WorkflowDefinitionVO activate(
      DefinitionState state,
      WorkflowInstanceVO prepared) {
    String previousExecutionId = state.latestExecutionId;
    String previousExecutionStatus = state.latestExecutionStatus;
    Instant previousUpdateTime = state.updateTime;
    state.latestExecutionId = prepared.id();
    state.latestExecutionStatus = prepared.status();
    state.updateTime = Instant.now();
    try {
      persistence.saveDefinition(toRecord(state));
    } catch (RuntimeException exception) {
      state.latestExecutionId = previousExecutionId;
      state.latestExecutionStatus = previousExecutionStatus;
      state.updateTime = previousUpdateTime;
      throw exception;
    }

    WorkflowInstanceVO execution = runtimeService.activate(prepared.id());
    if (!Objects.equals(state.latestExecutionStatus, execution.status())) {
      state.latestExecutionStatus = execution.status();
      state.updateTime = Instant.now();
      persistence.saveDefinition(toRecord(state));
    }
    return toView(state);
  }

  private void ensureIdle(DefinitionState state) {
    refresh(state);
    if (isActive(state.latestExecutionStatus)) {
      throw new IllegalStateException("工作流已有运行中的执行实例");
    }
  }

  private PreparedDraft prepare(DefinitionState state) {
    validateGraph(state);
    WorkflowStartGraphCompiler.RuntimeGraph graph = WorkflowStartGraphCompiler.compile(
        state.nodes,
        state.edges,
        state.editorMeta,
        state.input);
    Map<String, TaskVersionSnapshot> tasks = new LinkedHashMap<>();
    for (WorkflowNodeSpec node : graph.nodes()) {
      TaskVersionSnapshot task = taskRegistry.snapshot(node.taskId());
      if (!"SYNC".equalsIgnoreCase(task.type())) {
        throw new IllegalStateException("第一阶段工作流仅支持 SYNC 任务：" + node.taskId());
      }
      tasks.put(node.id(), task);
    }
    return new PreparedDraft(toRunSpec(state, graph), Map.copyOf(tasks));
  }

  private void validateGraph(DefinitionState state) {
    if (state.nodes.isEmpty()) throw new IllegalStateException("请先配置至少一个任务节点");
    Set<String> ids = new HashSet<>();
    for (WorkflowNodeSpec node : state.nodes) {
      if (!ids.add(node.id())) {
        throw new IllegalStateException("工作流存在重复节点 ID：" + node.id());
      }
    }
    Map<String, Integer> in = new HashMap<>();
    Map<String, List<String>> next = new HashMap<>();
    ids.forEach(nodeId -> {
      in.put(nodeId, 0);
      next.put(nodeId, new ArrayList<>());
    });
    for (WorkflowEdgeSpec edge : state.edges) {
      if (!ids.contains(edge.source()) || !ids.contains(edge.target())) {
        throw new IllegalStateException(
            "连线引用了不存在的节点：" + edge.source() + " -> " + edge.target());
      }
      if (edge.source().equals(edge.target())) {
        throw new IllegalStateException("工作流节点不能连接自身：" + edge.source());
      }
      next.get(edge.source()).add(edge.target());
      in.compute(edge.target(), (key, value) -> value == null ? 1 : value + 1);
    }
    ArrayDeque<String> queue = new ArrayDeque<>();
    in.forEach((nodeId, degree) -> {
      if (degree == 0) queue.add(nodeId);
    });
    int visited = 0;
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      visited++;
      for (String successor : next.get(current)) {
        if (in.compute(successor, (key, value) -> value == null ? 0 : value - 1) == 0) {
          queue.addLast(successor);
        }
      }
    }
    if (visited != ids.size()) {
      throw new IllegalStateException("工作流存在循环依赖，不能发布或测试");
    }
  }

  private WorkflowRunSpec toRunSpec(
      DefinitionState state,
      WorkflowStartGraphCompiler.RuntimeGraph graph) {
    return new WorkflowRunSpec(
        state.name,
        graph.nodes(),
        graph.edges(),
        state.input,
        state.workflowTimeoutSeconds,
        state.failureStrategy);
  }

  private WorkflowVersionVO versionView(WorkflowVersion version, boolean active) {
    List<TaskBindingVO> bindings = version.taskVersionsByNode().entrySet().stream()
        .map(entry -> new TaskBindingVO(
            entry.getKey(),
            entry.getValue().taskId(),
            entry.getValue().name(),
            entry.getValue().version()))
        .toList();
    return new WorkflowVersionVO(
        version.id(),
        version.versionNo(),
        active,
        version.runSpec().nodes().size(),
        version.runSpec().edges().size(),
        bindings,
        version.publishedAt());
  }

  private WorkflowDefinitionVO toView(DefinitionState state) {
    synchronized (state) {
      refresh(state);
      WorkflowVersion active = state.activeVersion;
      return new WorkflowDefinitionVO(
          state.id,
          state.name,
          state.description,
          state.status,
          state.nodes.size(),
          state.edges.size(),
          state.nodes.stream().map(this::toView).toList(),
          state.edges.stream().map(this::toView).toList(),
          state.input,
          state.editorMeta,
          state.workflowTimeoutSeconds,
          state.failureStrategy,
          active == null ? null : active.id(),
          active == null ? null : active.versionNo(),
          state.latestVersionNo,
          draftChanged(state),
          state.latestExecutionId,
          state.latestExecutionStatus,
          state.createTime,
          state.updateTime);
    }
  }

  private WorkflowNodeSpec toSpec(NodeDTO node) {
    return new WorkflowNodeSpec(
        node.id(),
        node.taskId(),
        node.positionX(),
        node.positionY(),
        node.maxAttempts(),
        node.retryDelaySeconds(),
        node.dispatchTimeoutSeconds(),
        node.executionTimeoutSeconds(),
        node.inputMapping(),
        node.triggerRule(),
        node.failurePolicy());
  }

  private WorkflowEdgeSpec toSpec(EdgeDTO edge) {
    return new WorkflowEdgeSpec(edge.source(), edge.target());
  }

  private NodeVO toView(WorkflowNodeSpec node) {
    return new NodeVO(
        node.id(),
        node.taskId(),
        node.positionX(),
        node.positionY(),
        node.maxAttempts(),
        node.retryDelaySeconds(),
        node.dispatchTimeoutSeconds(),
        node.executionTimeoutSeconds(),
        node.inputMapping(),
        node.triggerRule(),
        node.failurePolicy());
  }

  private EdgeVO toView(WorkflowEdgeSpec edge) {
    return new EdgeVO(edge.source(), edge.target());
  }

  private boolean draftChanged(DefinitionState state) {
    return state.activeVersion == null
        || state.activeVersion.draftRevision() != state.draftRevision;
  }

  private void refresh(DefinitionState state) {
    if (!StringUtils.hasText(state.latestExecutionId)) return;
    String current = runtimeService.getInstance(state.latestExecutionId).status();
    if (!Objects.equals(state.latestExecutionStatus, current)) {
      state.latestExecutionStatus = current;
      state.updateTime = Instant.now();
      persistence.saveDefinition(toRecord(state));
    }
  }

  private DefinitionState require(String id) {
    if (!StringUtils.hasText(id)) throw new IllegalArgumentException("工作流 ID 不能为空");
    DefinitionState state = definitions.get(id.trim());
    if (state == null) throw new IllegalArgumentException("工作流定义不存在：" + id);
    return state;
  }

  private String requireLatestExecution(DefinitionState state) {
    refresh(state);
    if (!StringUtils.hasText(state.latestExecutionId)) {
      throw new IllegalStateException("工作流还没有可控制的执行实例");
    }
    return state.latestExecutionId;
  }

  private boolean isActive(String status) {
    return status != null && ACTIVE.contains(status.toUpperCase(Locale.ROOT));
  }

  private void restoreCatalog() {
    for (DefinitionRecord record : persistence.loadDefinitions()) {
      DefinitionState state = fromRecord(record);
      List<WorkflowVersion> versions = persistence.loadVersions(record.id()).stream()
          .map(this::fromRecord)
          .toList();
      state.versions = new ArrayList<>(versions);
      if (StringUtils.hasText(record.activeVersionId())) {
        state.activeVersion = versions.stream()
            .filter(version -> record.activeVersionId().equals(version.id()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "工作流激活版本不存在：" + record.id() + " -> " + record.activeVersionId()));
      }
      definitions.put(state.id, state);
    }
  }

  private DefinitionRecord toRecord(DefinitionState state) {
    return new DefinitionRecord(
        state.id,
        state.name,
        state.description,
        state.status,
        state.failureStrategy,
        state.nodes,
        state.edges,
        state.input,
        state.editorMeta,
        state.workflowTimeoutSeconds,
        state.draftRevision,
        state.latestVersionNo,
        state.activeVersion == null ? null : state.activeVersion.id(),
        state.latestExecutionId,
        state.latestExecutionStatus,
        state.createTime,
        state.updateTime);
  }

  private VersionRecord toRecord(WorkflowVersion version) {
    return new VersionRecord(
        version.id(),
        version.workflowId(),
        version.versionNo(),
        version.draftRevision(),
        version.runSpec(),
        version.editorMeta(),
        version.taskVersionsByNode(),
        version.publishedAt());
  }

  private DefinitionState fromRecord(DefinitionRecord record) {
    DefinitionState state = new DefinitionState();
    state.id = record.id();
    state.name = record.name();
    state.description = record.description();
    state.status = record.status();
    state.failureStrategy = record.failureStrategy();
    state.latestExecutionId = record.latestExecutionId();
    state.latestExecutionStatus = record.latestExecutionStatus();
    state.nodes = List.copyOf(record.nodes());
    state.edges = List.copyOf(record.edges());
    state.input = Map.copyOf(record.input());
    state.editorMeta = Map.copyOf(record.editorMeta());
    state.workflowTimeoutSeconds = record.workflowTimeoutSeconds();
    state.draftRevision = record.draftRevision();
    state.latestVersionNo = record.latestVersionNo();
    state.createTime = record.createTime();
    state.updateTime = record.updateTime();
    return state;
  }

  private WorkflowVersion fromRecord(VersionRecord record) {
    return new WorkflowVersion(
        record.id(),
        record.workflowId(),
        record.versionNo(),
        record.draftRevision(),
        record.runSpec(),
        record.editorMeta(),
        record.taskVersionsByNode(),
        record.publishedAt());
  }

  private String required(String value, String message) {
    if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
    return value.trim();
  }

  private String trim(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String text(String value) {
    return value == null ? "" : value;
  }

  private String normalize(String value) {
    return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
  }

  private String normalizeUpper(String value) {
    return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
  }

  private record PreparedDraft(
      WorkflowRunSpec spec,
      Map<String, TaskVersionSnapshot> tasks) {
  }

  private static final class DefinitionState {
    String id;
    String name;
    String description;
    String status;
    String failureStrategy;
    String latestExecutionId;
    String latestExecutionStatus;
    List<WorkflowNodeSpec> nodes;
    List<WorkflowEdgeSpec> edges;
    Map<String, Object> input;
    Map<String, Object> editorMeta;
    long workflowTimeoutSeconds;
    long draftRevision;
    int latestVersionNo;
    List<WorkflowVersion> versions;
    WorkflowVersion activeVersion;
    Instant createTime;
    Instant updateTime;
  }

  private record DefinitionStateSnapshot(
      String name,
      String description,
      String status,
      String failureStrategy,
      String latestExecutionId,
      String latestExecutionStatus,
      List<WorkflowNodeSpec> nodes,
      List<WorkflowEdgeSpec> edges,
      Map<String, Object> input,
      Map<String, Object> editorMeta,
      long workflowTimeoutSeconds,
      long draftRevision,
      int latestVersionNo,
      WorkflowVersion activeVersion,
      Instant updateTime) {

    static DefinitionStateSnapshot capture(DefinitionState state) {
      return new DefinitionStateSnapshot(
          state.name,
          state.description,
          state.status,
          state.failureStrategy,
          state.latestExecutionId,
          state.latestExecutionStatus,
          state.nodes,
          state.edges,
          state.input,
          state.editorMeta,
          state.workflowTimeoutSeconds,
          state.draftRevision,
          state.latestVersionNo,
          state.activeVersion,
          state.updateTime);
    }

    void restore(DefinitionState state) {
      state.name = name;
      state.description = description;
      state.status = status;
      state.failureStrategy = failureStrategy;
      state.latestExecutionId = latestExecutionId;
      state.latestExecutionStatus = latestExecutionStatus;
      state.nodes = nodes;
      state.edges = edges;
      state.input = input;
      state.editorMeta = editorMeta;
      state.workflowTimeoutSeconds = workflowTimeoutSeconds;
      state.draftRevision = draftRevision;
      state.latestVersionNo = latestVersionNo;
      state.activeVersion = activeVersion;
      state.updateTime = updateTime;
    }
  }
}
