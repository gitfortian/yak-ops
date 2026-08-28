package io.yak.ops.business.development.lineage;

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
    jdbc.update(
        "INSERT IGNORE INTO yak_dev_lineage_outbox "
            + "(task_id,project_id,node_id,revision_id,status,attempts,next_attempt_time,create_time,update_time) "
            + "SELECT ?,project_id,id,?,'PENDING',0,NOW(6),NOW(6),NOW(6) FROM yak_dev_node "
            + "WHERE id=? AND project_id IS NOT NULL",
        UUID.randomUUID().toString(),
        revisionId,
        nodeId);
  }

  /** Cross-project dispatcher query; each returned task restores its project before processing. */
  public List<Task> due(int limit) {
    return jdbc.query(
        "SELECT task_id,project_id,node_id,revision_id,attempts FROM yak_dev_lineage_outbox "
            + "WHERE project_id IS NOT NULL AND ((status IN ('PENDING','FAILED') AND next_attempt_time<=NOW(6)) "
            + "OR (status='RUNNING' AND update_time<DATE_SUB(NOW(6), INTERVAL 10 MINUTE))) "
            + "ORDER BY create_time LIMIT ?",
        (rs, row) ->
            new Task(
                rs.getString("task_id"),
                rs.getLong("project_id"),
                rs.getLong("node_id"),
                rs.getLong("revision_id"),
                rs.getInt("attempts")),
        limit);
  }

  public boolean claim(Task task) {
    return jdbc.update(
        "UPDATE yak_dev_lineage_outbox SET status='RUNNING',attempts=attempts+1,"
            + "update_time=NOW(6) WHERE task_id=? AND project_id=? AND revision_id=? "
            + "AND (status IN ('PENDING','FAILED') "
            + "OR (status='RUNNING' AND update_time<DATE_SUB(NOW(6), INTERVAL 10 MINUTE)))",
        task.taskId(),
        task.projectId(),
        task.revisionId()) == 1;
  }

  public void complete(Task task) {
    jdbc.update(
        "UPDATE yak_dev_lineage_outbox SET status='SUCCEEDED',last_error=NULL,update_time=NOW(6) "
            + "WHERE task_id=? AND project_id=? AND revision_id=? AND status='RUNNING'",
        task.taskId(),
        task.projectId(),
        task.revisionId());
  }

  public void fail(Task task, Throwable failure) {
    long delay = Math.min(3600, 1L << Math.min(12, task.attempts()));
    String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    jdbc.update(
        "UPDATE yak_dev_lineage_outbox SET status='FAILED',last_error=?,"
            + "next_attempt_time=DATE_ADD(NOW(6), INTERVAL ? SECOND),update_time=NOW(6) "
            + "WHERE task_id=? AND project_id=? AND revision_id=? AND status='RUNNING'",
        message.substring(0, Math.min(2000, message.length())),
        delay,
        task.taskId(),
        task.projectId(),
        task.revisionId());
  }

  public record Task(String taskId, Long projectId, long nodeId, long revisionId, int attempts) {
    public Task {
      if (projectId == null || projectId <= 0L) {
        throw new IllegalArgumentException("lineage outbox projectId must be positive");
      }
    }
  }
}
