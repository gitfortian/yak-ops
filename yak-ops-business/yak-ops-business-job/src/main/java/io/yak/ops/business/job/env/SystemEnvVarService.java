package io.yak.ops.business.job.env;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages application-level environment variables stored in {@code yak_system_env_var}.
 *
 * <p>On startup, all rows are loaded into an in-memory cache. Mutations update both the
 * cache and the database so that subsequent task executions immediately pick up the
 * latest values without restarting the application.
 *
 * <p>The merge priority (highest first) is:
 * <ol>
 *   <li>Task-level {@code envVars} specified in the task config</li>
 *   <li>Application-level env vars managed by this service</li>
 *   <li>OS-level environment variables from {@code System.getenv()}</li>
 * </ol>
 */
@Service
public class SystemEnvVarService {

  private static final Logger log = LoggerFactory.getLogger(SystemEnvVarService.class);

  private final JdbcTemplate jdbcTemplate;
  private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

  public SystemEnvVarService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @PostConstruct
  void loadFromDatabase() {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT var_key, var_value FROM yak_system_env_var");
    for (Map<String, Object> row : rows) {
      String key = String.valueOf(row.get("var_key"));
      String value = String.valueOf(row.get("var_value"));
      cache.put(key, value);
    }
    log.info("Loaded {} application environment variable(s) from database", cache.size());
  }

  /** Returns an unmodifiable snapshot of all application-level environment variables. */
  public Map<String, String> getAll() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(cache));
  }

  /**
   * Returns a merged environment variable map suitable for task execution.
   *
   * <p>OS-level environment variables form the base; application-level variables
   * override them.
   */
  public Map<String, String> resolveMergedEnv() {
    Map<String, String> merged = new LinkedHashMap<>(System.getenv());
    merged.putAll(cache);
    return Collections.unmodifiableMap(merged);
  }

  /** Sets (or updates) an application-level environment variable. */
  @Transactional
  public void set(String key, String value) {
    String normalizedKey = normalizeKey(key);
    String normalizedValue = value == null ? "" : value;
    jdbcTemplate.update(
        "INSERT INTO yak_system_env_var (var_key, var_value, create_time, update_time) "
            + "VALUES (?, ?, NOW(6), NOW(6)) "
            + "ON DUPLICATE KEY UPDATE var_value = VALUES(var_value), update_time = NOW(6)",
        normalizedKey, normalizedValue);
    cache.put(normalizedKey, normalizedValue);
    log.info("Application environment variable set: {} = {}", normalizedKey, maskSensitive(normalizedKey, normalizedValue));
  }

  /** Removes an application-level environment variable. */
  @Transactional
  public boolean remove(String key) {
    String normalizedKey = normalizeKey(key);
    int affected = jdbcTemplate.update(
        "DELETE FROM yak_system_env_var WHERE var_key = ?", normalizedKey);
    cache.remove(normalizedKey);
    if (affected > 0) {
      log.info("Application environment variable removed: {}", normalizedKey);
    }
    return affected > 0;
  }

  /** Batch-saves environment variables, replacing all existing application-level variables. */
  @Transactional
  public void batchSave(Map<String, String> variables) {
    if (variables == null || variables.isEmpty()) {
      return;
    }
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      set(entry.getKey(), entry.getValue());
    }
  }

  private String normalizeKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Environment variable key must not be blank");
    }
    String normalized = key.trim();
    if (normalized.length() > 128) {
      throw new IllegalArgumentException("Environment variable key must not exceed 128 characters");
    }
    if (!normalized.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException(
          "Environment variable key must start with a letter or underscore and contain only "
              + "alphanumeric characters and underscores: " + normalized);
    }
    return normalized;
  }

  private String maskSensitive(String key, String value) {
    String upper = key.toUpperCase();
    if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("TOKEN")
        || upper.contains("KEY") || upper.contains("CREDENTIAL")) {
      return "********";
    }
    return value.length() > 64 ? value.substring(0, 61) + "..." : value;
  }
}
