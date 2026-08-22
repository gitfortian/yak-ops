package io.yak.ops.business.sync.realtime.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** JDBC persistence adapter for compute runtime environments. */
@Repository
public class ComputeEnvironmentStore {

  private final JdbcTemplate db;
  private final ObjectMapper json;

  public ComputeEnvironmentStore(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      @Qualifier("realtimeObjectMapper") ObjectMapper json) {
    this.db = new JdbcTemplate(dataSource);
    this.json = json;
  }

  public long count() {
    Long value = db.queryForObject("select count(*) from yak_compute_environment", Long.class);
    return value == null ? 0 : value;
  }

  public List<ComputeEnvironment> list() {
    return db.query(
        "select * from yak_compute_environment order by is_default desc, enabled desc, update_time desc, id desc",
        (result, row) -> map(result));
  }

  public Optional<ComputeEnvironment> find(long id) {
    return db.query(
            "select * from yak_compute_environment where id=?", (result, row) -> map(result), id)
        .stream()
        .findFirst();
  }

  public Optional<ComputeEnvironment> defaultEnvironment() {
    return db.query(
            "select * from yak_compute_environment where is_default=1 and enabled=1 order by id limit 1",
            (result, row) -> map(result))
        .stream()
        .findFirst();
  }

  public long insert(
      String name,
      String engineType,
      String deploymentMode,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean defaultEnvironment) {
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    db.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(
                  "insert into yak_compute_environment"
                      + "(name,engine_type,deployment_mode,submitter_type,config_json,enabled,is_default) "
                      + "values(?,?,?,?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
          statement.setString(1, name);
          statement.setString(2, engineType);
          statement.setString(3, deploymentMode);
          statement.setString(4, submitterType);
          statement.setString(5, write(config));
          statement.setBoolean(6, enabled);
          statement.setBoolean(7, defaultEnvironment);
          return statement;
        },
        keys);
    return Objects.requireNonNull(keys.getKey(), "新增运行环境未返回主键").longValue();
  }

  public void update(
      long id, String name, String submitterType, RuntimeConfig config, boolean enabled) {
    int changed =
        db.update(
            "update yak_compute_environment set name=?,submitter_type=?,config_json=?,enabled=?,"
                + "version=version+1,last_check_status=null,last_check_message=null,last_check_time=null "
                + "where id=?",
            name,
            submitterType,
            write(config),
            enabled,
            id);
    if (changed != 1) {
      throw new IllegalArgumentException("运行环境不存在：" + id);
    }
  }

  public void setEnabled(long id, boolean enabled) {
    int changed =
        db.update(
            "update yak_compute_environment set enabled=?,version=version+1 where id=?",
            enabled,
            id);
    if (changed != 1) {
      throw new IllegalArgumentException("运行环境不存在：" + id);
    }
  }

  public void saveDiagnosis(
      long id, String status, String message, LocalDateTime checkedAt) {
    int changed =
        db.update(
            "update yak_compute_environment set last_check_status=?,last_check_message=?,"
                + "last_check_time=? where id=?",
            status,
            message,
            checkedAt == null ? null : Timestamp.valueOf(checkedAt),
            id);
    if (changed != 1) {
      throw new IllegalArgumentException("运行环境不存在：" + id);
    }
  }

  public void clearDefault() {
    db.update("update yak_compute_environment set is_default=0 where is_default=1");
  }

  public void setDefault(long id) {
    int changed =
        db.update(
            "update yak_compute_environment set is_default=1,version=version+1 where id=? and enabled=1",
            id);
    if (changed != 1) {
      throw new IllegalStateException("只有已启用的运行环境才能设为默认环境");
    }
  }

  public void delete(long id) {
    int changed = db.update("delete from yak_compute_environment where id=? and is_default=0", id);
    if (changed != 1) {
      throw new IllegalStateException("默认运行环境不能删除，请先切换默认环境");
    }
  }

  /**
   * Stage two makes the runtime binding explicit. Rows created before V7 are bound to the current
   * default environment once during application startup. Legacy deployment snapshots are a
   * best-effort reconstruction because stage one did not persist the historical environment.
   */
  public void bindLegacyRealtimeJobs(ComputeEnvironment environment) {
    ComputeEnvironmentSnapshot snapshot = ComputeEnvironmentSnapshot.from(environment);
    db.update(
        "update yak_realtime_job_definition set runtime_environment_id=? "
            + "where runtime_environment_id is null",
        environment.id());
    db.update(
        "update yak_realtime_job_deployment set runtime_environment_id=?,"
            + "runtime_environment_version=?,runtime_environment_snapshot_json=? "
            + "where runtime_environment_snapshot_json is null",
        environment.id(),
        environment.version(),
        write(snapshot));
  }

  public boolean hasBoundRealtimeJobs(long id) {
    Long count =
        db.queryForObject(
            "select count(*) from yak_realtime_job_definition where runtime_environment_id=?",
            Long.class,
            id);
    return count != null && count > 0;
  }

  public boolean hasActiveRealtimeJobs() {
    Long count =
        db.queryForObject(
            "select count(*) from yak_realtime_job_definition where desired_state='RUNNING' "
                + "or observed_state not in ('STOPPED','FAILED')",
            Long.class);
    return count != null && count > 0;
  }

  private ComputeEnvironment map(ResultSet result) throws SQLException {
    return new ComputeEnvironment(
        result.getLong("id"),
        result.getString("name"),
        result.getString("engine_type"),
        result.getString("deployment_mode"),
        result.getString("submitter_type"),
        read(result.getString("config_json")),
        result.getBoolean("enabled"),
        result.getBoolean("is_default"),
        result.getInt("version"),
        time(result.getTimestamp("create_time")),
        time(result.getTimestamp("update_time")),
        result.getString("last_check_status"),
        result.getString("last_check_message"),
        time(result.getTimestamp("last_check_time")));
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("无法序列化运行环境配置", exception);
    }
  }

  private RuntimeConfig read(String value) {
    try {
      return json.readValue(value, RuntimeConfig.class);
    } catch (Exception exception) {
      throw new IllegalStateException("运行环境配置无法解析", exception);
    }
  }

  private LocalDateTime time(Timestamp value) {
    return value == null ? null : value.toLocalDateTime();
  }
}
