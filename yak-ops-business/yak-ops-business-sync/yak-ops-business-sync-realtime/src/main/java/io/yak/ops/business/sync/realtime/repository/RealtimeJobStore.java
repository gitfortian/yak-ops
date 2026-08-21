package io.yak.ops.business.sync.realtime.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobChangeEvent;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** JDBC persistence adapter for the three realtime-only tables. */
@Repository
public class RealtimeJobStore {

  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final ApplicationEventPublisher events;

  public RealtimeJobStore(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      @Qualifier("realtimeObjectMapper") ObjectMapper json,
      ApplicationEventPublisher events) {
    this.db = new JdbcTemplate(dataSource);
    this.json = json;
    this.events = events;
  }

  public long insertDefinition(
      String name, String description, CdcPipelineSpec spec, String digest) {
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    db.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(
                  "insert into yak_realtime_job_definition"
                      + "(job_name,description,spec_json,config_digest) values(?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
          statement.setString(1, name);
          statement.setString(2, description);
          statement.setString(3, write(spec));
          statement.setString(4, digest);
          return statement;
        },
        keys);
    return Objects.requireNonNull(keys.getKey(), "新增实时任务未返回主键").longValue();
  }

  public void updateDefinition(
      long id, String name, String description, CdcPipelineSpec spec, String digest) {
    int changed =
        db.update(
            "update yak_realtime_job_definition set job_name=?,description=?,spec_json=?,"
                + "config_digest=?,definition_version=definition_version+1,release_state='DRAFT' "
                + "where id=? and desired_state='STOPPED'",
            name,
            description,
            write(spec),
            digest,
            id);
    if (changed != 1) {
      throw new IllegalStateException("运行中的任务不能编辑，或任务不存在");
    }
  }

  public void publish(long id) {
    int changed =
        db.update(
            "update yak_realtime_job_definition set release_state='PUBLISHED',"
                + "published_version=definition_version where id=? and desired_state='STOPPED'",
            id);
    if (changed != 1) {
      throw new IllegalStateException("运行中的任务不能发布，或任务不存在");
    }
  }

  public Optional<DefinitionRow> definition(long id) {
    List<DefinitionRow> rows =
        db.query(
            "select * from yak_realtime_job_definition where id=?",
            (result, row) -> definitionRow(result),
            id);
    return rows.stream().findFirst();
  }

  public DefinitionRow lockDefinition(long id) {
    List<DefinitionRow> rows =
        db.query(
            "select * from yak_realtime_job_definition where id=? for update",
            (result, row) -> definitionRow(result),
            id);
    return rows.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
  }

  public RealtimeJobPage page(int pageNo, int pageSize, String keyword) {
    int normalizedSize = Math.max(1, Math.min(pageSize, 100));
    int normalizedPage = Math.max(1, pageNo);
    String pattern = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    long total =
        pattern == null
            ? Objects.requireNonNull(
                db.queryForObject("select count(*) from yak_realtime_job_definition", Long.class))
            : Objects.requireNonNull(
                db.queryForObject(
                    "select count(*) from yak_realtime_job_definition "
                        + "where job_name like ? or description like ?",
                    Long.class,
                    pattern,
                    pattern));
    String sql =
        "select * from yak_realtime_job_definition "
            + (pattern == null ? "" : "where job_name like ? or description like ? ")
            + "order by update_time desc limit ? offset ?";
    Object[] arguments =
        pattern == null
            ? new Object[] {normalizedSize, (normalizedPage - 1) * normalizedSize}
            : new Object[] {
              pattern, pattern, normalizedSize, (normalizedPage - 1) * normalizedSize
            };
    List<RealtimeJobView> records =
        db.query(sql, (result, row) -> view(definitionRow(result), null), arguments);
    return new RealtimeJobPage(records, total, normalizedPage, normalizedSize);
  }

  public Optional<DeploymentRow> deploymentByIdempotencyKey(String key) {
    return db
        .query(
            "select * from yak_realtime_job_deployment where idempotency_key=?",
            (result, row) -> deploymentRow(result),
            key)
        .stream()
        .findFirst();
  }

  public Optional<DeploymentRow> latestDeployment(long definitionId) {
    return db
        .query(
            "select * from yak_realtime_job_deployment where definition_id=? "
                + "order by id desc limit 1",
            (result, row) -> deploymentRow(result),
            definitionId)
        .stream()
        .findFirst();
  }

  public long insertDeployment(
      DefinitionRow definition,
      CdcPipelineSpec spec,
      String summary,
      String digest,
      String idempotencyKey) {
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    db.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(
                  "insert into yak_realtime_job_deployment"
                      + "(definition_id,definition_version,spec_snapshot_json,pipeline_yaml,"
                      + "spec_summary,config_digest,idempotency_key,status) "
                      + "values(?,?,?,null,?,?,?,'SUBMITTING')",
                  Statement.RETURN_GENERATED_KEYS);
          statement.setLong(1, definition.id());
          statement.setInt(2, definition.definitionVersion());
          statement.setString(3, write(spec));
          statement.setString(4, summary);
          statement.setString(5, digest);
          statement.setString(6, idempotencyKey);
          return statement;
        },
        keys);
    return Objects.requireNonNull(keys.getKey(), "新增部署未返回主键").longValue();
  }

  public void markStarting(long definitionId) {
    db.update(
        "update yak_realtime_job_definition set desired_state='RUNNING',"
            + "observed_state='STARTING',last_error=null where id=?",
        definitionId);
  }

  public void markDeploymentRunning(
      long definitionId, long deploymentId, String engineJobId, String runtimeRevision) {
    db.update(
        "update yak_realtime_job_deployment set"
            + " gateway_job_id=?,runtime_version=?,runtime_revision=?,status='RUNNING',result_uncertain=0,error_message=null"
            + " where id=?",
        engineJobId,
        runtimeRevision,
        runtimeRevision,
        deploymentId);
    db.update(
        "update yak_realtime_job_definition set observed_state='RUNNING',last_error=null where"
            + " id=?",
        definitionId);
  }

  public void markDeployFailure(
      long definitionId, long deploymentId, boolean uncertain, String message) {
    db.update(
        "update yak_realtime_job_deployment set status=?,result_uncertain=?,error_message=? where"
            + " id=?",
        uncertain ? "UNKNOWN" : "FAILED",
        uncertain,
        message,
        deploymentId);
    db.update(
        "update yak_realtime_job_definition set desired_state=?,observed_state=?,last_error=? where"
            + " id=?",
        uncertain ? "RUNNING" : "STOPPED",
        uncertain ? "UNKNOWN" : "FAILED",
        message,
        definitionId);
  }

  public void markStopping(long definitionId, Long deploymentId) {
    db.update(
        "update yak_realtime_job_definition set desired_state='STOPPED',"
            + "observed_state='STOPPING' where id=?",
        definitionId);
    if (deploymentId != null) {
      db.update(
          "update yak_realtime_job_deployment set status='STOPPING' where id=?", deploymentId);
    }
  }

  public void reconcile(
      long definitionId,
      Long deploymentId,
      String observedState,
      String deploymentState,
      String engineJobId,
      String error) {
    db.update(
        "update yak_realtime_job_definition set observed_state=?,last_error=? where id=?",
        observedState,
        error,
        definitionId);
    if (deploymentId != null) {
      db.update(
          "update yak_realtime_job_deployment set status=?,"
              + "gateway_job_id=coalesce(gateway_job_id,?),error_message=? where id=?",
          deploymentState,
          engineJobId,
          error,
          deploymentId);
    }
  }

  public void markTerminalFailure(long definitionId, Long deploymentId, String message) {
    db.update(
        "update yak_realtime_job_definition set desired_state='STOPPED',"
            + "observed_state='FAILED',last_error=? where id=?",
        message,
        definitionId);
    if (deploymentId != null) {
      db.update(
          "update yak_realtime_job_deployment set status='FAILED',error_message=? where id=?",
          message,
          deploymentId);
    }
  }

  public List<DefinitionRow> desiredJobs() {
    return db.query(
        "select * from yak_realtime_job_definition where desired_state='RUNNING' or observed_state"
            + " in ('STARTING','STOPPING','UNKNOWN','CONFLICT') order by id",
        (result, row) -> definitionRow(result));
  }

  public boolean hasOtherDesiredRunning(long id) {
    return !db.queryForList(
            "select id from yak_realtime_job_definition "
                + "where id<>? and desired_state='RUNNING' for update",
            Long.class,
            id)
        .isEmpty();
  }

  public void delete(long id) {
    int changed =
        db.update(
            "delete from yak_realtime_job_definition where id=? and desired_state='STOPPED'", id);
    if (changed != 1) {
      throw new IllegalStateException("运行中的任务不能删除，或任务不存在");
    }
  }

  public void event(
      long definitionId, Long deploymentId, String type, String from, String to, String message) {
    db.update(
        "insert into yak_realtime_job_event"
            + "(definition_id,deployment_id,event_type,from_state,to_state,message) "
            + "values(?,?,?,?,?,?)",
        definitionId,
        deploymentId,
        type,
        from,
        to,
        message);
    events.publishEvent(new RealtimeJobChangeEvent(definitionId, type, from, to, message));
  }

  public boolean tryAcquireReconcileLease(String owner, int leaseSeconds) {
    int changed =
        db.update(
            "update yak_realtime_runtime_lease set lease_owner=?,"
                + "lease_until=timestampadd(second,?,current_timestamp(3)) where id=1 "
                + "and (lease_until is null or lease_until<current_timestamp(3) or lease_owner=?)",
            owner,
            Math.max(5, leaseSeconds),
            owner);
    return changed == 1;
  }

  public List<RealtimeJobEventView> events(long definitionId) {
    return db.query(
        "select id,deployment_id,event_type,from_state,to_state,message,create_time "
            + "from yak_realtime_job_event where definition_id=? order by id desc limit 200",
        (result, row) ->
            new RealtimeJobEventView(
                result.getLong("id"),
                result.getObject("deployment_id", Long.class),
                result.getString("event_type"),
                result.getString("from_state"),
                result.getString("to_state"),
                result.getString("message"),
                time(result.getTimestamp("create_time"))),
        definitionId);
  }

  public RealtimeJobView view(long id) {
    DefinitionRow definition =
        definition(id).orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    return view(definition, latestDeployment(id).orElse(null));
  }

  public CdcPipelineSpec spec(DefinitionRow definition) {
    return read(definition.specJson());
  }

  public RealtimeJobView.Deployment deploymentView(DeploymentRow deployment) {
    return deployment == null
        ? null
        : new RealtimeJobView.Deployment(
            deployment.id(),
            deployment.definitionVersion(),
            deployment.specSummary(),
            deployment.configDigest(),
            deployment.idempotencyKey(),
            deployment.engineJobId(),
            deployment.runtimeRevision(),
            deployment.status(),
            deployment.resultUncertain(),
            deployment.errorMessage(),
            deployment.createTime(),
            deployment.updateTime());
  }

  private RealtimeJobView view(DefinitionRow definition, DeploymentRow deployment) {
    return new RealtimeJobView(
        definition.id(),
        definition.name(),
        definition.description(),
        read(definition.specJson()),
        definition.releaseState(),
        definition.desiredState(),
        definition.observedState(),
        definition.definitionVersion(),
        definition.publishedVersion(),
        definition.configDigest(),
        definition.lastError(),
        definition.createTime(),
        definition.updateTime(),
        deploymentView(deployment));
  }

  private DefinitionRow definitionRow(java.sql.ResultSet result) throws java.sql.SQLException {
    return new DefinitionRow(
        result.getLong("id"),
        result.getString("job_name"),
        result.getString("description"),
        result.getString("spec_json"),
        result.getString("release_state"),
        result.getString("desired_state"),
        result.getString("observed_state"),
        result.getInt("definition_version"),
        result.getObject("published_version", Integer.class),
        result.getString("config_digest"),
        result.getString("last_error"),
        time(result.getTimestamp("create_time")),
        time(result.getTimestamp("update_time")));
  }

  private DeploymentRow deploymentRow(java.sql.ResultSet result) throws java.sql.SQLException {
    return new DeploymentRow(
        result.getLong("id"),
        result.getLong("definition_id"),
        result.getInt("definition_version"),
        result.getString("spec_snapshot_json"),
        result.getString("spec_summary"),
        result.getString("config_digest"),
        result.getString("idempotency_key"),
        result.getString("gateway_job_id"),
        result.getString("runtime_revision"),
        result.getString("status"),
        result.getBoolean("result_uncertain"),
        result.getString("error_message"),
        time(result.getTimestamp("create_time")),
        time(result.getTimestamp("update_time")));
  }

  private LocalDateTime time(Timestamp value) {
    return value == null ? null : value.toLocalDateTime();
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("无法序列化实时同步 Spec", exception);
    }
  }

  private CdcPipelineSpec read(String value) {
    try {
      return json.readValue(value, CdcPipelineSpec.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("实时同步 Spec 无效", exception);
    }
  }

  public record DefinitionRow(
      long id,
      String name,
      String description,
      String specJson,
      String releaseState,
      String desiredState,
      String observedState,
      int definitionVersion,
      Integer publishedVersion,
      String configDigest,
      String lastError,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}

  public record DeploymentRow(
      long id,
      long definitionId,
      int definitionVersion,
      String specSnapshotJson,
      String specSummary,
      String configDigest,
      String idempotencyKey,
      String engineJobId,
      String runtimeRevision,
      String status,
      boolean resultUncertain,
      String errorMessage,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}
}
