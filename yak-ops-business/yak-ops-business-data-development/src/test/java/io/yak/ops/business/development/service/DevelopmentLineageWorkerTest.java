package io.yak.ops.business.development.service;

import static org.mockito.Mockito.*;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DevelopmentLineageWorkerTest {
  @Test
  void preparesOutsideWriterAndCompletesOnlyAfterSuccessfulWrite() {
    DevelopmentLineageOutbox outbox = mock(DevelopmentLineageOutbox.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    DevelopmentTaskRevisionRepository revisions = mock(DevelopmentTaskRevisionRepository.class);
    DevelopmentSqlLineageService lineage = mock(DevelopmentSqlLineageService.class);
    DevelopmentLineageWriteTransaction writer = mock(DevelopmentLineageWriteTransaction.class);
    DevelopmentLineageOutbox.Task task = new DevelopmentLineageOutbox.Task("task", 1, 11, 0);
    DevelopmentNode node = node();
    DevelopmentTaskRevision revision = revision(11, 1);
    DevelopmentSqlLineageService.PreparedLineage prepared = mock(DevelopmentSqlLineageService.PreparedLineage.class);
    when(outbox.claim(task)).thenReturn(true);
    when(nodes.findById(1L)).thenReturn(Optional.of(node));
    when(revisions.findById(11L)).thenReturn(Optional.of(revision));
    when(lineage.prepare(node, revision)).thenReturn(prepared);

    new DevelopmentLineageWorker(outbox, nodes, revisions, lineage, writer).process(task);

    var order = inOrder(lineage, writer, outbox);
    order.verify(lineage).prepare(node, revision);
    order.verify(writer).writeIfLatest(node, revision, prepared);
    order.verify(outbox).complete(task);
  }

  @Test
  void failureIsRetriedWithoutEscapingWorker() {
    DevelopmentLineageOutbox outbox = mock(DevelopmentLineageOutbox.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    DevelopmentLineageOutbox.Task task = new DevelopmentLineageOutbox.Task("task", 1, 11, 2);
    RuntimeException failure = new RuntimeException("catalog unavailable");
    when(outbox.claim(task)).thenReturn(true);
    when(nodes.findById(1L)).thenThrow(failure);

    new DevelopmentLineageWorker(outbox, nodes, mock(DevelopmentTaskRevisionRepository.class),
        mock(DevelopmentSqlLineageService.class), mock(DevelopmentLineageWriteTransaction.class))
        .process(task);

    verify(outbox).fail(task, failure);
    verify(outbox, never()).complete(task);
  }

  private static DevelopmentNode node() {
    return new DevelopmentNode(1L, "sql", "SQL", null, null, true, Instant.now(), Instant.now());
  }

  private static DevelopmentTaskRevision revision(long id, int number) {
    return new DevelopmentTaskRevision(id, 1L, number, number,
        new TaskDefinition("SQL", 1, "insert into b select * from a", "{\"dataSourceId\":\"1\"}"),
        "checksum", Instant.now());
  }
}
