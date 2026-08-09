package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.EdgeRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.NodeRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowStartGraphCompilerTest {

  @Test
  void shouldOnlyCompileNodesReachableFromExplicitStartConnections() {
    List<NodeRequest> nodes = List.of(
        node("a", "task-a"),
        node("b", "task-b"),
        node("c", "task-c"));
    List<EdgeRequest> edges = List.of(new EdgeRequest("a", "c"));
    Map<String, Object> input = startInput(List.of("a"));

    WorkflowStartGraphCompiler.RuntimeGraph runtimeGraph =
        WorkflowStartGraphCompiler.compile(nodes, edges, input);

    assertThat(runtimeGraph.nodes()).extracting(NodeRequest::id).containsExactly("a", "c");
    assertThat(runtimeGraph.edges())
        .extracting(edge -> edge.source() + "->" + edge.target())
        .containsExactly("a->c");
  }

  @Test
  void shouldKeepLegacyDefinitionsUsingAllTaskRoots() {
    List<NodeRequest> nodes = List.of(
        node("a", "task-a"),
        node("b", "task-b"),
        node("c", "task-c"));
    List<EdgeRequest> edges = List.of(new EdgeRequest("a", "c"));

    WorkflowStartGraphCompiler.RuntimeGraph runtimeGraph =
        WorkflowStartGraphCompiler.compile(nodes, edges, Map.of());

    assertThat(runtimeGraph.nodes()).extracting(NodeRequest::id).containsExactly("a", "b", "c");
    assertThat(runtimeGraph.edges()).hasSize(1);
  }

  @Test
  void shouldRejectExplicitStartWithoutAnyConnectedTask() {
    assertThatThrownBy(() -> WorkflowStartGraphCompiler.compile(
        List.of(node("a", "task-a")),
        List.of(),
        startInput(List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("至少需要连接一个任务节点");
  }

  @Test
  void shouldRejectStartConnectionToNonRootTask() {
    List<NodeRequest> nodes = List.of(node("a", "task-a"), node("b", "task-b"));
    List<EdgeRequest> edges = List.of(new EdgeRequest("a", "b"));

    assertThatThrownBy(() -> WorkflowStartGraphCompiler.compile(
        nodes,
        edges,
        startInput(List.of("b"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("只能连接没有前置任务的根节点");
  }

  private Map<String, Object> startInput(List<String> nextNodeIds) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("__yak_start__", Map.of(
        "version", 2,
        "nextNodeIds", nextNodeIds));
    return input;
  }

  private NodeRequest node(String id, String taskId) {
    return new NodeRequest(
        id,
        taskId,
        0D,
        0D,
        1,
        0L,
        0L,
        0L,
        Map.of(),
        "ALL_SUCCESS",
        "FAIL_WORKFLOW");
  }
}
