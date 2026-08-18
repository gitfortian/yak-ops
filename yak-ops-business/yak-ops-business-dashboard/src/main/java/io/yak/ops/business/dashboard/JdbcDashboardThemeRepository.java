package io.yak.ops.business.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("yakDashboardFlyway")
@ConditionalOnDataSourceEnabled
class JdbcDashboardThemeRepository implements DashboardThemeRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  JdbcDashboardThemeRepository(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      ObjectMapper objectMapper) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
  }

  @Override
  public void save(long versionId, String themeJson) {
    int updated = jdbcTemplate.update(
        "UPDATE yak_dashboard_version SET theme_json = ? WHERE id = ?",
        themeJson,
        versionId);
    if (updated != 1) throw new IllegalArgumentException("DashboardVersion 不存在：" + versionId);
  }

  @Override
  public Object find(long versionId) {
    String json = jdbcTemplate.query(
        "SELECT theme_json FROM yak_dashboard_version WHERE id = ? LIMIT 1",
        rs -> rs.next() ? rs.getString("theme_json") : null,
        versionId);
    if (json == null || json.isBlank()) return null;
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Dashboard Theme JSON 反序列化失败", exception);
    }
  }
}
