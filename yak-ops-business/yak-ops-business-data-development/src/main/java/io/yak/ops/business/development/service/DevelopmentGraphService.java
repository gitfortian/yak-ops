package io.yak.ops.business.development.service;

import io.yak.ops.business.development.domain.DevelopmentGraph;
import io.yak.ops.business.development.domain.DevelopmentGraph.Edge;
import io.yak.ops.business.development.domain.DevelopmentGraph.NodeLayout;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentNodeConnectionPolicy;
import io.yak.ops.business.development.domain.DevelopmentNodeType;
import io.yak.ops.business.development.repository.DevelopmentGraphRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the lightweight data-development DAG layout and topology lifecycle. */
@Service
public class DevelopmentGraphService {

  private final DevelopmentGraphRepository graphRepository;
  private final DevelopmentNodeRepository nodeRepository;

  public DevelopmentGraphService(
      DevelopmentGraphRepository graphRepository,
      DevelopmentNodeRepository nodeRepository) {
    this.graphRepository = graphRepository;
    this.nodeRepository = nodeRepository;
  }

  public DevelopmentGraph get(Long projectId) {
    Long scopeProjectId = normalizeProjectId(projectId);
    DevelopmentGraph graph = graphRepository.findByProjectId(scopeProjectId)
        .orElseGet(() -> DevelopmentGraph.empty(scopeProjectId));
    return sanitize(scopeProjectId, graph);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentGraph save(
      Long projectId,
      List<NodeLayout> layouts,
      List<Edge> edges) {
    Long scopeProjectId = normalizeProjectId(projectId);
    List<NodeLayout> normalizedLayouts = normalizeLayouts(
        scopeProjectId,
        layouts == null ? List.of() : layouts);
    List<Edge> normalizedEdges = normalizeEdges(
        scopeProjectId,
        normalizedLayouts,
        edges == null ? List.of() : edges);
    ensureAcyclic(normalizedLayouts, normalizedEdges);
    return graphRepository.save(
        new DevelopmentGraph(scopeProjectId, normalizedLayouts, normalizedEdges, null));
  }

  private DevelopmentGraph sanitize(Long projectId, DevelopmentGraph graph) {
    Map<Long, DevelopmentNode> availableNodes = nodesForProject(projectId);
    Map<Long, NodeLayout> layoutByNodeId = new LinkedHashMap<>();
    for (NodeLayout layout : graph.nodes()) {
      if (!validLayout(layout) || !availableNodes.containsKey(layout.nodeId())) continue;
      layoutByNodeId.putIfAbsent(layout.nodeId(), layout);
    }

    List<Edge> acceptedEdges = new ArrayList<>();
    Set<String> edgeKeys = new HashSet<>();
    for (Edge edge : graph.edges()) {
      if (!validEdgeShape(edge)) continue;
      if (!layoutByNodeId.containsKey(edge.sourceNodeId())
          || !layoutByNodeId.containsKey(edge.targetNodeId())) {
        continue;
      }
      DevelopmentNode source = availableNodes.get(edge.sourceNodeId());
      DevelopmentNode target = availableNodes.get(edge.targetNodeId());
      if (!connectionAllowed(source, target)) continue;
      String key = edgeKey(edge);
      if (!edgeKeys.add(key)) continue;
      List<Edge> candidate = new ArrayList<>(acceptedEdges);
      candidate.add(edge);
      if (hasCycle(layoutByNodeId.keySet(), candidate)) continue;
      acceptedEdges.add(edge);
    }

    List<NodeLayout> sanitizedLayouts = layoutByNodeId.values().stream()
        .sorted(Comparator.comparing(NodeLayout::nodeId))
        .toList();
    List<Edge> sanitizedEdges = acceptedEdges.stream()
        .sorted(Comparator
            .comparing(Edge::sourceNodeId)
            .thenComparing(Edge::targetNodeId))
        .toList();
    return new DevelopmentGraph(projectId, sanitizedLayouts, sanitizedEdges, graph.updateTime());
  }

  private List<NodeLayout> normalizeLayouts(
      Long projectId,
      List<NodeLayout> layouts) {
    Map<Long, DevelopmentNode> availableNodes = nodesForProject(projectId);
    Map<Long, NodeLayout> normalized = new LinkedHashMap<>();
    for (NodeLayout layout : layouts) {
      if (!validLayout(layout)) {
        throw new IllegalArgumentException("DAG 节点布局无效");
      }
      if (!availableNodes.containsKey(layout.nodeId())) {
        throw new IllegalArgumentException("DAG 节点不存在或不属于当前项目：" + layout.nodeId());
      }
      if (normalized.putIfAbsent(layout.nodeId(), layout) != null) {
        throw new IllegalArgumentException("DAG 节点布局重复：" + layout.nodeId());
      }
    }
    return normalized.values().stream()
        .sorted(Comparator.comparing(NodeLayout::nodeId))
        .toList();
  }

  private List<Edge> normalizeEdges(
      Long projectId,
      List<NodeLayout> layouts,
      List<Edge> edges) {
    Map<Long, DevelopmentNode> availableNodes = nodesForProject(projectId);
    Set<Long> layoutNodeIds = layouts.stream()
        .map(NodeLayout::nodeId)
        .collect(java.util.stream.Collectors.toSet());
    Set<String> edgeKeys = new HashSet<>();
    List<Edge> normalized = new ArrayList<>();

    for (Edge edge : edges) {
      if (!validEdgeShape(edge)) {
        throw new IllegalArgumentException("DAG 连线无效");
      }
      if (!layoutNodeIds.contains(edge.sourceNodeId())
          || !layoutNodeIds.contains(edge.targetNodeId())) {
        throw new IllegalArgumentException("DAG 连线端点不在当前画布中");
      }
      DevelopmentNode source = availableNodes.get(edge.sourceNodeId());
      DevelopmentNode target = availableNodes.get(edge.targetNodeId());
      if (source == null || target == null) {
        throw new IllegalArgumentException("DAG 连线节点不存在或不属于当前项目");
      }
      if (!connectionAllowed(source, target)) {
        throw new IllegalArgumentException(
            "不允许的数据开发 DAG 连线：" + source.type() + " -> " + target.type());
      }
      if (!edgeKeys.add(edgeKey(edge))) {
        throw new IllegalArgumentException("DAG 连线重复");
      }
      normalized.add(edge);
    }

    return normalized.stream()
        .sorted(Comparator
            .comparing(Edge::sourceNodeId)
            .thenComparing(Edge::targetNodeId))
        .toList();
  }

  private Map<Long, DevelopmentNode> nodesForProject(Long projectId) {
    Map<Long, DevelopmentNode> result = new HashMap<>();
    for (DevelopmentNode node : nodeRepository.list()) {
      if (Objects.equals(normalizeProjectId(node.projectId()), projectId)) {
        result.put(node.id(), node);
      }
    }
    return result;
  }

  private boolean connectionAllowed(DevelopmentNode source, DevelopmentNode target) {
    if (source == null || target == null || Objects.equals(source.id(), target.id())) return false;
    DevelopmentNodeType sourceType = DevelopmentNodeType.tryParse(source.type()).orElse(null);
    DevelopmentNodeType targetType = DevelopmentNodeType.tryParse(target.type()).orElse(null);
    return DevelopmentNodeConnectionPolicy.canConnect(sourceType, targetType);
  }

  private boolean validLayout(NodeLayout layout) {
    return layout != null
        && layout.nodeId() != null
        && layout.nodeId() > 0L
        && Double.isFinite(layout.x())
        && Double.isFinite(layout.y());
  }

  private boolean validEdgeShape(Edge edge) {
    return edge != null
        && edge.sourceNodeId() != null
        && edge.sourceNodeId() > 0L
        && edge.targetNodeId() != null
        && edge.targetNodeId() > 0L
        && !Objects.equals(edge.sourceNodeId(), edge.targetNodeId());
  }

  private String edgeKey(Edge edge) {
    return edge.sourceNodeId() + "->" + edge.targetNodeId();
  }

  private void ensureAcyclic(
      List<NodeLayout> layouts,
      List<Edge> edges) {
    Set<Long> nodeIds = layouts.stream()
        .map(NodeLayout::nodeId)
        .collect(java.util.stream.Collectors.toSet());
    if (hasCycle(nodeIds, edges)) {
      throw new IllegalArgumentException("数据开发 DAG 不能形成循环依赖");
    }
  }

  private boolean hasCycle(Set<Long> nodeIds, List<Edge> edges) {
    Map<Long, Integer> indegree = new HashMap<>();
    Map<Long, List<Long>> outgoing = new HashMap<>();
    nodeIds.forEach(nodeId -> indegree.put(nodeId, 0));
    for (Edge edge : edges) {
      outgoing.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>())
          .add(edge.targetNodeId());
      indegree.computeIfPresent(edge.targetNodeId(), (ignored, value) -> value + 1);
    }

    Queue<Long> queue = new ArrayDeque<>();
    indegree.forEach((nodeId, value) -> {
      if (value == 0) queue.add(nodeId);
    });
    int visited = 0;
    while (!queue.isEmpty()) {
      Long source = queue.remove();
      visited++;
      for (Long target : outgoing.getOrDefault(source, List.of())) {
        Integer next = indegree.computeIfPresent(target, (ignored, value) -> value - 1);
        if (next != null && next == 0) queue.add(target);
      }
    }
    return visited != nodeIds.size();
  }

  private Long normalizeProjectId(Long projectId) {
    return projectId == null || projectId <= 0L ? null : projectId;
  }
}
