package io.yak.ops.business.development.service;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the durable outbox; parsing/catalog IO deliberately happens before the write transaction. */
@Component
public class DevelopmentLineageWorker {
  private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentLineageWorker.class);
  private final DevelopmentLineageOutbox outbox;
  private final DevelopmentNodeRepository nodes;
  private final DevelopmentTaskRevisionRepository revisions;
  private final DevelopmentSqlLineageService lineage;
  private final DevelopmentLineageWriteTransaction writer;

  public DevelopmentLineageWorker(DevelopmentLineageOutbox outbox, DevelopmentNodeRepository nodes,
      DevelopmentTaskRevisionRepository revisions, DevelopmentSqlLineageService lineage,
      DevelopmentLineageWriteTransaction writer) {
    this.outbox = outbox;
    this.nodes = nodes;
    this.revisions = revisions;
    this.lineage = lineage;
    this.writer = writer;
  }

  @Scheduled(fixedDelayString = "${yak.development.lineage-outbox.poll-delay-ms:1000}")
  public void poll() {
    for (DevelopmentLineageOutbox.Task task : outbox.due(20)) process(task);
  }

  void process(DevelopmentLineageOutbox.Task task) {
    if (!outbox.claim(task)) return;
    try {
      DevelopmentNode node = nodes.findById(task.nodeId()).orElseThrow();
      DevelopmentTaskRevision revision = revisions.findById(task.revisionId()).orElseThrow();
      DevelopmentSqlLineageService.PreparedLineage prepared = lineage.prepare(node, revision);
      writer.writeIfLatest(node, revision, prepared);
      outbox.complete(task);
    } catch (Throwable failure) {
      outbox.fail(task, failure);
      LOGGER.warn("SQL lineage outbox task {} failed; publish remains committed", task.taskId(), failure);
    }
  }
}
