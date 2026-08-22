package io.yak.ops.business.sync.realtime.repository;

import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists the deterministic Flink runtime name separately from the user-facing task name. */
@Repository
public class RealtimeRuntimeIdentityStore {

  private final JdbcTemplate db;

  public RealtimeRuntimeIdentityStore(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    this.db = new JdbcTemplate(dataSource);
  }

  /** Must succeed before the Flink CDC CLI is started. */
  public void bind(String idempotencyKey, String runtimeJobName) {
    int changed =
        db.update(
            "update yak_realtime_job_deployment set runtime_job_name=?,runtime_identity_state='BOUND' "
                + "where idempotency_key=? and gateway_job_id is null "
                + "and runtime_identity_state='REQUIRED' "
                + "and (runtime_job_name is null or runtime_job_name=?)",
            runtimeJobName,
            idempotencyKey,
            runtimeJobName);
    if (changed != 1) {
      throw new IllegalStateException("无法绑定实时同步 runtime job identity，部署状态可能已变化");
    }
  }

  public Optional<String> findByDeploymentId(long deploymentId) {
    return db
        .query(
            "select runtime_job_name from yak_realtime_job_deployment where id=?",
            (result, row) -> result.getString("runtime_job_name"),
            deploymentId)
        .stream()
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
  }
}
