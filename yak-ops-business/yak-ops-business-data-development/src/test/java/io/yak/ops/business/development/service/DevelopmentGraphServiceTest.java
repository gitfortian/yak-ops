package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.domain.DevelopmentGraph;
import io.yak.ops.business.development.domain.DevelopmentGraph.Edge;
import io.yak.ops.business.development.domain.DevelopmentGraph.NodeLayout;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentGraphRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DevelopmentGraphServiceTest {

  @Test
  void savesAllowedSqlDatasetAndDataServiceTopology() {
    DevelopmentGraphRepository graphs = mock(DevelopmentGraphRepository.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    when(nodes.list()).thenReturn(List.of(
        node(1L, "SQL"), node(2L, "DATASET"), node(3L, "DATA_SERVICE")));
    when(graphs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    DevelopmentGraphService service = new DevelopmentGraphService(graphs, nodes);

    DevelopmentGraph saved = service.save(
        9L,
        List.of(layout(1L, 10, 20), layout(2L, 300, 20), layout(3L, 590, 20)),
        List.of(edge(1L, 2L), edge(2L, 3L)));

    assertEquals(3, saved.nodes().size());
    assertEquals(2, saved.edges().size());
  }

  @Test
  void rejectsConnectionThatViolatesNodePolicy() {
    DevelopmentGraphRepository graphs = mock(DevelopmentGraphRepository.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    when(nodes.list()).thenReturn(List.of(node(1L, "SQL"), node(2L, "DATASET")));
    DevelopmentGraphService service = new DevelopmentGraphService(graphs, nodes);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(
            9L,
            List.of(layout(1L, 0, 0), layout(2L, 200, 0)),
            List.of(edge(2L, 1L))));
  }

  @Test
  void rejectsCyclesEvenWhenEveryIndividualEdgeIsAllowed() {
    DevelopmentGraphRepository graphs = mock(DevelopmentGraphRepository.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    when(nodes.list()).thenReturn(List.of(node(1L, "SQL"), node(2L, "SQL")));
    DevelopmentGraphService service = new DevelopmentGraphService(graphs, nodes);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(
            9L,
            List.of(layout(1L, 0, 0), layout(2L, 200, 0)),
            List.of(edge(1L, 2L), edge(2L, 1L))));
  }

  @Test
  void ignoresStaleGraphEntriesWhenAResourceWasDeleted() {
    DevelopmentGraphRepository graphs = mock(DevelopmentGraphRepository.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    when(nodes.list()).thenReturn(List.of(node(1L, "SQL")));
    when(graphs.findByProjectId(9L)).thenReturn(Optional.of(new DevelopmentGraph(
        9L,
        List.of(layout(1L, 0, 0), layout(2L, 200, 0)),
        List.of(edge(1L, 2L)),
        Instant.now())));
    DevelopmentGraphService service = new DevelopmentGraphService(graphs, nodes);

    DevelopmentGraph hydrated = service.get(9L);
    assertEquals(List.of(layout(1L, 0, 0)), hydrated.nodes());
    assertEquals(List.of(), hydrated.edges());
  }

  private DevelopmentNode node(Long id, String type) {
    Instant now = Instant.now();
    return new DevelopmentNode(id, type + " " + id, type, 9L, null, false, now, now, null, false);
  }

  private NodeLayout layout(Long id, double x, double y) {
    return new NodeLayout(id, x, y);
  }

  private Edge edge(Long source, Long target) {
    return new Edge(source, target);
  }
}
