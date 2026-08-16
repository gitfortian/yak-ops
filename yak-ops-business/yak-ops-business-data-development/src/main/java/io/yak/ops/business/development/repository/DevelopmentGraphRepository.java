package io.yak.ops.business.development.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentGraph;
import io.yak.ops.business.development.domain.DevelopmentGraph.Edge;
import io.yak.ops.business.development.domain.DevelopmentGraph.NodeLayout;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable repository for the lightweight project-scoped DAG document. */
@Repository
public class DevelopmentGraphRepository {

  private static final long DEFAULT_PROJECT_ID = 0L;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public DevelopmentGraphRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<DevelopmentGraph> findByProjectId(Long projectId) {
    List<DevelopmentGraph> values = jdbcTemplate.query(
        "SELECT project_id, graph_json, update_time FROM yak_dev_graph WHERE project_id = ?",
        (rs, rowNum) -> {
          GraphPayload payload = readPayload(rs.getString("graph_json"));
          Timestamp updateTime = rs.getTimestamp("update_time");
          return new DevelopmentGraph(
              fromStoredProjectId(rs.getLong("project_id")),
              payload.nodes(),
              payload.edges(),
              updateTime == null ? null : updateTime.toInstant());
        },
        toStoredProjectId(projectId));
    return values.stream().findFirst();
  }

  public DevelopmentGraph save(DevelopmentGraph graph) {
    long storedProjectId = toStoredProjectId(graph.projectId());
    Instant now = Instant.now();
    String graphJson = writePayload(graph);
    jdbcTemplate.update(
        "INSERT INTO yak_dev_graph(project_id, graph_json, create_time, update_time) "
            + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE graph_json = VALUES(graph_json), "
            + "update_time = VALUES(update_time)",
        storedProjectId,
        graphJson,
        Timestamp.from(now),
        Timestamp.from(now));
    return new DevelopmentGraph(graph.projectId(), graph.nodes(), graph.edges(), now);
  }

  private String writePayload(DevelopmentGraph graph) {
    try {
      return objectMapper.writeValueAsString(new GraphPayload(graph.nodes(), graph.edges()));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化数据开发 DAG 失败", exception);
    }
  }

  private GraphPayload readPayload(String value) {
    if (value == null || value.isBlank()) return new GraphPayload(List.of(), List.of());
    try {
      return objectMapper.readValue(value, GraphPayload.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取数据开发 DAG 失败", exception);
    }
  }

  private long toStoredProjectId(Long projectId) {
    return projectId == null || projectId <= 0L ? DEFAULT_PROJECT_ID : projectId;
  }

  private Long fromStoredProjectId(long projectId) {
    return projectId == DEFAULT_PROJECT_ID ? null : projectId;
  }

  private record GraphPayload(List<NodeLayout> nodes, List<Edge> edges) {
    private GraphPayload {
      nodes = nodes == null ? List.of() : List.copyOf(nodes);
      edges = edges == null ? List.of() : List.copyOf(edges);
    }
  }
}
