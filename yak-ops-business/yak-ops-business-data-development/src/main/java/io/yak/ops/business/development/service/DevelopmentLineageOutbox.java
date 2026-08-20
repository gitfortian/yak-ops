package io.yak.ops.business.development.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable SQL-lineage work committed atomically with the published revision. */
@Repository
public class DevelopmentLineageOutbox {
  private final JdbcTemplate jdbc;

  public DevelopmentLineageOutbox(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void enqueue(long nodeId, long revisionId) {
    jdbc.update("INSERT IGNORE INTO yak_dev_lineage_outbox "
        + "(task_id,node_id,revision_id,status,attempts,next_attempt_time,create_time,update_time) "
        + "VALUES (?,?,?,'PENDING',0,NOW(6),NOW(6),NOW(6))",
        UUID.randomUUID().toString(), nodeId, revisionId);
  }

  public List<Task> due(int limit) {
    return jdbc.query("SELECT task_id,node_id,revision_id,attempts FROM yak_dev_lineage_outbox "
            + "WHERE (status IN ('PENDING','FAILED') AND next_attempt_time<=NOW(6)) "
            + "OR (status='RUNNING' AND update_time<DATE_SUB(NOW(6), INTERVAL 10 MINUTE)) "
            + "ORDER BY create_time LIMIT ?",
        (rs, row) -> new Task(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getInt(4)), limit);
  }

  public boolean claim(Task task) {
    return jdbc.update("UPDATE yak_dev_lineage_outbox SET status='RUNNING',attempts=attempts+1,"
            + "update_time=NOW(6) WHERE task_id=? AND revision_id=? AND (status IN ('PENDING','FAILED') "
            + "OR (status='RUNNING' AND update_time<DATE_SUB(NOW(6), INTERVAL 10 MINUTE)))",
        task.taskId(), task.revisionId()) == 1;
  }

  public void complete(Task task) {
    jdbc.update("UPDATE yak_dev_lineage_outbox SET status='SUCCEEDED',last_error=NULL,update_time=NOW(6) "
        + "WHERE task_id=? AND revision_id=? AND status='RUNNING'", task.taskId(), task.revisionId());
  }

  public void fail(Task task, Throwable failure) {
    long delay = Math.min(3600, 1L << Math.min(12, task.attempts()));
    String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    jdbc.update("UPDATE yak_dev_lineage_outbox SET status='FAILED',last_error=?,"
            + "next_attempt_time=DATE_ADD(NOW(6), INTERVAL ? SECOND),update_time=NOW(6) "
            + "WHERE task_id=? AND revision_id=? AND status='RUNNING'",
        message.substring(0, Math.min(2000, message.length())), delay, task.taskId(), task.revisionId());
  }

  public record Task(String taskId, long nodeId, long revisionId, int attempts) {}
}
