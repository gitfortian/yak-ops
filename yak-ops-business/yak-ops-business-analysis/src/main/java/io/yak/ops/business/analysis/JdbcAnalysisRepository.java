package io.yak.ops.business.analysis;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("yakAnalysisFlyway")
@ConditionalOnDataSourceEnabled
class JdbcAnalysisRepository implements AnalysisRepository {

  private static final String COLUMNS = """
      SELECT id, name, description, dataset_id, chart_type,
             query_spec_json, visual_config_json, create_time, update_time
      FROM yak_analysis
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher eventPublisher;

  @Autowired
  JdbcAnalysisRepository(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      ApplicationEventPublisher eventPublisher) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.eventPublisher = eventPublisher;
  }

  /** Keeps focused repository tests source-compatible without a Spring event publisher. */
  JdbcAnalysisRepository(DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.eventPublisher = null;
  }

  @Override
  public long insert(
      String name,
      String description,
      long datasetId,
      AnalysisChartType chartType,
      String querySpecJson,
      String visualConfigJson) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO yak_analysis
            (name, description, dataset_id, chart_type, query_spec_json,
             visual_config_json, create_time, update_time)
          VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
          """,
          Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, name);
      statement.setString(2, description);
      statement.setLong(3, datasetId);
      statement.setString(4, chartType.name());
      statement.setString(5, querySpecJson);
      statement.setString(6, visualConfigJson);
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) throw new IllegalStateException("创建 Analysis 后未返回主键");
    long analysisId = key.longValue();
    requestLineageRefresh(AnalysisLineageRefreshRequested.refresh(analysisId));
    return analysisId;
  }

  @Override
  public void update(
      long analysisId,
      String name,
      String description,
      long datasetId,
      AnalysisChartType chartType,
      String querySpecJson,
      String visualConfigJson) {
    int updated = jdbcTemplate.update(
        """
        UPDATE yak_analysis
        SET name = ?, description = ?, dataset_id = ?, chart_type = ?,
            query_spec_json = ?, visual_config_json = ?, update_time = NOW(6)
        WHERE id = ?
        """,
        name,
        description,
        datasetId,
        chartType.name(),
        querySpecJson,
        visualConfigJson,
        analysisId);
    if (updated != 1) throw new IllegalArgumentException("Analysis 不存在：" + analysisId);
    requestLineageRefresh(AnalysisLineageRefreshRequested.refresh(analysisId));
  }

  @Override
  public Optional<AnalysisRow> findById(long analysisId) {
    return jdbcTemplate.query(
        COLUMNS + " WHERE id = ? LIMIT 1",
        JdbcAnalysisRepository::map,
        analysisId).stream().findFirst();
  }

  @Override
  public List<AnalysisRow> list() {
    return jdbcTemplate.query(
        COLUMNS + " ORDER BY update_time DESC, id DESC",
        JdbcAnalysisRepository::map);
  }

  @Override
  public void delete(long analysisId) {
    int deleted = jdbcTemplate.update("DELETE FROM yak_analysis WHERE id = ?", analysisId);
    if (deleted != 1) throw new IllegalArgumentException("Analysis 不存在：" + analysisId);
    requestLineageRefresh(AnalysisLineageRefreshRequested.deleted(analysisId));
  }

  private void requestLineageRefresh(AnalysisLineageRefreshRequested event) {
    if (eventPublisher != null) eventPublisher.publishEvent(event);
  }

  private static AnalysisRow map(ResultSet rs, int rowNum) throws SQLException {
    return new AnalysisRow(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getLong("dataset_id"),
        AnalysisChartType.valueOf(rs.getString("chart_type")),
        rs.getString("query_spec_json"),
        rs.getString("visual_config_json"),
        instant(rs.getTimestamp("create_time")),
        instant(rs.getTimestamp("update_time")));
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
