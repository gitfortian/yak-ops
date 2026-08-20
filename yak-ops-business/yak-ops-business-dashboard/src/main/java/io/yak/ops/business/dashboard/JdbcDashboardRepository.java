package io.yak.ops.business.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@DependsOn("yakDashboardFlyway")
@ConditionalOnDataSourceEnabled
class JdbcDashboardRepository implements DashboardRepository {

  private static final String DASHBOARD_COLUMNS =
      "SELECT id, name, description, current_version_id, current_version_no, published_version_id, published_version_no, published_time, create_time, update_time FROM yak_dashboard";
  private static final String VERSION_COLUMNS =
      "SELECT id, dashboard_id, version_no, name_snapshot, description_snapshot, active_dataset_id, create_time FROM yak_dashboard_version";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Autowired
  JdbcDashboardRepository(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  /** Keeps focused repository tests source-compatible without a Spring event publisher. */
  JdbcDashboardRepository(DataSource dataSource, ObjectMapper objectMapper) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
    this.eventPublisher = null;
  }

  @Override
  public long insertDashboard(String name, String description) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          "INSERT INTO yak_dashboard (name, description, current_version_id, current_version_no, published_version_id, published_version_no, published_time, create_time, update_time) VALUES (?, ?, NULL, 0, NULL, 0, NULL, NOW(6), NOW(6))",
          Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, name);
      statement.setString(2, description);
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) throw new IllegalStateException("创建 Dashboard 后未返回主键");
    return key.longValue();
  }

  @Override
  public long insertVersion(long dashboardId, int versionNo, String name, String description, Long activeDatasetId) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          "INSERT INTO yak_dashboard_version (dashboard_id, version_no, name_snapshot, description_snapshot, active_dataset_id, create_time) VALUES (?, ?, ?, ?, ?, NOW(6))",
          Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, dashboardId);
      statement.setInt(2, versionNo);
      statement.setString(3, name);
      statement.setString(4, description);
      if (activeDatasetId == null) statement.setObject(5, null); else statement.setLong(5, activeDatasetId);
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) throw new IllegalStateException("创建 DashboardVersion 后未返回主键");
    return key.longValue();
  }

  @Override
  public void insertWidgets(long versionId, List<DashboardService.WidgetSpec> widgets, List<String> inlineJson) {
    for (int index = 0; index < widgets.size(); index++) {
      DashboardService.WidgetSpec widget = widgets.get(index);
      jdbcTemplate.update(
          """
          INSERT INTO yak_dashboard_widget
            (dashboard_version_id, widget_key, analysis_id, title, inline_analysis_json,
             grid_x, grid_y, grid_w, grid_h, min_w, min_h, sort_order)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          versionId,
          widget.widgetKey(),
          widget.analysisId(),
          widget.title(),
          inlineJson.get(index),
          widget.x(), widget.y(), widget.w(), widget.h(), widget.minW(), widget.minH(), index + 1);
    }
  }

  @Override
  public void insertGlobalFilters(
      long versionId,
      List<DashboardService.GlobalFilterSpec> filters,
      List<String> defaultValueJson) {
    for (int index = 0; index < filters.size(); index++) {
      DashboardService.GlobalFilterSpec filter = filters.get(index);
      jdbcTemplate.update(
          "INSERT INTO yak_dashboard_filter (dashboard_version_id, filter_key, name, operator, default_value_json, sort_order) VALUES (?, ?, ?, ?, ?, ?)",
          versionId,
          filter.filterKey(),
          filter.name(),
          filter.operator().name(),
          defaultValueJson.get(index),
          index + 1);
      for (int bindingIndex = 0; bindingIndex < filter.bindings().size(); bindingIndex++) {
        DashboardService.FilterBindingSpec binding = filter.bindings().get(bindingIndex);
        jdbcTemplate.update(
            "INSERT INTO yak_dashboard_filter_binding (dashboard_version_id, filter_key, widget_key, field_id, sort_order) VALUES (?, ?, ?, ?, ?)",
            versionId, filter.filterKey(), binding.widgetKey(), binding.fieldId(), bindingIndex + 1);
      }
    }
  }

  @Override
  public void insertInteractions(long versionId, List<DashboardService.InteractionSpec> interactions) {
    for (int index = 0; index < interactions.size(); index++) {
      DashboardService.InteractionSpec interaction = interactions.get(index);
      jdbcTemplate.update(
          "INSERT INTO yak_dashboard_interaction (dashboard_version_id, interaction_key, event_type, source_widget_key, source_field_id, target_filter_key, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)",
          versionId,
          interaction.interactionKey(),
          interaction.event().name(),
          interaction.sourceWidgetKey(),
          interaction.sourceFieldId(),
          interaction.targetFilterKey(),
          index + 1);
    }
  }

  @Override
  public void updateCurrentVersion(long dashboardId, long versionId, int versionNo, String name, String description) {
    int updated = jdbcTemplate.update(
        "UPDATE yak_dashboard SET current_version_id = ?, current_version_no = ?, name = ?, description = ?, update_time = NOW(6) WHERE id = ?",
        versionId, versionNo, name, description, dashboardId);
    if (updated != 1) throw new IllegalArgumentException("Dashboard 不存在：" + dashboardId);
    requestLineageRefresh(DashboardLineageRefreshRequested.refresh(dashboardId));
  }

  @Override
  public void updatePublishedVersion(long dashboardId, long versionId, int versionNo) {
    int updated = jdbcTemplate.update(
        "UPDATE yak_dashboard SET published_version_id = ?, published_version_no = ?, published_time = NOW(6), update_time = NOW(6) WHERE id = ?",
        versionId, versionNo, dashboardId);
    if (updated != 1) throw new IllegalArgumentException("Dashboard 不存在：" + dashboardId);
    requestLineageRefresh(DashboardLineageRefreshRequested.refresh(dashboardId));
  }

  @Override
  public Optional<DashboardAsset> findDashboard(long dashboardId) {
    return jdbcTemplate.query(DASHBOARD_COLUMNS + " WHERE id = ? LIMIT 1", JdbcDashboardRepository::mapDashboard, dashboardId)
        .stream().findFirst();
  }

  @Override
  public List<DashboardAsset> listDashboards() {
    return jdbcTemplate.query(DASHBOARD_COLUMNS + " ORDER BY update_time DESC, id DESC", JdbcDashboardRepository::mapDashboard);
  }

  @Override
  public Optional<DashboardVersion> findVersion(long versionId) {
    return jdbcTemplate.query(VERSION_COLUMNS + " WHERE id = ? LIMIT 1", JdbcDashboardRepository::mapVersion, versionId)
        .stream().findFirst();
  }

  @Override
  public Optional<DashboardVersion> findVersionByNo(long dashboardId, int versionNo) {
    return jdbcTemplate.query(
        VERSION_COLUMNS + " WHERE dashboard_id = ? AND version_no = ? LIMIT 1",
        JdbcDashboardRepository::mapVersion, dashboardId, versionNo).stream().findFirst();
  }

  @Override
  public List<DashboardVersion> listVersions(long dashboardId) {
    return jdbcTemplate.query(
        VERSION_COLUMNS + " WHERE dashboard_id = ? ORDER BY version_no DESC",
        JdbcDashboardRepository::mapVersion, dashboardId);
  }

  @Override
  public List<DashboardWidgetSnapshot> listWidgets(long versionId) {
    return jdbcTemplate.query(
        """
        SELECT id, dashboard_version_id, widget_key, analysis_id, title, inline_analysis_json,
               grid_x, grid_y, grid_w, grid_h, min_w, min_h, sort_order
        FROM yak_dashboard_widget WHERE dashboard_version_id = ? ORDER BY sort_order ASC, id ASC
        """,
        this::mapWidget,
        versionId);
  }

  @Override
  public List<DashboardGlobalFilterSnapshot> listGlobalFilters(long versionId) {
    List<FilterHeader> headers = jdbcTemplate.query(
        "SELECT filter_key, name, operator, default_value_json, sort_order FROM yak_dashboard_filter WHERE dashboard_version_id = ? ORDER BY sort_order ASC, id ASC",
        (rs, rowNum) -> new FilterHeader(
            rs.getString("filter_key"),
            rs.getString("name"),
            DashboardGlobalFilterOperator.valueOf(rs.getString("operator")),
            rs.getString("default_value_json"),
            rs.getInt("sort_order")),
        versionId);
    return headers.stream().map(header -> new DashboardGlobalFilterSnapshot(
        header.filterKey(),
        header.name(),
        header.operator(),
        parseJson(header.defaultValueJson()),
        jdbcTemplate.query(
            "SELECT widget_key, field_id, sort_order FROM yak_dashboard_filter_binding WHERE dashboard_version_id = ? AND filter_key = ? ORDER BY sort_order ASC",
            (rs, rowNum) -> new DashboardGlobalFilterBindingSnapshot(
                rs.getString("widget_key"), rs.getString("field_id"), rs.getInt("sort_order")),
            versionId, header.filterKey()),
        header.sortOrder())).toList();
  }

  @Override
  public List<DashboardInteractionSnapshot> listInteractions(long versionId) {
    return jdbcTemplate.query(
        "SELECT interaction_key, event_type, source_widget_key, source_field_id, target_filter_key, sort_order FROM yak_dashboard_interaction WHERE dashboard_version_id = ? ORDER BY sort_order ASC, id ASC",
        (rs, rowNum) -> new DashboardInteractionSnapshot(
            rs.getString("interaction_key"),
            DashboardInteractionEvent.valueOf(rs.getString("event_type")),
            rs.getString("source_widget_key"),
            rs.getString("source_field_id"),
            rs.getString("target_filter_key"),
            rs.getInt("sort_order")),
        versionId);
  }

  @Override
  public int nextVersionNo(long dashboardId) {
    Integer value = jdbcTemplate.queryForObject(
        "SELECT COALESCE(MAX(version_no), 0) + 1 FROM yak_dashboard_version WHERE dashboard_id = ?",
        Integer.class, dashboardId);
    return value == null ? 1 : value;
  }

  @Override
  public void deleteDashboard(long dashboardId) {
    int deleted = jdbcTemplate.update("DELETE FROM yak_dashboard WHERE id = ?", dashboardId);
    if (deleted != 1) throw new IllegalArgumentException("Dashboard 不存在：" + dashboardId);
    requestLineageRefresh(DashboardLineageRefreshRequested.deleted(dashboardId));
  }

  private void requestLineageRefresh(DashboardLineageRefreshRequested event) {
    if (eventPublisher != null) eventPublisher.publishEvent(event);
  }

  private static DashboardAsset mapDashboard(ResultSet rs, int rowNum) throws SQLException {
    Long currentVersionId = rs.getObject("current_version_id") == null ? null : rs.getLong("current_version_id");
    Long publishedVersionId = rs.getObject("published_version_id") == null ? null : rs.getLong("published_version_id");
    return new DashboardAsset(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("description"),
        currentVersionId,
        rs.getInt("current_version_no"),
        publishedVersionId,
        rs.getInt("published_version_no"),
        instant(rs.getTimestamp("published_time")),
        instant(rs.getTimestamp("create_time")),
        instant(rs.getTimestamp("update_time")));
  }

  private static DashboardVersion mapVersion(ResultSet rs, int rowNum) throws SQLException {
    Long activeDatasetId = rs.getObject("active_dataset_id") == null ? null : rs.getLong("active_dataset_id");
    return new DashboardVersion(
        rs.getLong("id"), rs.getLong("dashboard_id"), rs.getInt("version_no"),
        rs.getString("name_snapshot"), rs.getString("description_snapshot"), activeDatasetId,
        instant(rs.getTimestamp("create_time")));
  }

  private DashboardWidgetSnapshot mapWidget(ResultSet rs, int rowNum) throws SQLException {
    Long analysisId = rs.getObject("analysis_id") == null ? null : rs.getLong("analysis_id");
    Integer minW = rs.getObject("min_w") == null ? null : rs.getInt("min_w");
    Integer minH = rs.getObject("min_h") == null ? null : rs.getInt("min_h");
    String inlineJson = rs.getString("inline_analysis_json");
    return new DashboardWidgetSnapshot(
        rs.getLong("id"), rs.getLong("dashboard_version_id"), rs.getString("widget_key"), analysisId,
        rs.getString("title"), parseJson(inlineJson), rs.getInt("grid_x"), rs.getInt("grid_y"),
        rs.getInt("grid_w"), rs.getInt("grid_h"), minW, minH, rs.getInt("sort_order"));
  }

  private Object parseJson(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return objectMapper.readValue(value, Object.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Dashboard JSON 反序列化失败", exception);
    }
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private record FilterHeader(
      String filterKey,
      String name,
      DashboardGlobalFilterOperator operator,
      String defaultValueJson,
      int sortOrder) {
  }
}
