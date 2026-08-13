package io.yak.ops.business.development.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores per-user SQL editor preferences for the data-development workbench. */
@Service
public class DevelopmentEditorSettingsService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public DevelopmentEditorSettingsService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> get(String userKey) {
    List<String> values = jdbcTemplate.query(
        "SELECT setting_json FROM yak_dev_editor_setting WHERE user_key = ? LIMIT 1",
        (rs, rowNum) -> rs.getString(1),
        normalizeUserKey(userKey));
    if (values.isEmpty()) {
      return defaults();
    }
    try {
      Map<String, Object> stored = objectMapper.readValue(values.get(0), MAP_TYPE);
      Map<String, Object> result = defaults();
      result.putAll(stored);
      return result;
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid persisted editor settings", ex);
    }
  }

  @Transactional
  public Map<String, Object> save(String userKey, Map<String, Object> settings) {
    Map<String, Object> normalized = defaults();
    if (settings != null) {
      normalized.putAll(settings);
    }
    normalized.put("theme", normalizeTheme(normalized.get("theme")));
    normalized.put("fontSize", clampInt(normalized.get("fontSize"), 10, 32, 14));
    normalized.put("lineHeight", clampDouble(normalized.get("lineHeight"), 1.0, 3.0, 1.6));

    try {
      String json = objectMapper.writeValueAsString(normalized);
      jdbcTemplate.update(
          "INSERT INTO yak_dev_editor_setting (user_key, setting_json, create_time, update_time) "
              + "VALUES (?, ?, NOW(6), NOW(6)) "
              + "ON DUPLICATE KEY UPDATE setting_json = VALUES(setting_json), update_time = NOW(6)",
          normalizeUserKey(userKey), json);
      return normalized;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to persist editor settings", ex);
    }
  }

  private String normalizeUserKey(String userKey) {
    String value = userKey == null ? "" : userKey.trim();
    return value.isEmpty() ? "default" : value.substring(0, Math.min(value.length(), 128));
  }

  private String normalizeTheme(Object value) {
    String theme = value == null ? "Yak-Light" : String.valueOf(value).trim();
    return theme.isEmpty() ? "Yak-Light" : theme;
  }

  private int clampInt(Object value, int min, int max, int fallback) {
    try {
      int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
      return Math.max(min, Math.min(max, parsed));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private double clampDouble(Object value, double min, double max, double fallback) {
    try {
      double parsed = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
      return Math.max(min, Math.min(max, parsed));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private Map<String, Object> defaults() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("theme", "Yak-Light");
    result.put("fontSize", 14);
    result.put("fontFamily", "Monaco");
    result.put("customFontFamily", "");
    result.put("lineHeight", 1.6);
    result.put("showLineNumber", true);
    result.put("showMinimap", false);
    result.put("wordWrap", true);
    result.put("folding", true);
    result.put("renderLineHighlight", "line");
    result.put("keywordCase", "lower");
    result.put("sqlCompletionFQN", "none");
    result.put("renderWhitespace", "none");
    return result;
  }
}
