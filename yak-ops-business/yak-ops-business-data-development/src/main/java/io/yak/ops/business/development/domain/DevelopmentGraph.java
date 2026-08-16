package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/** Project-scoped graph document containing only canvas layout and dependency topology. */
public record DevelopmentGraph(
    @JsonSerialize(using = ToStringSerializer.class) Long projectId,
    List<NodeLayout> nodes,
    List<Edge> edges,
    Instant updateTime) {

  public DevelopmentGraph {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    edges = edges == null ? List.of() : List.copyOf(edges);
  }

  public static DevelopmentGraph empty(Long projectId) {
    return new DevelopmentGraph(projectId, List.of(), List.of(), null);
  }

  public record NodeLayout(
      @JsonSerialize(using = ToStringSerializer.class) Long nodeId,
      double x,
      double y) {
  }

  public record Edge(
      @JsonSerialize(using = ToStringSerializer.class) Long sourceNodeId,
      @JsonSerialize(using = ToStringSerializer.class) Long targetNodeId) {
  }
}
