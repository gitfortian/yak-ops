package io.yak.ops.business.sync.realtime.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** Read model for the realtime synchronization list page. */
@Repository
public class RealtimeJobListQuery {

  private final JdbcTemplate db;
  private final ObjectMapper json;

  public RealtimeJobListQuery(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      @Qualifier("realtimeObjectMapper") ObjectMapper json) {
    this.db = new JdbcTemplate(dataSource);
    this.json = json;
  }

  public RealtimeJobPage page(
      int pageNo,
      int pageSize,
      String keyword,
      Long id,
      String releaseState,
      String stateGroup) {
    int normalizedSize = Math.max(1, Math.min(pageSize, 100));
    int normalizedPage = Math.max(1, pageNo);
    List<String> conditions = new ArrayList<>();
    List<Object> arguments = new ArrayList<>();

    if (StringUtils.hasText(keyword)) {
      String pattern = "%" + keyword.trim() + "%";
      conditions.add("(d.job_name like ? or d.description like ?)");
      arguments.add(pattern);
      arguments.add(pattern);
    }
    if (id != null) {
      conditions.add("d.id=?");
      arguments.add(id);
    }

    String normalizedReleaseState = normalizeReleaseState(releaseState);
    if (normalizedReleaseState != null) {
      conditions.add("d.release_state=?");
      arguments.add(normalizedReleaseState);
    }

    appendStateGroup(conditions, stateGroup);
    String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);

    long total =
        Objects.requireNonNull(
            db.queryForObject(
                "select count(*) from yak_realtime_job_definition d" + where,
                Long.class,
                arguments.toArray()));

    List<Object> pageArguments = new ArrayList<>(arguments);
    pageArguments.add(normalizedSize);
    pageArguments.add((normalizedPage - 1) * normalizedSize);

    String sql =
        "select d.*,"
            + " p.id as deployment_id,"
            + " p.definition_version as deployment_definition_version,"
            + " p.spec_summary as deployment_spec_summary,"
            + " p.config_digest as deployment_config_digest,"
            + " p.idempotency_key as deployment_idempotency_key,"
            + " p.gateway_job_id as deployment_gateway_job_id,"
            + " p.runtime_revision as deployment_runtime_revision,"
            + " p.status as deployment_status,"
            + " p.result_uncertain as deployment_result_uncertain,"
            + " p.error_message as deployment_error_message,"
            + " p.create_time as deployment_create_time,"
            + " p.update_time as deployment_update_time"
            + " from yak_realtime_job_definition d"
            + " left join yak_realtime_job_deployment p on p.id=("
            + "select max(p2.id) from yak_realtime_job_deployment p2 where p2.definition_id=d.id)"
            + where
            + " order by d.update_time desc limit ? offset ?";

    List<RealtimeJobView> records =
        db.query(sql, (result, row) -> mapView(result), pageArguments.toArray());
    return new RealtimeJobPage(records, total, normalizedPage, normalizedSize);
  }

  private void appendStateGroup(List<String> conditions, String stateGroup) {
    if (!StringUtils.hasText(stateGroup)) {
      return;
    }
    switch (stateGroup.trim().toUpperCase(Locale.ROOT)) {
      case "RUNNING" ->
          conditions.add("d.observed_state in ('STARTING','RUNNING','STOPPING')");
      case "STOPPED" -> conditions.add("d.observed_state='STOPPED'");
      case "ABNORMAL" ->
          conditions.add("d.observed_state in ('FAILED','UNKNOWN','CONFLICT')");
      default -> throw new IllegalArgumentException("不支持的实时任务状态筛选：" + stateGroup);
    }
  }

  private String normalizeReleaseState(String releaseState) {
    if (!StringUtils.hasText(releaseState)) {
      return null;
    }
    String normalized = releaseState.trim().toUpperCase(Locale.ROOT);
    if (!"DRAFT".equals(normalized) && !"PUBLISHED".equals(normalized)) {
      throw new IllegalArgumentException("不支持的实时任务发布状态：" + releaseState);
    }
    return normalized;
  }

  private RealtimeJobView mapView(ResultSet result) throws SQLException {
    RealtimeJobView.Deployment deployment = null;
    Long deploymentId = result.getObject("deployment_id", Long.class);
    if (deploymentId != null) {
      deployment =
          new RealtimeJobView.Deployment(
              deploymentId,
              result.getInt("deployment_definition_version"),
              result.getString("deployment_spec_summary"),
              result.getString("deployment_config_digest"),
              result.getString("deployment_idempotency_key"),
              result.getString("deployment_gateway_job_id"),
              result.getString("deployment_runtime_revision"),
              result.getString("deployment_status"),
              result.getBoolean("deployment_result_uncertain"),
              result.getString("deployment_error_message"),
              time(result.getTimestamp("deployment_create_time")),
              time(result.getTimestamp("deployment_update_time")));
    }

    return new RealtimeJobView(
        result.getLong("id"),
        result.getString("job_name"),
        result.getString("description"),
        readSpec(result.getString("spec_json")),
        result.getString("release_state"),
        result.getString("desired_state"),
        result.getString("observed_state"),
        result.getInt("definition_version"),
        result.getObject("published_version", Integer.class),
        result.getString("config_digest"),
        result.getString("last_error"),
        time(result.getTimestamp("create_time")),
        time(result.getTimestamp("update_time")),
        deployment);
  }

  private CdcPipelineSpec readSpec(String value) {
    // Two-stage drafts intentionally have no spec until the configuration page is saved.
    // Keep those rows visible in list queries instead of attempting to deserialize SQL NULL.
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return json.readValue(value, CdcPipelineSpec.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("实时同步 Spec 无效", exception);
    }
  }

  private LocalDateTime time(Timestamp value) {
    return value == null ? null : value.toLocalDateTime();
  }
}
